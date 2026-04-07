package com.ycy.aiapplication.infrastructure.ai.chat.impl.service;

import cn.hutool.core.collection.CollUtil;
import com.ycy.aiapplication.framework.convention.ChatRequest;
import com.ycy.aiapplication.framework.errorcode.BaseErrorCode;
import com.ycy.aiapplication.framework.exception.RemoteException;
import com.ycy.aiapplication.infrastructure.ai.chat.toolkit.ChatModelSelector;
import com.ycy.aiapplication.infrastructure.ai.chat.interfaces.ChatClient;
import com.ycy.aiapplication.infrastructure.ai.chat.interfaces.LLMService;
import com.ycy.aiapplication.infrastructure.ai.chat.interfaces.StreamCallback;
import com.ycy.aiapplication.infrastructure.ai.chat.interfaces.StreamCancellationHandle;
import com.ycy.aiapplication.infrastructure.ai.chat.toolkit.FirstPacketAwaiter;
import com.ycy.aiapplication.infrastructure.ai.config.AIModelProperties;
import com.ycy.aiapplication.infrastructure.ai.enums.ModelCapability;
import com.ycy.aiapplication.infrastructure.ai.model.ModelHealthStore;
import com.ycy.aiapplication.infrastructure.ai.model.ModelRoutingExecutor;
import com.ycy.aiapplication.infrastructure.ai.model.ModelTarget;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.ycy.aiapplication.infrastructure.ai.chat.constat.ChatServiceMessageConstant.*;

/**
 * 聊天路由服务。
 * 负责组合模型选择器、健康状态和执行器，完成 chat 的同步与流式降级调用。
 */
@Slf4j
@Service
@Primary
public class RoutingLLMService implements LLMService {

    private final ChatModelSelector selector;
    private final ModelHealthStore healthStore;
    private final ModelRoutingExecutor executor;
    private final AIModelProperties properties;
    private final Map<String, ChatClient> clientsByProvider;

    /**
     * 初始化路由服务，并建立 provider 到客户端实现的映射关系。
     */
    public RoutingLLMService(
            ChatModelSelector selector,
            ModelHealthStore healthStore,
            ModelRoutingExecutor executor,
            AIModelProperties properties,
            List<ChatClient> clients) {
        this.selector = selector;
        this.healthStore = healthStore;
        this.executor = executor;
        this.properties = properties;
        this.clientsByProvider = clients.stream().collect(Collectors.toMap(ChatClient::provider, Function.identity()));
    }

    /**
     * 执行非流式聊天，并按候选顺序自动进行兜底。
     */
    @Override
    public String chat(ChatRequest request) {
        List<ModelTarget> targets = selector.select(request);
        return executor.executeWithFallback(
                ModelCapability.CHAT,
                targets,
                target -> clientsByProvider.get(target.provider()),
                (client, target) -> client.chat(request, target)
        );
    }

    /**
     * 执行流式聊天，并在首包失败、超时或无内容时切换到下一个候选模型。
     */
    @Override
    public StreamCancellationHandle streamChat(ChatRequest request, StreamCallback callback) {
        List<ModelTarget> targets = selector.select(request);
        if (CollUtil.isEmpty(targets)) {
            throw new RemoteException(STREAM_NO_PROVIDER_MESSAGE);
        }

        Throwable lastError = null;
        for (ModelTarget target : targets) {
            ChatClient client = clientsByProvider.get(target.provider());
            if (client == null) {
                continue;
            }

            // 首包探测阶段先缓冲内容，只有确认当前模型可用后才对外提交。
            FirstPacketAwaiter awaiter = new FirstPacketAwaiter();
            ProbeBufferingCallback wrapper = new ProbeBufferingCallback(callback, awaiter);
            StreamCancellationHandle handle;
            try {
                handle = client.streamChat(request, wrapper, target);
            } catch (Exception ex) {
                healthStore.markFailure(target.id());
                lastError = ex;
                continue;
            }
            if (handle == null) {
                healthStore.markFailure(target.id());
                lastError = new RemoteException(STREAM_START_FAILED_MESSAGE, BaseErrorCode.REMOTE_ERROR);
                continue;
            }

            FirstPacketAwaiter.Result result = awaitFirstPacket(awaiter, handle, callback);
            if (result.isSuccess()) {
                // 只有首包确认成功后，才把探测阶段缓冲的事件回放给下游。
                wrapper.commit();
                healthStore.markSuccess(target.id());
                return handle;
            }

            handle.cancel();
            healthStore.markFailure(target.id());
            lastError = mapStreamFailure(result);
        }

        RemoteException exception = new RemoteException(STREAM_ALL_FAILED_MESSAGE, lastError, BaseErrorCode.REMOTE_ERROR);
        callback.onError(exception);
        throw exception;
    }

