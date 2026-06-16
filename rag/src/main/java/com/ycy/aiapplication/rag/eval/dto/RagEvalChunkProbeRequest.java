package com.ycy.aiapplication.rag.eval.dto;

import java.util.List;

public record RagEvalChunkProbeRequest(
        String kbId,
        List<String> docIds,
        List<String> chunkIds,
        Integer limit,
        Integer questionsPerChunk,
        Integer minChars,
        Long seed) {
}
