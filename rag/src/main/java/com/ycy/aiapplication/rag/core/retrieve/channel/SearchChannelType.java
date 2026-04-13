package com.ycy.aiapplication.rag.core.retrieve.channel;

/**
 * 检索通道类型枚举。
 * <p>
 * 用于标识当前结果来自哪类检索策略，便于后续统计、监控与扩展处理。
 */
public enum SearchChannelType {

    /**
     * 向量全局检索。
     */
    VECTOR_GLOBAL,

    /**
     * 意图定向检索。
     */
    INTENT_DIRECTED,

    /**
     * Elasticsearch 关键词检索。
     */
    KEYWORD_ES,

    /**
     * 混合检索。
     */
    HYBRID
}
