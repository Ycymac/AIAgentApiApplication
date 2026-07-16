package com.ycy.aiapplication.rag.eval.dto;

import java.util.List;

public record RagEvalResponse(
        String traceId,
        String runId,
        String queryId,
        String question,
        String rewrittenQuestion,
        List<String> subQuestions,
        String route,
        String intentMode,
        String guidancePrompt,
        boolean hasKb,
        boolean hasMcp,
        List<String> retrievedDocIds,
        List<String> retrievedDocNames,
        List<String> retrievedChunkIds,
        List<String> retrievedContexts,
        List<String> retrievedContextDocIds,
        List<String> intentLeafIds,
        List<IntentView> intents,
        List<ChunkView> chunks,
        List<ChannelView> channels,
        TimingView timings) {

    public record IntentView(
            String subQuestion,
            String routeKind,
            boolean globalKbFallback,
            Double firstLayerRagScore,
            Double firstLayerSystemScore,
            List<IntentNodeView> nodes,
            List<IntentNodeView> candidateNodes) {
    }

    public record IntentNodeView(
            String id,
            String name,
            String kbId,
            String collectionName,
            String kind,
            double score) {
    }

    public record ChunkView(
            int rank,
            String chunkId,
            String vectorChunkId,
            String docId,
            String docName,
            String kbId,
            Float score,
            String text) {
    }

    public record ChannelView(
            String name,
            String type,
            double confidence,
            long latencyMs,
            List<ChunkView> chunks) {
    }

    public record TimingView(
            long totalMs,
            long rewriteMs,
            long intentMs,
            long guidanceMs,
            long retrievalMs,
            long promptMs) {
    }
}
