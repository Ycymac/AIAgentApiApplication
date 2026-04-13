package com.ycy.aiapplication.rag.core.retrieve.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 向量检索请求参数。
 * <p>
 * 整体职责：
 * 1. 统一封装底层向量检索服务的输入参数。
 * 2. 支持基础 query + topK 检索，也支持显式指定 collection。
 * 3. 为后续扩展 metadata 过滤、表达式过滤等能力预留字段。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RetrieveRequest {

    /**
     * 用户自然语言问题 / 查询语句。
     */
    private String query;

    /**
     * 返回 TopK，默认 5。
     */
    @Builder.Default
    private int topK = 5;

    /**
     * 目标向量集合名称。
     * <p>
     * 说明：
     * 1. 为空时走默认 collection。
     * 2. 非空时按指定 collection 执行检索。
     */
    private String collectionName;

    /**
     * 元数据过滤条件，作为后续扩展字段预留。
     * <p>
     * key 为 metadata 字段名，value 为期望匹配值。
     */
    private Map<String, Object> metadataFilters;
}
