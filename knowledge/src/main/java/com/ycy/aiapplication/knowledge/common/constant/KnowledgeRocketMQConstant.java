package com.ycy.aiapplication.knowledge.common.constant;

/**
 * 为后续使用消息队列解耦文件上传和分块做铺垫
 * 当前先实现串行执行
 */
public class KnowledgeRocketMQConstant {
    public static final String KNOWLEDGE_DOCUMENT_CHUNK_TOPIC_KEY = "AI-knowledge-document-chunk-topic";
    public static final String KNOWLEDGE_DOCUMENT_CHUNK_CONSUMER_GROUP_KEY = "AI-knowledge-document-chunk-consumer-group";
}
