package com.ycy.aiapplication.infrastructure.ai.chat.impl.service;

import cn.hutool.core.collection.CollUtil;
import com.ycy.aiapplication.framework.convention.ChatRequest;
import com.ycy.aiapplication.framework.errorcode.BaseErrorCode;
import com.ycy.aiapplication.framework.exception.RemoteException;
import com.ycy.aiapplication.infrastructure.ai.chat.interfaces.ChatClient;
import com.ycy.aiapplication.infrastructure.ai.chat.interfaces.LLMService;
import com.ycy.aiapplication.infrastructure.ai.chat.interfaces.StreamCallback;
import com.ycy.aiapplication.infrastructure.ai.chat.interfaces.StreamCancellationHandle;
import com.ycy.aiapplication.infrastructure.ai.chat.toolkit.ChatModelSelector;
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
import java.util.concurrent.TimeUnit;

import static com.ycy.aiapplication.infrastructure.ai.chat.constat.ChatServiceMessageConstant.STREAM_ALL_FAILED_MESSAGE;
import static com.ycy.aiapplication.infrastructure.ai.chat.constat.ChatServiceMessageConstant.STREAM_INTERRUPTED_MESSAGE;
import static com.ycy.aiapplication.infrastructure.ai.chat.constat.ChatServiceMessageConstant.STREAM_NO_CONTENT_MESSAGE;
import static com.ycy.aiapplication.infrastructure.ai.chat.constat.ChatServiceMessageConstant.STREAM_NO_PROVIDER_MESSAGE;
import static com.ycy.aiapplication.infrastructure.ai.chat.constat.ChatServiceMessageConstant.STREAM_START_FAILED_MESSAGE;
import static com.ycy.aiapplication.infrastructure.ai.chat.constat.ChatServiceMessageConstant.STREAM_TIMEOUT_MESSAGE;

/**
 * 聊天路由服务。
 * <p>
 * 负责模型候选链执行、健康状态记录和流式首包探测。
 */
@Slf4j
@Service
@Primary
public class RoutingLLMService implements LLMService {

    private final ChatModelSelector selector;
    private final ModelHealthStore healthStore;
    private final ModelRoutingExecutor executor;
    private final AIModelProperties properties;
    private final ChatClient chatClient;

    public RoutingLLMService(
            ChatModelSelector selector,
            ModelHealthStore healthStore,
            ModelRoutingExecutor executor,
            AIModelProperties properties,
            ChatClient chatClient) {
        this.selector = selector;
        this.healthStore = healthStore;
        this.executor = executor;
        this.properties = properties;
        this.chatClient = chatClient;
    }

    @Override
    public String chat(ChatRequest request) {
        List<ModelTarget> targets = selector.select(request);
        return executor.executeWithFallback(
                ModelCapability.CHAT,
                targets,
                target -> chatClient,
                (client, target) -> client.chat(request, target)
        );
    }

    @Override
    public StreamCancellationHandle streamChat(ChatRequest request, StreamCallback callback) {
        List<ModelTarget> targets = selector.select(request);
        if (CollUtil.isEmpty(targets)) {
            throw new RemoteException(STREAM_NO_PROVIDER_MESSAGE);
        }

        Throwable lastError = null;
        for (ModelTarget target : targets) {
            FirstPacketAwaiter awaiter = new FirstPacketAwaiter();
            ProbeBufferingCallback wrapper = new ProbeBufferingCallback(callback, awaiter);
            StreamCancellationHandle handle;
            try {
                handle = chatClient.streamChat(request, wrapper, target);
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
     * 首包探测阶段先缓存事件，确认当前模型可用后再提交给下游。
     */
    private static final class ProbeBufferingCallback implements StreamCallback {
        private final StreamCallback downstream;
        private final FirstPacketAwaiter awaiter;
        private final Object lock = new Object();
        private final List<BufferedEvent> bufferedEvents = new ArrayList<>();
        private volatile boolean committed;

        private ProbeBufferingCallback(StreamCallback downstream, FirstPacketAwaiter awaiter) {
            this.downstream = downstream;
            this.awaiter = awaiter;
        }

        @Override
        public void onContent(String content) {
            awaiter.markContent();
            bufferOrDispatch(BufferedEvent.content(content));
        }

        @Override
        public void onThinking(String content) {
            awaiter.markContent();
            bufferOrDispatch(BufferedEvent.thinking(content));
        }

        @Override
        public void onComplete() {
            awaiter.markComplete();
            bufferOrDispatch(BufferedEvent.complete());
        }

        @Override
        public void onError(Throwable error) {
            awaiter.markError(error);
            bufferOrDispatch(BufferedEvent.error(error));
        }

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
