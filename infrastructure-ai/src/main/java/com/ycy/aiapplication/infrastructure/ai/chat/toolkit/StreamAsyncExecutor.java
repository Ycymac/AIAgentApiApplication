package com.ycy.aiapplication.infrastructure.ai.chat.toolkit;

import com.ycy.aiapplication.infrastructure.ai.chat.interfaces.StreamCallback;
import com.ycy.aiapplication.infrastructure.ai.chat.interfaces.StreamCancellationHandle;
import com.ycy.aiapplication.infrastructure.ai.http.ModelClientErrorType;
import com.ycy.aiapplication.infrastructure.ai.http.ModelClientException;
import okhttp3.Call;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 流式任务异步执行器。
 * 负责把阻塞式流读取任务提交到线程池，并返回统一的取消句柄。
 */
public final class StreamAsyncExecutor {

    private static final String STREAM_BUSY_MESSAGE = "流式线程池繁忙";

    /**
     * 工具类不允许实例化。
     */
    private StreamAsyncExecutor() {
    }

    /**
     * 提交流式任务，并将共享取消标记传递给执行体。
     */
    public static StreamCancellationHandle submit(
            Executor executor,
            Call call,
            StreamCallback callback,
            Consumer<AtomicBoolean> streamTask) {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        try {
            // 共享取消标记让执行线程可以感知外部 cancel 信号。
            CompletableFuture.runAsync(() -> streamTask.accept(cancelled), executor);
        } catch (RejectedExecutionException ex) {
            call.cancel();
            callback.onError(new ModelClientException(STREAM_BUSY_MESSAGE, ModelClientErrorType.SERVER_ERROR, null, ex));
            return StreamCancellationHandles.noop();
        }
        return StreamCancellationHandles.fromOkHttp(call, cancelled);
    }
}
