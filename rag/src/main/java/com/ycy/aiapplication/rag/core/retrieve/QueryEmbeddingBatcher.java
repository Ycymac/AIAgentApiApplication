package com.ycy.aiapplication.rag.core.retrieve;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ycy.aiapplication.infrastructure.ai.embedding.EmbeddingService;
import com.ycy.aiapplication.knowledge.dao.entity.KnowledgeBaseDO;
import com.ycy.aiapplication.knowledge.dao.mapper.KnowledgeBaseMapper;
import com.ycy.aiapplication.rag.core.retrieve.common.QueryEmbeddingContext;
import com.ycy.aiapplication.rag.core.retrieve.common.SearchTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 按模型批量生成一次检索请求所需的全部查询向量。
 */
@Slf4j
@Component
public class QueryEmbeddingBatcher {

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final EmbeddingService embeddingService;
    private final Executor executor;

    public QueryEmbeddingBatcher(KnowledgeBaseMapper knowledgeBaseMapper,
                                 EmbeddingService embeddingService,
                                 @Qualifier("ragInnerRetrievalThreadPoolExecutor") Executor executor) {
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.embeddingService = embeddingService;
        this.executor = executor;
    }

    /**
     * 一次性加载 collection 模型映射，并按模型批量向量化去重后的问题。
     */
    public QueryEmbeddingContext prepare(List<SearchTask> tasks) {
        List<String> collections = collectCollections(tasks);
        if (collections.isEmpty()) {
            return QueryEmbeddingContext.empty();
        }

        Map<String, String> collectionModels;
        try {
            collectionModels = loadCollectionModels(collections);
        } catch (Exception ex) {
            log.error("加载知识库 embedding 模型映射失败，跳过本轮向量检索", ex);
            logSummary(collections.size(), 0, 0, 0, 0, 0, 0L);
            return QueryEmbeddingContext.empty();
        }

        Map<String, LinkedHashSet<String>> modelQuestions = new LinkedHashMap<>();
        Set<String> missingCollections = new LinkedHashSet<>();
        for (SearchTask task : tasks) {
            if (task == null || !StringUtils.hasText(task.question()) || CollectionUtils.isEmpty(task.collections())) {
                continue;
            }
            for (String collection : task.collections()) {
                String modelId = collectionModels.get(collection);
                if (!StringUtils.hasText(modelId)) {
                    missingCollections.add(collection);
                    continue;
                }
                modelQuestions.computeIfAbsent(modelId, ignored -> new LinkedHashSet<>())
                        .add(task.question());
            }
        }
        if (!missingCollections.isEmpty()) {
            log.error("部分 collection 缺少有效 embedding 模型映射，已跳过，collections={}", missingCollections);
        }

        if (modelQuestions.isEmpty()) {
            logSummary(collections.size(), 0, 0, 0, 0, 0, 0L);
            return new QueryEmbeddingContext(collectionModels, Map.of());
        }

        long embeddingStart = System.currentTimeMillis();
        List<ModelFuture> futures = modelQuestions.entrySet().stream()
                .map(entry -> {
                    List<String> questions = List.copyOf(entry.getValue());
                    return new ModelFuture(
                            entry.getKey(),
                            questions,
                            CompletableFuture.supplyAsync(
                                    () -> embedAndValidate(questions, entry.getKey()),
                                    executor));
                })
                .toList();

        Map<QueryEmbeddingContext.EmbeddingKey, float[]> vectors = new LinkedHashMap<>();
        int successfulModels = 0;
        int failedModels = 0;
        for (ModelFuture modelFuture : futures) {
            try {
                List<float[]> modelVectors = modelFuture.future().join();
                for (int index = 0; index < modelFuture.questions().size(); index++) {
                    vectors.put(
                            new QueryEmbeddingContext.EmbeddingKey(
                                    modelFuture.modelId(), modelFuture.questions().get(index)),
                            modelVectors.get(index));
                }
                successfulModels++;
            } catch (RuntimeException ex) {
                failedModels++;
                Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                log.error("指定 embedding 模型批量向量化失败，已隔离该模型，model={}",
                        modelFuture.modelId(), cause);
            }
        }

        int uniqueModelQuestionCount = modelQuestions.values().stream()
                .mapToInt(Set::size)
                .sum();
        logSummary(
                collections.size(),
                modelQuestions.size(),
                uniqueModelQuestionCount,
                futures.size(),
                successfulModels,
                failedModels,
                System.currentTimeMillis() - embeddingStart);
        return new QueryEmbeddingContext(collectionModels, vectors);
    }

    private List<String> collectCollections(List<SearchTask> tasks) {
        if (CollectionUtils.isEmpty(tasks)) {
            return List.of();
        }
        return tasks.stream()
                .filter(task -> task != null && !CollectionUtils.isEmpty(task.collections()))
                .flatMap(task -> task.collections().stream())
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private Map<String, String> loadCollectionModels(List<String> collections) {
        List<KnowledgeBaseDO> knowledgeBases = knowledgeBaseMapper.selectList(
                new QueryWrapper<KnowledgeBaseDO>()
                        .select("collection_name", "embedding_model")
                        .in("collection_name", collections)
                        .eq("deleted", 0));
        if (CollectionUtils.isEmpty(knowledgeBases)) {
            return Map.of();
        }

        Map<String, String> result = new LinkedHashMap<>();
        for (KnowledgeBaseDO knowledgeBase : knowledgeBases) {
            if (knowledgeBase == null
                    || !StringUtils.hasText(knowledgeBase.getCollectionName())
                    || !StringUtils.hasText(knowledgeBase.getEmbeddingModel())) {
                continue;
            }
            result.putIfAbsent(knowledgeBase.getCollectionName(), knowledgeBase.getEmbeddingModel());
        }
        return result;
    }

    private List<float[]> embedAndValidate(List<String> questions, String modelId) {
        List<List<Float>> embeddings = embeddingService.embedBatch(questions, modelId);
        if (embeddings == null || embeddings.size() != questions.size()) {
            throw new IllegalStateException("Embedding result size does not match question size");
        }

        List<float[]> vectors = new ArrayList<>(embeddings.size());
        for (List<Float> embedding : embeddings) {
            if (CollectionUtils.isEmpty(embedding)) {
                throw new IllegalStateException("Embedding result contains empty vector");
            }
            float[] vector = new float[embedding.size()];
            for (int index = 0; index < embedding.size(); index++) {
                Float value = embedding.get(index);
                if (value == null) {
                    throw new IllegalStateException("Embedding result contains null value");
                }
                vector[index] = value;
            }
            vectors.add(vector);
        }
        return vectors;
    }

    private void logSummary(int collectionCount,
                            int modelCount,
                            int uniqueModelQuestionCount,
                            int embeddingBatchInvocationCount,
                            int successfulModelCount,
                            int failedModelCount,
                            long embeddingDurationMs) {
        log.info("查询向量批处理完成，collectionCount={}, modelCount={}, uniqueModelQuestionCount={}, "
                        + "embeddingBatchInvocationCount={}, successfulModelCount={}, failedModelCount={}, "
                        + "embeddingDurationMs={}",
                collectionCount,
                modelCount,
                uniqueModelQuestionCount,
                embeddingBatchInvocationCount,
                successfulModelCount,
                failedModelCount,
                embeddingDurationMs);
    }

    private record ModelFuture(
            String modelId,
            List<String> questions,
            CompletableFuture<List<float[]>> future
    ) {
    }
}
