package com.ycy.aiapplication.infrastructure.ai.embedding;

import java.util.List;

/**
 * 一次 embedding 请求的路由结果。
 */
public record EmbeddingRoute(
        EmbeddingTarget primary,
        List<EmbeddingTarget> fallbacks
) {

    public List<EmbeddingTarget> attempts() {
        List<EmbeddingTarget> all = new java.util.ArrayList<>();
        all.add(primary);
        all.addAll(fallbacks);
        return all;
    }

    public record EmbeddingTarget(String provider, String modelId) {
    }
}
