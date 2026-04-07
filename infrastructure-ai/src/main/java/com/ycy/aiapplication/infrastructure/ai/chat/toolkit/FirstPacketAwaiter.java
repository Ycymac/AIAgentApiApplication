package com.ycy.aiapplication.infrastructure.ai.chat.toolkit;

import lombok.Getter;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 首包等待器。
 * 在流式路由探测阶段用于等待当前模型返回首个有效事件。
 */
public class FirstPacketAwaiter {

    private final CountDownLatch latch = new CountDownLatch(1);
    private final AtomicBoolean hasContent = new AtomicBoolean(false);
    private final AtomicBoolean eventFired = new AtomicBoolean(false);
    private final AtomicReference<Throwable> error = new AtomicReference<>();

    /**
     * 标记已收到有效内容。
     */
    public void markContent() {
        hasContent.set(true);
        fireEventOnce();
    }

    /**
     * 标记流式过程正常完成。
     */
    public void markComplete() {
        fireEventOnce();
    }

    /**
     * 标记流式过程出现异常。
     */
    public void markError(Throwable throwable) {
        error.set(throwable);
        fireEventOnce();
    }

    /**
     * 保证只触发一次首包结果事件。
     */
    private void fireEventOnce() {
        if (eventFired.compareAndSet(false, true)) {
            latch.countDown();
        }
    }

    /**
     * 在限定时间内等待首包探测结果。
     */
    public Result await(long timeout, TimeUnit unit) throws InterruptedException {
        boolean completed = latch.await(timeout, unit);
        if (error.get() != null) {
            return Result.error(error.get());
        }
        if (!completed) {
            return Result.timeout();
        }
        if (!hasContent.get()) {
            return Result.noContent();
        }
        return Result.success();
    }

    /**
     * 首包等待结果。
     */
    @Getter
    public static class Result {

        public enum Type {SUCCESS, ERROR, TIMEOUT, NO_CONTENT}

        private final Type type;
        private final Throwable error;

        private Result(Type type, Throwable error) {
            this.type = type;
            this.error = error;
        }

        /**
         * 创建成功结果。
         */
        public static Result success() {
            return new Result(Type.SUCCESS, null);
        }

        /**
         * 创建异常结果。
         */
        public static Result error(Throwable throwable) {
            return new Result(Type.ERROR, throwable);
        }

        /**
         * 创建超时结果。
         */
        public static Result timeout() {
            return new Result(Type.TIMEOUT, null);
        }

        /**
         * 创建无内容结果。
         */
        public static Result noContent() {
            return new Result(Type.NO_CONTENT, null);
        }

        /**
         * 判断当前结果是否为成功。
         */
        public boolean isSuccess() {
            return type == Type.SUCCESS;
        }
    }
}
