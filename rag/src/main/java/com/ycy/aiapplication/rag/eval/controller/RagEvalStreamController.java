package com.ycy.aiapplication.rag.eval.controller;

import com.ycy.aiapplication.rag.eval.service.RagEvalStreamService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.rag-eval.enabled", havingValue = "true")
public class RagEvalStreamController {

    private final RagEvalStreamService ragEvalStreamService;

    @GetMapping(value = "/rag/eval/chat-stream", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter stream(
            @RequestParam String question,
            @RequestParam(defaultValue = "10") int topK,
            @RequestParam(defaultValue = "false") boolean deepThinking,
            @RequestParam(required = false) String traceId,
            @RequestHeader(value = "X-Eval-Run-Id", required = false) String runId,
            @RequestHeader(value = "X-Eval-Query-Id", required = false) String queryId) {
        return ragEvalStreamService.stream(
                question,
                topK,
                deepThinking,
                traceId,
                runId,
                queryId);
    }
}
