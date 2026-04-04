package com.ycy.aiapplication.knowledge.control.request.base;

import lombok.Data;

/**
 * 知识库创建请求
 */
@Data
public class KnowledgeBaseCreateRequest {
    /**
     * 知识库名称
     */
    private String name;

    /**
     * 嵌入模型，如 qwen3-embedding:8b-fp16
     */
    private String embeddingModel;


}
