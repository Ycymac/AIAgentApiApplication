package com.ycy.aiapplication.infrastructure.ai.chat.toolkit;

import lombok.Getter;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * First packet awaiter used during stream routing probe.
 */
public class FirstPacketAwaiter {

    private final CompletableFuture<Result> firstPacket = new CompletableFuture<>();

    /**
     * Mark that valid stream content has been received.
     */
    public void markContent() {
        firstPacket.complete(Result.success());
    }

    /**
     * Mark that the stream completed before any valid content arrived.
     */
    public void markComplete() {
        firstPacket.complete(Result.noContent());
    }

    /**
     * Mark that the stream failed before first packet probing succeeded.
     */
    public void markError(Throwable throwable) {
        firstPacket.complete(Result.error(throwable));
    }

    /**
     * Wait for the first decisive probe result within the given timeout.
     */
    public Result await(long timeout, TimeUnit unit) throws InterruptedException {
        try {
            return firstPacket.get(timeout, unit);
        } catch (TimeoutException ex) {
            return Result.timeout();
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            return Result.error(cause);
        }
    }

    /**
     * First packet probe result.
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
         * Create a success result.
         */
        public static Result success() {
            return new Result(Type.SUCCESS, null);
        }

        /**
         * Create an error result.
         */
        public static Result error(Throwable throwable) {
            return new Result(Type.ERROR, throwable);
        }

        /**
         * Create a timeout result.
         */
        public static Result timeout() {
            return new Result(Type.TIMEOUT, null);
        }

        /**
         * Create a no-content result.
         */
        public static Result noContent() {
            return new Result(Type.NO_CONTENT, null);
        }

        /**
         * Return whether this result indicates a successful first packet.
         */
        public boolean isSuccess() {
            return type == Type.SUCCESS;
        }
    }
}
