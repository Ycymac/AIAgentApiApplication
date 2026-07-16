package com.ycy.aiapplication.rag.eval.dto;

import java.util.List;
import java.util.Map;

public final class RagEvalStreamEvents {

    private RagEvalStreamEvents() {
    }

    public record MetaEvent(
            String traceId,
            String runId,
            String queryId,
            String taskId,
            String intentMode) {
    }

    public record RetrievalEvent(
            String traceId,
            String route,
            List<RagEvalResponse.ChunkView> chunks,
            List<RagEvalResponse.ChannelView> channels,
            Map<String, Long> timings) {
    }

    public record FinishEvent(
            String status,
            String error,
            int responseChars,
            Map<String, Long> timings) {
    }
}
