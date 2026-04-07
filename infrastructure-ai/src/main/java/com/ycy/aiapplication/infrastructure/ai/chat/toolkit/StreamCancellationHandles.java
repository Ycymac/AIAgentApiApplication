package com.ycy.aiapplication.infrastructure.ai.chat.toolkit;

import com.ycy.aiapplication.infrastructure.ai.chat.interfaces.StreamCancellationHandle;
import okhttp3.Call;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 流式取消句柄工具类。
 * 用于构造统一的取消句柄，并保证取消动作具备幂等语义。
 */
public final class StreamCancellationHandles {

    private static final StreamCancellationHandle NOOP = () -> {};

    /**
     * 工具类不允许实例化。
     */
    private StreamCancellationHandles() {
    }

    /**
     * 返回一个空实现的取消句柄。
     */
    public static StreamCancellationHandle noop() {
        return NOOP;
    }

    /**
     * 基于 OkHttp Call 构造取消句柄。
     */
    public static StreamCancellationHandle fromOkHttp(Call call, AtomicBoolean cancelled) {
        return new OkHttpCancellationHandle(call, cancelled);
    }

    /**
     * OkHttp 专用取消句柄。
     */
    private static final class OkHttpCancellationHandle implements StreamCancellationHandle {
        private final Call call;
        private final AtomicBoolean cancelled;
        private final AtomicBoolean once = new AtomicBoolean(false);

        /**
         * 初始化 OkHttp 取消句柄。
         */
        private OkHttpCancellationHandle(Call call, AtomicBoolean cancelled) {
            this.call = call;
            this.cancelled = cancelled;
        }

        /**
         * 取消当前流式任务。
         */
        @Override
        public void cancel() {
            if (!once.compareAndSet(false, true)) {
                return;
            }
            if (cancelled != null) {
                cancelled.set(true);
            }
            if (call != null) {
                call.cancel();
            }
        }
    }
}
