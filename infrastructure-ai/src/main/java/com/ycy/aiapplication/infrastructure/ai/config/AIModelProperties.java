package com.ycy.aiapplication.infrastructure.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 通用 AI 模型调用配置。
 * <p>
 * 当前仅保留百炼 SDK 与 SiliconFlow HTTP 两条调用通道，
 * 用于聊天与向量化链路，不再维护 provider/candidate 这类平台化配置结构。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "ai.properties")
public class AIModelProperties {

    /**
     * 聊天链路配置。
     */
    private Chat chat = new Chat();

    /**
     * 向量化链路配置。
     */
    private Embedding embedding = new Embedding();

    /**
     * 百炼 SDK 通道配置。
     */
    private BaiLian baiLian = new BaiLian();

    /**
     * SiliconFlow HTTP 通道配置。
     */
    private SiliconFlow siliconFlow = new SiliconFlow();

    /**
     * HTTP 公共调用配置。
     */
    private Http http = new Http();

    @Data
    public static class Chat {

        /**
         * 主通道，可选 baiLian / siliconFlow。
         */
        private String primary = "baiLian";

        /**
         * 是否开启失败降级。
         */
        private Boolean fallbackEnabled = true;

        /**
         * 聊天链路降级顺序。
         */
        private List<String> fallbackOrder = List.of("baiLian", "siliconFlow");
    }

    @Data
    public static class Embedding {

        /**
         * 主通道，可选 baiLian / siliconFlow。
         */
        private String primary = "baiLian";

        /**
         * 是否开启失败降级。
         */
        private Boolean fallbackEnabled = true;

        /**
         * 向量维度。两条通道应保持一致。
         */
        private Integer dimension = 1024;

        /**
         * 批量向量化单次请求数。
         */
        private Integer batchSize = 16;

        /**
         * 向量化链路降级顺序。
         */
        private List<String> fallbackOrder = List.of("baiLian", "siliconFlow");
    }

    @Data
    public static class Channel {

        /**
         * 是否启用当前通道。
         */
        private Boolean enabled = true;

        /**
         * 访问密钥。
         */
        private String apiKey;

        /**
         * 聊天模型名称。
         */
        private String chatModel;

        /**
         * 向量化模型名称。
         */
        private String embeddingModel;
    }

    @Data
    public static class BaiLian extends Channel {

        /**
         * 百炼兼容模式或服务接入地址。
         */
        private String baseUrl;

        /**
         * 可选工作空间标识。
         */
        private String workspaceId;
    }

    @Data
    public static class SiliconFlow extends Channel {

        /**
         * SiliconFlow 服务根地址。
         */
        private String baseUrl;

        /**
         * 聊天接口路径。
         */
        private String chatPath = "/v1/chat/completions";

        /**
         * 向量化接口路径。
         */
        private String embeddingPath = "/v1/embeddings";
    }

    @Data
    public static class Http {

        /**
         * 连接超时时间，单位毫秒。
         */
        private Long connectTimeoutMs = 3000L;

        /**
         * 读取超时时间，单位毫秒。
         */
        private Long readTimeoutMs = 10000L;

        /**
         * 写入超时时间，单位毫秒。
         */
        private Long writeTimeoutMs = 10000L;
    }
}
