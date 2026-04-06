package com.ycy.aiapplication.infrastructure.ai.http;

import com.ycy.aiapplication.infrastructure.ai.config.AIModelProperties;
import com.ycy.aiapplication.infrastructure.ai.enums.ModelCapability;
import org.springframework.util.StringUtils;

/**
 * 模型 URL 解析工具。
 * <p>
 * 当前项目已经改为统一配置模型通道，不再使用 ragent 中的 ProviderConfig / ModelCandidate 结构。
 * 因此这里直接根据统一配置中的通道配置拼装完整请求地址。
 */
public final class ModelURLResolver {

    private ModelURLResolver() {
    }

    /**
     * 根据 SiliconFlow 通道配置解析完整请求地址。
     *
     * @param channel    SiliconFlow 通道配置
     * @param capability 模型能力
     * @return 完整请求 URL
     */
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

    /**
     * 根据百炼通道配置解析完整请求地址
     *
     * @param channel    通道配置
     * @param capability 模型能力
     * @return 完整请求url URL
     */
    public static String resolveBaiLianUrl(AIModelProperties.BaiLianProvider channel, ModelCapability capability) {
        if (channel == null || !StringUtils.hasText(channel.getBaseUrl())) {
            throw new IllegalStateException("BaiLian baseUrl is missing");
        }
        String path = switch (capability) {
            case EMBEDDING -> channel.getEmbeddingPath();
            default -> throw new IllegalStateException("Unsupported capability for BaiLian: " + capability);
        };
        if (!StringUtils.hasText(path)) {
            throw new IllegalStateException("BaiLian path is missing for capability: " + capability);
        }
        return joinUrl(channel.getBaseUrl(), path);
    }

    /**
     * 拼接 baseUrl 和 path，统一处理两端的斜杠。
     *
     * @param baseUrl 基础地址
     * @param path    路径
     * @return 完整 URL
     */
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
