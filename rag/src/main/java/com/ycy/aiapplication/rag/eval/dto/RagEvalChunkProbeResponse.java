package com.ycy.aiapplication.rag.eval.dto;

import java.util.List;

public record RagEvalChunkProbeResponse(
        String kbId,
        int requestedLimit,
        int generatedCount,
        long generationSeed,
        List<ProbeSample> samples) {

    public record ProbeSample(
            String queryId,
            String query,
            String topic,
            String difficulty,
            boolean requiresRag,
            List<String> expectedChunkIds,
            List<String> expectedDocIds,
            List<String> expectedDocNames,
            String expectedRoute,
            String kbId,
            String docId,
            String docName,
            Integer chunkIndex,
            String chunkPreview,
            long generationSeed) {
    }
}
