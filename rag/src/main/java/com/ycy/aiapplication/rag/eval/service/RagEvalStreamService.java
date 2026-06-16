package com.ycy.aiapplication.rag.eval.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface RagEvalStreamService {

    SseEmitter stream(
            String question,
            int topK,
            boolean deepThinking,
            String traceId,
            String runId,
            String queryId);
}
