package com.ycy.aiapplication.rag.eval.service;

import com.ycy.aiapplication.rag.eval.dto.RagEvalResponse;
import com.ycy.aiapplication.rag.eval.dto.RagEvalChunkProbeRequest;
import com.ycy.aiapplication.rag.eval.dto.RagEvalChunkProbeResponse;
import com.ycy.aiapplication.rag.eval.intent.RagEvalIntentMode;

public interface RagEvalService {

    RagEvalResponse evaluate(
            String question,
            int topK,
            boolean includeContexts,
            RagEvalIntentMode intentMode,
            String traceId,
            String runId,
            String queryId);

    RagEvalChunkProbeResponse generateChunkProbes(RagEvalChunkProbeRequest request);
}
