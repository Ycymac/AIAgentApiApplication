package com.ycy.aiapplication.infrastructure.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Unified AI provider and capability configuration.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "ai")
public class AIModelProperties {

    private Providers providers = new Providers();

    private Embedding embedding = new Embedding();

    private Rerank rerank = new Rerank();

    private Http http = new Http();

    @Data
    public static class Providers {

        private BaiLianProvider bailian = new BaiLianProvider();

        private SiliconFlowProvider siliconflow = new SiliconFlowProvider();
    }

    @Data
    public static class Provider {

        private Boolean enabled = true;

        private String apiKey;
    }

    @Data
    public static class BaiLianProvider extends Provider {

        private String baseUrl = "https://dashscope.aliyuncs.com/api/v1";

        private String embeddingPath = "/services/embeddings/text-embedding/text-embedding";

        private String rerankPath = "/services/rerank/text-rerank/text-rerank";
    }

    @Data
    public static class SiliconFlowProvider extends Provider {

        private String baseUrl = "https://api.siliconflow.cn";

        private String chatPath = "/v1/chat/completions";

        private String embeddingPath = "/v1/embeddings";
    }

    @Data
    public static class Embedding {

        private String defaultProvider = "bailian";

        private Boolean fallbackEnabled = true;

        private List<String> fallbackOrder = List.of("siliconflow");

        private Integer batchSize = 16;

        private Integer dimension = 1024;

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
         * Current rerank provider. The project only configures BaiLian here.
         */
        private String provider = "bailian";

        /**
         * Primary model used for the first attempt.
         */
        private String primaryModel = "qwen3-vl-rerank";

        /**
         * Backup models used inside the same provider when the primary call fails.
         */
        private List<String> backupModels = List.of("qwen3-rerank");
    }

    @Data
    public static class Http {

        private Long connectTimeoutMs = 3000L;

        private Long readTimeoutMs = 10000L;

        private Long writeTimeoutMs = 10000L;
    }
}
