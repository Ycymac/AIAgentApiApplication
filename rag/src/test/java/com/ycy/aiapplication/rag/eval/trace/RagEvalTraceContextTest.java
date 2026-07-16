package com.ycy.aiapplication.rag.eval.trace;

import com.alibaba.ttl.threadpool.TtlExecutors;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RagEvalTraceContextTest {

    @Test
    void propagatesTraceStateToTtlExecutor() throws Exception {
        ExecutorService delegate = Executors.newSingleThreadExecutor();
        ExecutorService executor = TtlExecutors.getTtlExecutorService(delegate);
        try (RagEvalTraceContext.Scope ignored = RagEvalTraceContext.open("trace", "run", "query")) {
            RagEvalTraceContext.State state = executor.submit(
                    () -> RagEvalTraceContext.current().orElseThrow()).get();
            assertEquals("trace", state.traceId());
            assertEquals("run", state.runId());
            assertEquals("query", state.queryId());
        } finally {
            executor.shutdownNow();
        }
    }
}
