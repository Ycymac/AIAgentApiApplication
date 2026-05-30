package com.ycy.aiapplication.infrastructure.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 模型统一配置。
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

        private Map<String, String> models;
    }

    /**
     * 聊天模型配置。
     * <p>
     * models 是用户可选模型；backupModels 是系统隐藏兜底模型。
     */
    @Data
    public static class Chat {
        private String defaultProvider = "bailian";

        private String defaultModel = "qwen3.6-plus";

        private Map<String, String> models = defaultChatModels();

        private List<BackupModel> backupModels = defaultBackupModels();

        private static Map<String, String> defaultChatModels() {
            Map<String, String> models = new LinkedHashMap<>();
            models.put("bailian_1", "qwen3.6-plus");
            models.put("bailian_2", "qwen3.5-plus");
            models.put("siliconflow_1", "deepseek-ai/DeepSeek-V4-Flash");
            models.put("siliconflow_2", "Pro/deepseek-ai/DeepSeek-V3.2");
            return models;
        }

        private static List<BackupModel> defaultBackupModels() {
            return List.of(
                    new BackupModel("bailian_backup", "bailian", "qwen-plus"),
                    new BackupModel("siliconflow_backup", "siliconflow", "deepseek-ai/DeepSeek-V3.2")
            );
        }

        @Data
        public static class BackupModel {
            private String id;

            private String provider;

            private String model;

            public BackupModel() {
            }

            public BackupModel(String id, String provider, String model) {
                this.id = id;
                this.provider = provider;
                this.model = model;
            }
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

        private Long connectTimeoutMs = 2500L;

        private Long readTimeoutMs = 10000L;

        private Long writeTimeoutMs = 10000L;
    }

    @Data
    public static class Selection {

        private Integer failureThreshold = 1;

        private Long openDurationMs = 30000L;
    }

    @Data
    public static class Stream {

        private Integer firstPacketTimeoutSeconds = 60;

        private Integer executorCoreSize = 2;

        private Integer executorMaxSize = 4;

        private Integer executorQueueCapacity = 128;

        private Integer messageChunkSize = 5;
    }
}
