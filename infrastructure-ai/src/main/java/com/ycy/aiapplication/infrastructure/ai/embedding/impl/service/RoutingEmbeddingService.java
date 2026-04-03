package com.ycy.aiapplication.infrastructure.ai.embedding.impl.service;

import com.ycy.aiapplication.infrastructure.ai.config.AIModelProperties;
import com.ycy.aiapplication.infrastructure.ai.embedding.EmbeddingClient;
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
 * 向量化路由服务。
 * <p>
 * 负责基于统一配置类 AIModelProperties 在百炼和 SiliconFlow 之间进行选择与降级。
 */
@Service
@RequiredArgsConstructor
public class RoutingEmbeddingService implements EmbeddingService {

    private final List<EmbeddingClient> embeddingClients;
    private final AIModelProperties properties;

    @Override
    public int dimension() {
        return properties.getEmbedding().getDimension() == null ? 0 : properties.getEmbedding().getDimension();
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
        List<String> routeOrder = resolveRouteOrder(modelId);
        RuntimeException lastException = null;

        for (String provider : routeOrder) {
            EmbeddingClient client = clientMap.get(normalizeProvider(provider));
            if (client == null) {
                continue;
            }
            try {
                return client.embedBatch(texts);
            } catch (RuntimeException ex) {
                lastException = ex;
                if (!Boolean.TRUE.equals(properties.getEmbedding().getFallbackEnabled())) {
                    throw ex;
                }
            }
        }

        if (lastException != null) {
            throw lastException;
        }
        throw new IllegalStateException("No available embedding client found");
    }

    /**
     * 构建 provider -> client 的映射。
     *
     * @return 客户端映射
     */
    private Map<String, EmbeddingClient> buildClientMap() {
        Map<String, EmbeddingClient> clientMap = new LinkedHashMap<>();
        for (EmbeddingClient client : embeddingClients) {
            clientMap.put(normalizeProvider(client.provider()), client);
        }
        return clientMap;
    }

    /**
     * 计算本次调用的路由顺序。
     * <p>
     * 规则：
     * 1. 如果传入了 modelId，则优先根据 modelId 反推 provider
     * 2. 否则使用 embedding.primary
     * 3. 如果允许降级，再拼接 fallbackOrder 中的其它 provider
     *
     * @param modelId 指定模型 ID
     * @return provider 顺序列表
     */
    private List<String> resolveRouteOrder(String modelId) {
        List<String> routeOrder = new ArrayList<>();

        String preferredProvider = resolveProviderByModelId(modelId);
        if (!StringUtils.hasText(preferredProvider)) {
            preferredProvider = properties.getEmbedding().getPrimary();
        }
        if (StringUtils.hasText(preferredProvider)) {
            routeOrder.add(normalizeProvider(preferredProvider));
        }

        if (Boolean.TRUE.equals(properties.getEmbedding().getFallbackEnabled())
                && !CollectionUtils.isEmpty(properties.getEmbedding().getFallbackOrder())) {
            for (String provider : properties.getEmbedding().getFallbackOrder()) {
                String normalized = normalizeProvider(provider);
                if (!routeOrder.contains(normalized)) {
                    routeOrder.add(normalized);
                }
            }
        }
        return routeOrder;
    }

    /**
     * 根据模型 ID 反推 provider。
     *
     * @param modelId 模型 ID
     * @return provider 标识
     */
    private String resolveProviderByModelId(String modelId) {
        if (!StringUtils.hasText(modelId)) {
            return null;
        }
        if (properties.getBaiLian() != null && modelId.equals(properties.getBaiLian().getEmbeddingModel())) {
            return "bailian";
        }
        if (properties.getSiliconFlow() != null && modelId.equals(properties.getSiliconFlow().getEmbeddingModel())) {
            return "siliconflow";
        }
        return null;
    }

    /**
     * 统一 provider 字符串格式。
     *
     * @param provider 原始 provider
     * @return 标准化 provider
     */
    private String normalizeProvider(String provider) {
        return provider == null ? null : provider.trim().toLowerCase();
    }
}
