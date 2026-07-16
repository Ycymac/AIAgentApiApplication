package com.ycy.aiapplication.rag.eval.service;

import com.ycy.aiapplication.rag.eval.intent.RagEvalIntentMode;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface RagEvalStreamService {

    SseEmitter stream(
            String question,
            int topK,
            boolean deepThinking,
            RagEvalIntentMode intentMode,
            String traceId,
            String runId,
            String queryId);
}
