package com.ycy.aiapplication.infrastructure.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 平台与能力配置。
 * 平台连接信息与 embedding 路由策略分离，避免 provider 配置和能力选择耦合。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "ai")
public class AIModelProperties {

    /**
     * 各平台连接配置。
     */
    private Providers providers = new Providers();

    /**
     * Embedding 能力层配置。
     */
    private Embedding embedding = new Embedding();

    /**
     * Rerank能力配置
     */
    private Rerank rerank = new Rerank();

    /**
     * HTTP 公共配置。
     */
    private Http http = new Http();

    @Data
    public static class Providers {

        private BaiLianProvider bailian = new BaiLianProvider();

        private SiliconFlowProvider siliconflow = new SiliconFlowProvider();
    }

    @Data
    public static class Provider {

        /**
         * 是否启用当前平台。
         */
        private Boolean enabled = true;

        /**
         * 平台 API Key。
         */
        private String apiKey;
    }

    @Data
    public static class BaiLianProvider extends Provider {

        /**
         * 百炼 HTTP 根地址。
         */
        private String baseUrl = "https://dashscope.aliyuncs.com/api/v1";

        /**
         * 百炼 embedding 接口路径。
         */
        private String embeddingPath = "/services/embeddings/text-embedding/text-embedding";

        /**
         * 百炼rerank接口路径
         */
        private String rerankPath = "/services/rerank/text-rerank/text-rerank";


    }

    @Data
    public static class SiliconFlowProvider extends Provider {

        /**
         * SiliconFlow HTTP 根地址。
         */
        private String baseUrl = "https://api.siliconflow.cn";

        /**
         * SiliconFlow chat 接口路径。
         */
        private String chatPath = "/v1/chat/completions";

        /**
         * SiliconFlow embedding 接口路径。
         */
        private String embeddingPath = "/v1/embeddings";
    }

    @Data
    public static class Embedding {

        /**
         * 默认 provider。
         */
        private String defaultProvider = "bailian";

        /**
         * 是否开启失败降级。
         */
        private Boolean fallbackEnabled = true;

        /**
         * provider 降级顺序。
         */
        private List<String> fallbackOrder = List.of("siliconflow");

        /**
         * 统一批量大小。
         */
        private Integer batchSize = 16;

        /**
         * 统一向量维度。
         */
        private Integer dimension = 1024;

        /**
         * provider -> modelId 映射。
         */
        private Map<String, String> models = defaultModels();

        private static Map<String, String> defaultModels() {
            Map<String, String> models = new LinkedHashMap<>();
            models.put("bailian", "text-embedding-v4");
            models.put("siliconflow", "BAAI/bge-m3");
            return models;
        }
    }

    @Data
    public static class Rerank {

        /**
         * 默认 provider。
         */
        private String defaultProvider = "bailian1";

        /**
         * provider 降级顺序。
         */
        private List<String> fallbackOrder = List.of("bailian2");

        /**
         * provider -> modelId 映射。
         */
        private Map<String, String> models = defaultModels();

        private static Map<String, String> defaultModels() {
            Map<String, String> models = new LinkedHashMap<>();
            models.put("bailian1", "qwen3-vl-rerank");
            models.put("bailian2", "qwen3-rerank");
            return models;
        }

    }

    @Data
    public static class Http {

        private Long connectTimeoutMs = 3000L;

        private Long readTimeoutMs = 10000L;

        private Long writeTimeoutMs = 10000L;
    }
}
