package com.ycy.aiapplication.rag.eval.controller;

import com.ycy.aiapplication.framework.web.Result;
import com.ycy.aiapplication.framework.web.Results;
import com.ycy.aiapplication.rag.eval.dto.RagEvalChunkProbeRequest;
import com.ycy.aiapplication.rag.eval.dto.RagEvalChunkProbeResponse;
import com.ycy.aiapplication.rag.eval.dto.RagEvalResponse;
import com.ycy.aiapplication.rag.eval.service.RagEvalService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rag/eval")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.rag-eval.enabled", havingValue = "true")
public class RagEvalController {

    private final RagEvalService ragEvalService;

    @GetMapping("/retrieve")
    public Result<RagEvalResponse> retrieve(
            @RequestParam String question,
            @RequestParam(defaultValue = "10") int topK,
            @RequestParam(defaultValue = "true") boolean includeContexts,
            @RequestParam(required = false) String traceId,
            @RequestHeader(value = "X-Eval-Run-Id", required = false) String runId,
            @RequestHeader(value = "X-Eval-Query-Id", required = false) String queryId) {
        return Results.success(ragEvalService.evaluate(
                question,
                topK,
                includeContexts,
                traceId,
                runId,
                queryId));
    }

    @PostMapping("/datasets/chunk-probes")
    public Result<RagEvalChunkProbeResponse> generateChunkProbes(
            @RequestBody RagEvalChunkProbeRequest request) {
        return Results.success(ragEvalService.generateChunkProbes(request));
    }
}
