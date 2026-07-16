package com.ycy.aiapplication.rag.core.retrieve.channel.retriver;

import com.ycy.aiapplication.framework.convention.RetrievedChunk;
import com.ycy.aiapplication.rag.core.retrieve.common.RetrieveRequest;
import com.ycy.aiapplication.rag.core.retrieve.service.RetrieverService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 基于 collection 的并行向量检索器。
 * <p>
 * 整体职责：
 * 1. 接收已经按模型生成的查询向量。
 * 2. 并行调用底层 RetrieverService 完成 collection 检索。
 * 3. 汇总 collection 维度的结果与成功失败统计。
 */
@Slf4j
@Component
public class CollectionParallelRetriever {

    private final RetrieverService retrieverService;
    private final Executor executor;

    public CollectionParallelRetriever(RetrieverService retrieverService,
                                       @Qualifier("ragInnerRetrievalThreadPoolExecutor") Executor executor) {
        this.retrieverService = retrieverService;
        this.executor = executor;
    }

    /**
     * 对多个 collection 并行执行预计算向量检索。
     *
     * @param question 当前检索问题。
     * @param collectionVectors collection 与查询向量的映射。
     * @param topK 每个 collection 需要返回的分块条数。
     */
    public ParallelRetrievalResult executeParallelRetrievalWithTargets(
            String question,
            Map<String, float[]> collectionVectors,
            int topK) {
        if (collectionVectors == null || collectionVectors.isEmpty()) {
            return new ParallelRetrievalResult(List.of(), Map.of(), 0, 0);
        }

        record RetrievalFuture(String collectionName, CompletableFuture<List<RetrievedChunk>> future) {
        }

        List<RetrievalFuture> futures = collectionVectors.entrySet().stream()
                .map(entry -> new RetrievalFuture(
                        entry.getKey(),
                        CompletableFuture.supplyAsync(
                                () -> retrieve(question, entry.getKey(), entry.getValue(), topK),
                                executor)))
                .toList();

        List<RetrievedChunk> allChunks = new ArrayList<>();
        Map<String, List<RetrievedChunk>> targetChunks = new LinkedHashMap<>();
        int successCount = 0;
        int failureCount = 0;
        for (RetrievalFuture retrievalFuture : futures) {
            try {
                List<RetrievedChunk> chunks = retrievalFuture.future().join();
                allChunks.addAll(chunks);
                targetChunks.put(retrievalFuture.collectionName(), chunks);
                successCount++;
            } catch (RuntimeException ex) {
                failureCount++;
                targetChunks.put(retrievalFuture.collectionName(), List.of());
                Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                log.error("collection vector retrieve failed, collection={}",
                        retrievalFuture.collectionName(), cause);
            }
        }

        log.info("collection-parallel-retrieval finished, targets={}, success={}, failure={}, chunks={}",
                collectionVectors.size(), successCount, failureCount, allChunks.size());
        return new ParallelRetrievalResult(allChunks, targetChunks, successCount, failureCount);
    }

    private List<RetrievedChunk> retrieve(String question,
                                          String collectionName,
                                          float[] vector,
                                          int topK) {
        return retrieverService.retrieveByVector(
                vector,
                RetrieveRequest.builder()
                        .collectionName(collectionName)
                        .query(question)
                        .topK(topK)
                        .build());
    }

    public record ParallelRetrievalResult(
            List<RetrievedChunk> allChunks,
            Map<String, List<RetrievedChunk>> targetChunks,
            int successCount,
            int failureCount
    ) {
    }
}
