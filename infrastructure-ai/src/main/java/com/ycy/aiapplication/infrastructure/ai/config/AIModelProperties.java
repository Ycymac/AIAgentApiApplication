package com.ycy.aiapplication.infrastructure.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一的AI配置类
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "ai")
public class AIModelProperties {

    private Providers providers = new Providers();

    private Embedding embedding = new Embedding();

    private Rerank rerank = new Rerank();

    private Chat chat = new Chat();

    private Http http = new Http();

    private Selection selection = new Selection();

    private Stream stream = new Stream();

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

        private String chatPath = "/chat/completions";

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

        private Map<String, String> models = defaultEmbeddingModels();

        private static Map<String, String> defaultEmbeddingModels() {
            Map<String, String> models = new LinkedHashMap<>();
            models.put("bailian", "text-embedding-v4");
            models.put("siliconflow", "BAAI/bge-m3");
            return models;
        }
    }

    /**
     * 双平台聊天配置
     * 使用平台内降级策略
     * 每个平台有两种主流聊天模型
     * 同时有一个稳定版本的兜底模型用于出现模型不可用时进行兜底
     */
    @Data
    public static class Chat {
        private String defaultProvider = "bailian";

        private String defaultModel = "qwen3.6-plus";
        //百炼平台内降级
        private Boolean bailianFallbackEnabled = true;

        private Boolean siliconFlowFallbackEnabled = true;

        private String bailianBackUpModel = "qwen-plus";

        private String siliconFlowBackUpModel = "deepseek-ai/DeepSeek-V3.2";

        private Map<String, String> models = defaultChatModels();

        private static Map<String, String> defaultChatModels() {
            Map<String, String> models = new LinkedHashMap<>();
            //可供用于选择的模型
            models.put("bailian_1", "qwen3.6-plus");
            models.put("bailian_2", "qwen3.5-plus");
            models.put("siliconflow_1", "Pro/zai-org/GLM-5");
            models.put("siliconflow_2", "Pro/deepseek-ai/DeepSeek-V3.2");
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

    @Data
    public static class Selection {

        private Integer failureThreshold = 2;

        private Long openDurationMs = 30000L;
    }

    @Data
    public static class Stream {

        private Integer firstPacketTimeoutSeconds = 60;

        private Integer executorCoreSize = 2;

        private Integer executorMaxSize = 4;

        private Integer executorQueueCapacity = 128;
    }
}
