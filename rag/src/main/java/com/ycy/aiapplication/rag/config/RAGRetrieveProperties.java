package com.ycy.aiapplication.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 *RAG检索配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "rag.retrieve")
public class RAGRetrieveProperties {
    /**
     * 向量严格清洗开关
     */
    private Boolean strictDataBaseFilterUse;
    /**
     * 检索通道配置
     */
    private Channels channels = new Channels();

    @Data
    public static class Channels {

        /**
         * 向量全局检索配置
         */
        private VectorGlobal vectorGlobal = new VectorGlobal();

        /**
         * 意图定向检索配置
         */
        private IntentDirected intentDirected = new IntentDirected();
    }

    @Data
    public static class VectorGlobal {

        /**
         * 是否启用
         */
        private boolean enabled = true;

        /**
         * TopK 倍数
         * 全局检索时召回更多候选，后续通过 Rerank 筛选
         */
        private int topKMultiplier = 3;
    }

    @Data
    public static class IntentDirected {

        /**
         * 是否启用
         */
        private boolean enabled = true;

        /**
         * TopK 倍数
         */
        private int topKMultiplier = 2;
    }

    public boolean getStrictDBFilterUse(){
        if(strictDataBaseFilterUse==null)return false;
        else return strictDataBaseFilterUse;
    }

}
