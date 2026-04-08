package com.ycy.aiapplication.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * rag功能重写功能相关配置
 * 对应ragent项目当中的RAGConfigProperties
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "rag.rewrite")
public class RAGReWriteProperties {
    /**
     * 查询重写功能开关
     * <p>
     * 控制是否启用查询重写功能，查询重写可以将用户的查询语句优化为更适合检索的形式
     * 默认值：{@code true}
     */
    private Boolean queryRewriteEnabled;

    /**
     * 改写时用于承接上下文的最大历史消息数
     */
    private Integer queryRewriteMaxHistoryMessages;

    /**
     * 改写时用于承接上下文的最大字符数
     */
    private Integer queryRewriteMaxHistoryChars;
}
