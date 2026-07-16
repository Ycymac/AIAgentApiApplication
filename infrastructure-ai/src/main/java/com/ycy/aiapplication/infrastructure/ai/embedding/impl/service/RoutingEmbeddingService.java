package com.ycy.aiapplication.infrastructure.ai.embedding.impl.service;

import com.ycy.aiapplication.infrastructure.ai.config.AIModelProperties;
import com.ycy.aiapplication.infrastructure.ai.embedding.EmbeddingClient;
import com.ycy.aiapplication.infrastructure.ai.embedding.EmbeddingModelRegistry;
import com.ycy.aiapplication.infrastructure.ai.embedding.EmbeddingRoute;
import com.ycy.aiapplication.infrastructure.ai.embedding.EmbeddingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Embedding 路由服务。
 * 负责根据 embedding 配置计算 provider/model 路由，并在失败时执行降级。
 */
@Service
@RequiredArgsConstructor
public class RoutingEmbeddingService implements EmbeddingService {

    private final List<EmbeddingClient> embeddingClients;
    private final AIModelProperties properties;
    private final EmbeddingModelRegistry modelRegistry;

    @Override
    public int dimension() {
        Integer dimension = properties.getEmbedding().getDimension();
        return dimension == null ? 0 : dimension;
    }

    @Override
    public List<Float> embed(String text) {
        return embed(text, null);
    }

    @Override
    public List<Float> embed(String text, String modelId) {
        List<List<Float>> vectors = embedBatch(List.of(text), modelId);
        return vectors.get(0);
    }

    @Override
    public List<List<Float>> embedBatch(List<String> texts) {
        return embedBatch(texts, null);
    }

    @Override
    public List<List<Float>> embedBatch(List<String> texts, String modelId) {
        if (CollectionUtils.isEmpty(texts)) {
            return List.of();
        }

        Map<String, EmbeddingClient> clientMap = buildClientMap();
        EmbeddingRoute route = resolveRoute(modelId);
        RuntimeException lastException = null;
        int expectedDimension = dimension();
        int batchSize = resolveBatchSize();

        for (EmbeddingRoute.EmbeddingTarget target : route.attempts()) {
            EmbeddingClient client = clientMap.get(modelRegistry.normalize(target.provider()));
            if (client == null || !client.supports(target.provider(), target.modelId())) {
                continue;
            }
            try {
                return client.embedBatch(texts, target.modelId(), expectedDimension, batchSize);
            } catch (RuntimeException ex) {
                lastException = ex;
                if (!Boolean.TRUE.equals(properties.getEmbedding().getFallbackEnabled())
                        || target.equals(route.attempts().get(route.attempts().size() - 1))) {
                    throw ex;
                }
            }
        }

        if (lastException != null) {
            throw lastException;
        }
        throw new IllegalStateException("No available embedding client found");
    }

    private Map<String, EmbeddingClient> buildClientMap() {
        Map<String, EmbeddingClient> clientMap = new LinkedHashMap<>();
        for (EmbeddingClient client : embeddingClients) {
            clientMap.put(modelRegistry.normalize(client.provider()), client);
        }
        return clientMap;
    }

    private EmbeddingRoute resolveRoute(String modelId) {
        if (StringUtils.hasText(modelId)) {
            String provider = modelRegistry.resolveProvider(modelId);
            if (!StringUtils.hasText(provider)) {
                throw new IllegalArgumentException("Embedding model is not registered: " + modelId);
            }
            return new EmbeddingRoute(
                    new EmbeddingRoute.EmbeddingTarget(provider, modelId),
                    List.of());
        }

        String primaryProvider = resolvePrimaryProvider();
        String primaryModel = resolvePrimaryModel(primaryProvider);

        List<EmbeddingRoute.EmbeddingTarget> fallbacks = new ArrayList<>();
        if (Boolean.TRUE.equals(properties.getEmbedding().getFallbackEnabled())
                && !CollectionUtils.isEmpty(properties.getEmbedding().getFallbackOrder())) {
            for (String fallbackProvider : properties.getEmbedding().getFallbackOrder()) {
                String normalizedProvider = modelRegistry.normalize(fallbackProvider);
                if (!StringUtils.hasText(normalizedProvider) || normalizedProvider.equals(primaryProvider)) {
                    continue;
                }
                String fallbackModel = modelRegistry.getModel(normalizedProvider);
                if (StringUtils.hasText(fallbackModel)) {
                    fallbacks.add(new EmbeddingRoute.EmbeddingTarget(normalizedProvider, fallbackModel));
                }
            }
        }

        return new EmbeddingRoute(
                new EmbeddingRoute.EmbeddingTarget(primaryProvider, primaryModel),
                fallbacks
        );
    }

    private String resolvePrimaryProvider() {
        String defaultProvider = modelRegistry.normalize(properties.getEmbedding().getDefaultProvider());
        if (StringUtils.hasText(defaultProvider)) {
            return defaultProvider;
        }
        throw new IllegalStateException("Embedding defaultProvider is not configured");
    }

    private String resolvePrimaryModel(String provider) {
        String resolvedModel = modelRegistry.getModel(provider);
        if (StringUtils.hasText(resolvedModel)) {
            return resolvedModel;
        }
        throw new IllegalStateException("Embedding model is not configured for provider: " + provider);
    }

    private int resolveBatchSize() {
        Integer batchSize = properties.getEmbedding().getBatchSize();
        return batchSize == null || batchSize <= 0 ? 16 : batchSize;
    }
}
