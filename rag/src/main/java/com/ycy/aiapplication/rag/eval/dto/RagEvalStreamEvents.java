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
            String intentMode,
            String rerankMode,
            double rerankKeepRatio) {
    }

    public record RetrievalEvent(
            String traceId,
            String route,
            List<RagEvalResponse.ChunkView> chunks,
            List<RagEvalResponse.ChannelView> channels,
            Map<String, Long> timings,
            List<ChunkContentDetail> chunkContents) {
    }

    public record ChunkContentDetail(
            String chunkId,
            String content,
            Double score,
            Integer rank) {
    }

    public record PromptEvent(
            String traceId,
            List<PromptMessageView> messages,
            int messageCount,
            int totalChars,
            ChatRequestView request,
            Map<String, Long> timings) {
    }

    public record PromptMessageView(
            String role,
            String content) {
    }

    public record ChatRequestView(
            String provider,
            String modelId,
            Double temperature,
            Double topP,
            Integer topK,
            Integer maxTokens,
            Boolean thinking,
            Boolean enableTools) {
    }

    public record FinishEvent(
            String status,
            String error,
            int responseChars,
            Map<String, Long> timings) {
    }
}
