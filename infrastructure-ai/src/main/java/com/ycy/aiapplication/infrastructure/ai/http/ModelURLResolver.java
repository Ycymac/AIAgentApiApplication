package com.ycy.aiapplication.infrastructure.ai.http;

import com.ycy.aiapplication.infrastructure.ai.config.AIModelProperties;
import com.ycy.aiapplication.infrastructure.ai.enums.ModelCapability;
import org.springframework.util.StringUtils;

/**
 * Resolves provider endpoint URLs from unified configuration.
 */
public final class ModelURLResolver {

    private ModelURLResolver() {
    }

    public static String resolveSiliconFlowUrl(AIModelProperties.SiliconFlowProvider channel, ModelCapability capability) {
        if (channel == null || !StringUtils.hasText(channel.getBaseUrl())) {
            throw new IllegalStateException("SiliconFlow baseUrl is missing");
        }
        String path = switch (capability) {
            case CHAT -> channel.getChatPath();
            case EMBEDDING -> channel.getEmbeddingPath();
            default -> throw new IllegalStateException("Unsupported capability for SiliconFlow: " + capability);
        };
        if (!StringUtils.hasText(path)) {
            throw new IllegalStateException("SiliconFlow path is missing for capability: " + capability);
        }
        return joinUrl(channel.getBaseUrl(), path);
    }

    public static String resolveBaiLianUrl(AIModelProperties.BaiLianProvider channel, ModelCapability capability) {
        if (channel == null || !StringUtils.hasText(channel.getBaseUrl())) {
            throw new IllegalStateException("BaiLian baseUrl is missing");
        }
        String path = switch (capability) {
            case CHAT -> channel.getChatPath();
            case EMBEDDING -> channel.getEmbeddingPath();
            case RERANK -> channel.getRerankPath();
        };
        if (!StringUtils.hasText(path)) {
            throw new IllegalStateException("BaiLian path is missing for capability: " + capability);
        }
        return joinUrl(channel.getBaseUrl(), path);
    }

    private static String joinUrl(String baseUrl, String path) {
        if (baseUrl.endsWith("/") && path.startsWith("/")) {
            return baseUrl + path.substring(1);
        }
        if (!baseUrl.endsWith("/") && !path.startsWith("/")) {
            return baseUrl + "/" + path;
        }
        return baseUrl + path;
    }
}
