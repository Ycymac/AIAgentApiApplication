package com.ycy.aiapplication.rag.eval.trace;

import com.ycy.aiapplication.framework.convention.RetrievedChunk;
import com.ycy.aiapplication.rag.core.retrieve.channel.SearchChannelResult;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Aspect
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.rag-eval.enabled", havingValue = "true")
public class RagEvalRetrievalAspect {

    private final RagEvalTraceWriter traceWriter;

    @Around("execution(* com.ycy.aiapplication.rag.core.retrieve.channel.SearchChannel+.search(..))")
    public Object observeSearchChannel(ProceedingJoinPoint joinPoint) throws Throwable {
        if (!RagEvalTraceContext.isActive()) {
            return joinPoint.proceed();
        }

        long startedAt = System.nanoTime();
        try {
            Object result = joinPoint.proceed();
            if (result instanceof SearchChannelResult channelResult) {
                Map<String, Object> details = new LinkedHashMap<>();
                details.put("channelName", channelResult.getChannelName());
                details.put("channelType", channelResult.getChannelType());
                details.put("confidence", channelResult.getConfidence());
                details.put("reportedLatencyMs", channelResult.getLatencyMs());
                details.put("observedLatencyMs", elapsedMs(startedAt));
                details.put("metadata", channelResult.getMetadata());
                details.put("chunks", traceWriter.chunkSummaries(channelResult.getChunks()));
                traceWriter.write("eval.channel.retrieval.completed", details);
            }
            return result;
        } catch (Throwable throwable) {
            traceWriter.write("eval.channel.retrieval.failed", Map.of(
                    "method", joinPoint.getSignature().toShortString(),
                    "latencyMs", elapsedMs(startedAt),
                    "error", throwable.toString()));
            throw throwable;
        }
    }

    @Around("execution(* com.ycy.aiapplication.infrastructure.ai.rerank.RerankClient+.rerank(..))")
    public Object observeRerank(ProceedingJoinPoint joinPoint) throws Throwable {
        if (!RagEvalTraceContext.isActive()) {
            return joinPoint.proceed();
        }

        Object[] args = joinPoint.getArgs();
        String query = (String) args[0];
        @SuppressWarnings("unchecked")
        List<RetrievedChunk> candidates = (List<RetrievedChunk>) args[1];
        int topN = (int) args[2];

        long startedAt = System.nanoTime();
        traceWriter.write("eval.rerank.started", details(
                "query", query,
                "topN", topN,
                "candidates", traceWriter.chunkSummaries(candidates)));
        try {
            Object result = joinPoint.proceed();
            @SuppressWarnings("unchecked")
            List<RetrievedChunk> reranked = (List<RetrievedChunk>) result;
            traceWriter.write("eval.rerank.completed", details(
                    "latencyMs", elapsedMs(startedAt),
                    "chunks", traceWriter.chunkSummaries(reranked),
                    "rankChanges", traceWriter.rerankComparisons(candidates, reranked)));
            return result;
        } catch (Throwable throwable) {
            traceWriter.write("eval.rerank.failed", Map.of(
                    "latencyMs", elapsedMs(startedAt),
                    "error", throwable.toString()));
            throw throwable;
        }
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private Map<String, Object> details(Object... keyValues) {
        Map<String, Object> details = new LinkedHashMap<>();
        for (int index = 0; index < keyValues.length; index += 2) {
            details.put((String) keyValues[index], keyValues[index + 1]);
        }
        return details;
    }
}
