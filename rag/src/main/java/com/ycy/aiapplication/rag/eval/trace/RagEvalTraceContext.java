package com.ycy.aiapplication.rag.eval.trace;

import com.alibaba.ttl.TransmittableThreadLocal;

import java.util.Optional;

public final class RagEvalTraceContext {

    private static final ThreadLocal<State> CURRENT = new TransmittableThreadLocal<>();

    private RagEvalTraceContext() {
    }

    public static Scope open(String traceId, String runId, String queryId) {
        State previous = CURRENT.get();
        CURRENT.set(new State(traceId, runId, queryId));
        return () -> {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        };
    }

    public static Optional<State> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static boolean isActive() {
        return CURRENT.get() != null;
    }

    public record State(String traceId, String runId, String queryId) {
    }

    @FunctionalInterface
    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }
}
