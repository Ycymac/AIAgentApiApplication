package com.ycy.aiapplication.rag.core.retrieve.common;

import java.util.Map;

/**
 * 一次检索请求内可复用的查询向量上下文。
 *
 * @param collectionModels collection 与 embedding 模型的映射
 * @param vectors 模型和问题对应的查询向量
 */
public record QueryEmbeddingContext(
        Map<String, String> collectionModels,
        Map<EmbeddingKey, float[]> vectors
) {

    public QueryEmbeddingContext {
        collectionModels = Map.copyOf(collectionModels);
        vectors = Map.copyOf(vectors);
    }

    public static QueryEmbeddingContext empty() {
        return new QueryEmbeddingContext(Map.of(), Map.of());
    }

    /**
     * 获取指定 collection 和问题对应的预计算向量。
     */
    public float[] resolveVector(String collectionName, String question) {
        String modelId = collectionModels.get(collectionName);
        return modelId == null ? null : vectors.get(new EmbeddingKey(modelId, question));
    }

    /**
     * 查询向量的请求级唯一键。
     */
    public record EmbeddingKey(String modelId, String question) {
    }
}
