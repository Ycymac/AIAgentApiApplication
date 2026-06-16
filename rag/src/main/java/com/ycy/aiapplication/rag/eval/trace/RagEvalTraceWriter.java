package com.ycy.aiapplication.rag.eval.trace;

import com.alibaba.fastjson2.JSON;
import com.ycy.aiapplication.framework.convention.RetrievedChunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.rag-eval.enabled", havingValue = "true")
public class RagEvalTraceWriter {

    private final Object writeLock = new Object();

    @Value("${app.rag-eval.log-path:logs/rag-eval.jsonl}")
    private String logPath;

    @Value("${app.rag-eval.include-context-text:true}")
    private boolean includeContextText;

    public void write(String event, Map<String, ?> details) {
        try {
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("schemaVersion", 1);
            line.put("timestamp", Instant.now().toString());
            line.put("event", event);
            RagEvalTraceContext.current().ifPresent(state -> {
                line.put("traceId", state.traceId());
                line.put("runId", state.runId());
                line.put("queryId", state.queryId());
            });
            line.put("details", details);

            Path target = Path.of(logPath).toAbsolutePath().normalize();
            synchronized (writeLock) {
                Path parent = target.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.writeString(
                        target,
                        JSON.toJSONString(line) + System.lineSeparator(),
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND);
            }
        } catch (IOException | RuntimeException e) {
            log.warn("Failed to write RAG evaluation trace", e);
        }
    }

    public List<Map<String, Object>> chunkSummaries(List<RetrievedChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> summaries = new ArrayList<>();
        for (int index = 0; index < chunks.size(); index++) {
            RetrievedChunk chunk = chunks.get(index);
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("rank", index + 1);
            summary.put("chunkId", chunk.getId());
            summary.put("score", chunk.getScore());
            if (includeContextText) {
                summary.put("text", chunk.getText());
            }
            summaries.add(summary);
        }
        return summaries;
    }

    public List<Map<String, Object>> rerankComparisons(
            List<RetrievedChunk> candidates,
            List<RetrievedChunk> reranked) {
        if (candidates == null || reranked == null || reranked.isEmpty()) {
            return List.of();
        }
        Map<String, Integer> previousRanks = new HashMap<>();
        for (int index = 0; index < candidates.size(); index++) {
            previousRanks.putIfAbsent(candidates.get(index).getId(), index + 1);
        }

        List<Map<String, Object>> comparisons = new ArrayList<>();
        for (int index = 0; index < reranked.size(); index++) {
            RetrievedChunk chunk = reranked.get(index);
            Map<String, Object> comparison = new LinkedHashMap<>();
            comparison.put("chunkId", chunk.getId());
            comparison.put("previousRank", previousRanks.get(chunk.getId()));
            comparison.put("rerankRank", index + 1);
            comparison.put("rerankScore", chunk.getScore());
            comparisons.add(comparison);
        }
        return comparisons;
    }
}