    /**
     * 等待当前候选模型返回首个有效事件。
     */
    private FirstPacketAwaiter.Result awaitFirstPacket(
            FirstPacketAwaiter awaiter,
            StreamCancellationHandle handle,
            StreamCallback callback) {
        try {
            int timeoutSeconds = properties.getStream().getFirstPacketTimeoutSeconds() == null
                    ? 60
                    : properties.getStream().getFirstPacketTimeoutSeconds();
            return awaiter.await(timeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            handle.cancel();
            RemoteException interrupted = new RemoteException(STREAM_INTERRUPTED_MESSAGE, ex, BaseErrorCode.REMOTE_ERROR);
            callback.onError(interrupted);
            throw interrupted;
        }
    }

    /**
     * 将首包探测结果映射为统一异常。
     */
    private Throwable mapStreamFailure(FirstPacketAwaiter.Result result) {
        return switch (result.getType()) {
            case ERROR -> result.getError() == null
                    ? new RemoteException(STREAM_ALL_FAILED_MESSAGE, BaseErrorCode.REMOTE_ERROR)
                    : result.getError();
            case TIMEOUT -> new RemoteException(STREAM_TIMEOUT_MESSAGE, BaseErrorCode.REMOTE_ERROR);
            case NO_CONTENT -> new RemoteException(STREAM_NO_CONTENT_MESSAGE, BaseErrorCode.REMOTE_ERROR);
            default -> new RemoteException(STREAM_ALL_FAILED_MESSAGE, BaseErrorCode.REMOTE_ERROR);
        };
    }

    /**
     * 首包探测缓冲回调。
     * 在模型通过首包校验前先缓存事件，避免失败模型输出直接污染下游。
     */
    private static final class ProbeBufferingCallback implements StreamCallback {
        private final StreamCallback downstream;
        private final FirstPacketAwaiter awaiter;
        private final Object lock = new Object();
        private final List<BufferedEvent> bufferedEvents = new ArrayList<>();
        private volatile boolean committed;

        /**
         * 初始化一个缓冲回调包装器。
         */
        private ProbeBufferingCallback(StreamCallback downstream, FirstPacketAwaiter awaiter) {
            this.downstream = downstream;
            this.awaiter = awaiter;
        }

        /**
         * 缓存正文事件，并通知等待器已经收到有效内容。
         */
        @Override
        public void onContent(String content) {
            awaiter.markContent();
            bufferOrDispatch(BufferedEvent.content(content));
        }

        /**
         * 缓存 thinking 事件，并通知等待器已经收到有效内容。
         */
        @Override
        public void onThinking(String content) {
            awaiter.markContent();
            bufferOrDispatch(BufferedEvent.thinking(content));
        }

        /**
         * 缓存完成事件。
         */
        @Override
        public void onComplete() {
            awaiter.markComplete();
            bufferOrDispatch(BufferedEvent.complete());
        }

        /**
         * 缓存错误事件。
         */
        @Override
        public void onError(Throwable error) {
            awaiter.markError(error);
            bufferOrDispatch(BufferedEvent.error(error));
        }

        /**
         * 提交缓冲区，并按原顺序回放探测阶段的事件。
         */
        private void commit() {
            List<BufferedEvent> snapshot;
            synchronized (lock) {
                if (committed) {
                    return;
                }
                committed = true;
                snapshot = new ArrayList<>(bufferedEvents);
                bufferedEvents.clear();
            }
            for (BufferedEvent event : snapshot) {
                dispatch(event);
            }
        }

        /**
         * 在探测阶段缓存事件，在提交后直接透传事件。
         */
        private void bufferOrDispatch(BufferedEvent event) {
            boolean dispatchNow;
            synchronized (lock) {
                dispatchNow = committed;
                if (!dispatchNow) {
                    bufferedEvents.add(event);
                }
            }
            if (dispatchNow) {
                dispatch(event);
            }
        }

        /**
         * 把缓冲事件分发给真实下游回调。
         */
        private void dispatch(BufferedEvent event) {
            switch (event.type()) {
                case CONTENT -> downstream.onContent(event.content());
                case THINKING -> downstream.onThinking(event.content());
                case COMPLETE -> downstream.onComplete();
                case ERROR -> downstream.onError(event.error());
            }
        }

        private record BufferedEvent(EventType type, String content, Throwable error) {
            private static BufferedEvent content(String content) {
                return new BufferedEvent(EventType.CONTENT, content, null);
            }

            private static BufferedEvent thinking(String content) {
                return new BufferedEvent(EventType.THINKING, content, null);
            }

            private static BufferedEvent complete() {
                return new BufferedEvent(EventType.COMPLETE, null, null);
            }

            private static BufferedEvent error(Throwable error) {
                return new BufferedEvent(EventType.ERROR, null, error);
            }
        }

        private enum EventType {
            CONTENT,
            THINKING,
            COMPLETE,
            ERROR
        }
    }
}
