package com.ycy.aiapplication.infrastructure.ai.embedding;

import com.ycy.aiapplication.infrastructure.ai.config.AIModelProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Embedding 模型注册表。
 * 统一维护 provider 与 modelId 的映射关系，避免路由层硬编码。
 */
@Component
@RequiredArgsConstructor
public class EmbeddingModelRegistry {

    private final AIModelProperties properties;

    /**
     * 根据provider查询modelId
     */
    public String getModel(String provider) {
        if (!StringUtils.hasText(provider) || properties.getEmbedding().getModels() == null) {
            return null;
        }
        return properties.getEmbedding().getModels().get(normalize(provider));
    }

    /**
     * 反响解析：通过modelId找寻provider
     */
    public String resolveProvider(String modelId) {
        if (!StringUtils.hasText(modelId) || properties.getEmbedding().getModels() == null) {
            return null;
        }
        for (Map.Entry<String, String> entry : properties.getEmbedding().getModels().entrySet()) {
            if (modelId.equals(entry.getValue())) {
                return normalize(entry.getKey());
            }
        }
        return null;
    }

    /**
     * 兼容性判断
     */
    public boolean supports(String clientProvider, String provider, String modelId) {
        String normalizedClient = normalize(clientProvider);
        //配置了provider
        if (StringUtils.hasText(provider) && !normalizedClient.equals(normalize(provider))) {
            return false;
        }
        //没有指定modelId，clientProvider和provider相等，兼容
        if (!StringUtils.hasText(modelId)) {
            return true;
        }
        String resolvedProvider = resolveProvider(modelId);
        return normalizedClient.equals(normalize(resolvedProvider));
    }

    public Map<String, String> allModels() {
        Map<String, String> models = new LinkedHashMap<>();
        if (properties.getEmbedding().getModels() != null) {
            properties.getEmbedding().getModels()
                    .forEach((provider, model) -> models.put(normalize(provider), model));
        }
        return models;
    }

    public String normalize(String provider) {
        return provider == null ? null : provider.trim().toLowerCase();
    }
}
