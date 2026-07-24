package com.ycy.aiapplication.rag.eval.controller;

import com.ycy.aiapplication.rag.eval.service.RagEvalStreamService;
import com.ycy.aiapplication.rag.eval.intent.RagEvalIntentMode;
import com.ycy.aiapplication.rag.core.retrieve.RetrievalEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Set;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

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
            @RequestParam(defaultValue = "combined") String intentMode,
            @RequestParam(defaultValue = "per_channel") String rerankMode,
            @RequestParam(required = false) Double rerankKeepRatio,
            @RequestParam(required = false) String traceId,
            @RequestHeader(value = "X-Eval-Run-Id", required = false) String runId,
            @RequestHeader(value = "X-Eval-Query-Id", required = false) String queryId) {
        RetrievalEngine.RerankMode resolvedRerankMode = parseRerankMode(rerankMode);
        double resolvedKeepRatio = rerankKeepRatio == null ? 1.0D : rerankKeepRatio;
        validateRerankOptions(resolvedRerankMode, rerankKeepRatio, resolvedKeepRatio);
        return ragEvalStreamService.stream(
                question,
                topK,
                deepThinking,
                RagEvalIntentMode.parse(intentMode),
                resolvedRerankMode,
                resolvedKeepRatio,
                traceId,
                runId,
                queryId);
    }

    private RetrievalEngine.RerankMode parseRerankMode(String value) {
        try {
            return RetrievalEngine.RerankMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(BAD_REQUEST, "rerankMode must be per_channel or unified");
        }
    }

    private void validateRerankOptions(RetrievalEngine.RerankMode mode,
                                       Double requestedKeepRatio,
                                       double resolvedKeepRatio) {
        if (mode == RetrievalEngine.RerankMode.PER_CHANNEL && requestedKeepRatio != null) {
            throw new ResponseStatusException(BAD_REQUEST, "rerankKeepRatio is only valid for unified mode");
        }
        if (mode == RetrievalEngine.RerankMode.UNIFIED
                && !Set.of(0.2D, 0.4D, 0.6D, 1.0D).contains(resolvedKeepRatio)) {
            throw new ResponseStatusException(BAD_REQUEST, "rerankKeepRatio must be 0.2, 0.4, 0.6, or 1.0");
        }
    }
}
