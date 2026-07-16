package com.ycy.aiapplication.rag.eval.trace;

import com.ycy.aiapplication.framework.convention.RetrievedChunk;
import com.ycy.aiapplication.rag.core.retrieve.common.QueryEmbeddingContext;
import com.ycy.aiapplication.rag.core.retrieve.common.RetrieveRequest;
import com.ycy.aiapplication.rag.core.retrieve.common.SearchTask;
import com.ycy.aiapplication.rag.core.retrieve.channel.SearchChannelResult;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Aspect
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.rag-eval.enabled", havingValue = "true")
public class RagEvalRetrievalAspect {

    private final RagEvalTraceWriter traceWriter;

    @Around("execution(* com.ycy.aiapplication.rag.core.retrieve.QueryEmbeddingBatcher.prepare(..))")
    public Object observeEmbeddingPreparation(ProceedingJoinPoint joinPoint) throws Throwable {
        if (!RagEvalTraceContext.isActive()) {
            return joinPoint.proceed();
        }

        List<SearchTask> tasks = searchTasks(joinPoint.getArgs());
        long startedAt = System.nanoTime();
        try {
            Object result = joinPoint.proceed();
            if (result instanceof QueryEmbeddingContext context) {
                Set<String> models = new HashSet<>(context.collectionModels().values());
                int collectionQuestionTargets = collectionQuestionTargets(tasks);
                traceWriter.write("eval.embedding.preparation.completed", details(
                        "latencyMs", elapsedMs(startedAt),
                        "taskCount", tasks.size(),
                        "collectionCount", context.collectionModels().size(),
                        "modelCount", models.size(),
                        "vectorCount", context.vectors().size(),
                        "collectionQuestionTargetCount", collectionQuestionTargets,
                        "avoidedEmbeddingInvocationEstimate",
                        Math.max(0, collectionQuestionTargets - models.size())));
            }
            return result;
        } catch (Throwable throwable) {
            traceWriter.write("eval.embedding.preparation.failed", details(
                    "latencyMs", elapsedMs(startedAt),
                    "taskCount", tasks.size(),
                    "error", throwable.toString()));
            throw throwable;
        }
    }

    @Around("execution(* com.ycy.aiapplication.infrastructure.ai.embedding.EmbeddingService+.embedBatch(java.util.List,java.lang.String))")
    public Object observeEmbeddingBatch(ProceedingJoinPoint joinPoint) throws Throwable {
        if (!RagEvalTraceContext.isActive()) {
            return joinPoint.proceed();
        }

        Object[] args = joinPoint.getArgs();
        List<?> texts = args.length > 0 && args[0] instanceof List<?> values ? values : List.of();
        String modelId = args.length > 1 ? Objects.toString(args[1], null) : null;
        long startedAt = System.nanoTime();
        try {
            Object result = joinPoint.proceed();
            int vectorCount = result instanceof Collection<?> values ? values.size() : 0;
            traceWriter.write("eval.embedding.batch.completed", details(
                    "modelId", modelId,
                    "inputCount", texts.size(),
                    "vectorCount", vectorCount,
                    "latencyMs", elapsedMs(startedAt)));
            return result;
        } catch (Throwable throwable) {
            traceWriter.write("eval.embedding.batch.failed", details(
                    "modelId", modelId,
                    "inputCount", texts.size(),
                    "latencyMs", elapsedMs(startedAt),
                    "error", throwable.toString()));
            throw throwable;
        }
    }

    @Around("execution(* com.ycy.aiapplication.rag.core.retrieve.service.RetrieverService+.retrieveByVector(..))")
    public Object observeVectorSearch(ProceedingJoinPoint joinPoint) throws Throwable {
        if (!RagEvalTraceContext.isActive()) {
            return joinPoint.proceed();
        }

        Object[] args = joinPoint.getArgs();
        RetrieveRequest request = args.length > 1 && args[1] instanceof RetrieveRequest value ? value : null;
        long startedAt = System.nanoTime();
        try {
            Object result = joinPoint.proceed();
            int resultCount = result instanceof Collection<?> values ? values.size() : 0;
            traceWriter.write("eval.vector.search.completed", details(
                    "collectionName", request == null ? null : request.getCollectionName(),
                    "topK", request == null ? null : request.getTopK(),
                    "resultCount", resultCount,
                    "latencyMs", elapsedMs(startedAt)));
            return result;
        } catch (Throwable throwable) {
            traceWriter.write("eval.vector.search.failed", details(
                    "collectionName", request == null ? null : request.getCollectionName(),
                    "topK", request == null ? null : request.getTopK(),
                    "latencyMs", elapsedMs(startedAt),
                    "error", throwable.toString()));
            throw throwable;
        }
    }

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

    @SuppressWarnings("unchecked")
    private List<SearchTask> searchTasks(Object[] args) {
        if (args.length == 0 || !(args[0] instanceof List<?> values)) {
            return List.of();
        }
        return (List<SearchTask>) values;
    }

    private int collectionQuestionTargets(List<SearchTask> tasks) {
        Set<String> targets = new HashSet<>();
        for (SearchTask task : tasks) {
            if (task == null || task.question() == null || task.collections() == null) {
                continue;
            }
            for (String collection : task.collections()) {
                if (collection != null) {
                    targets.add(collection + "\u0000" + task.question());
                }
            }
        }
        return targets.size();
    }

    private Map<String, Object> details(Object... keyValues) {
        Map<String, Object> details = new LinkedHashMap<>();
        for (int index = 0; index < keyValues.length; index += 2) {
            details.put((String) keyValues[index], keyValues[index + 1]);
        }
        return details;
    }
}
