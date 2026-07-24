package com.ycy.aiapplication.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * RAG意图识别配置
 * <p>
 * 包含路由分数阈值、保留节点数量
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "rag.intent")
public class RAGIntentProperties {

    /**
     * 第一层路由阈值，低于该值认为信号不足。
     */
    private Double firstLayerMinScore = 0.55D;

    /**
     * 第二层知识库节点最低分数阈值。
     */
    private Double knowledgeMinScore = 0.45D;

    /**
     * 第二层知识库节点允许进入全库检索的最低相关分数。
     * 低于该值说明问题超出当前知识库能力范围。
     */
    private Double knowledgeGlobalMinScore = 0.20D;

    /**
     * 第二层保留的最高分节点数量。
     */
    private Integer knowledgeTopN = 3;
}
