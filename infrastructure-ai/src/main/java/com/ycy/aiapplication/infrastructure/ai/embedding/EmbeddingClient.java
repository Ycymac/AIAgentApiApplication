package com.ycy.aiapplication.infrastructure.ai.embedding;

import java.util.List;

/**
 * Embedding 平台客户端。
 * 只负责调用自身平台，不负责默认路由与降级策略。
 */
public interface EmbeddingClient {

    /**
     * 当前客户端所属 provider。
     */
    String provider();

    /**
     * 当前客户端是否支持给定 provider / modelId 组合。
     */
    boolean supports(String provider, String modelId);

    /**
     * 返回当前模型对应的向量维度。
     */
    int dimension(String modelId);

    /**
     * 单条文本向量化。
     */
    default List<Float> embed(String text, String modelId, Integer dimension) {
        return embedBatch(List.of(text), modelId, dimension, 1).get(0);
    }

    /**
     * 批量文本向量化。
     */
    List<List<Float>> embedBatch(List<String> texts, String modelId, Integer dimension, Integer batchSize);
}
