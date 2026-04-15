package com.ycy.aiapplication.rag.control.request;

import lombok.Data;

import java.util.List;

/**
 * 更新意图节点请求。
 */
@Data
public class IntentNodeUpdateRequest {

    /**
     * 节点类型：0=知识库节点，1=系统节点。
     * 为空时表示不修改类型。
     */
    private Integer kind;

    /**
     * 关联知识库 ID，仅知识库节点需要。
     */
    private String kbId;

    /**
     * 节点名称。
     */
    private String name;

    /**
     * 节点描述。
     */
    private String description;

    /**
     * 示例问题列表。
     */
    private List<String> examples;

    /**
     * 短提示片段。
     */
    private String promptSnippet;

    /**
     * 完整 Prompt 模板。
     */
    private String promptTemplate;

    /**
     * 是否启用：1=启用，0=停用。
     */
    private Integer enabled;
}
