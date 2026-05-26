package com.ycy.aiapplication.rag.core.intent.common;

import com.ycy.aiapplication.rag.core.intent.common.enums.IntentKind;
import com.ycy.aiapplication.rag.core.intent.common.enums.IntentLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 意图节点领域对象。
 * 作用：
 * 1. 统一描述知识库节点、系统节点等识别目标的结构。
 * 2. 作为分类器、缓存层、解析器之间传递的核心数据模型。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntentNode {

    /**
     * 节点唯一标识。
     */
    private String id;

    /**
     * 关联的知识库 ID。
     */
    private String kbId;

    /**
     * 节点名称。
     */
    private String name;

    /**
     * 节点语义描述。
     */
    private String description;

    /**
     * 节点层级。
     */
    private IntentLevel level;

    /**
     * 示例问题列表。
     */
    @Builder.Default
    private List<String> examples = new ArrayList<>();

    /**
     * 节点全路径，主要用于日志和排查。
     */
    @Builder.Default
    private String fullPath = "";

    /**
     * 节点类型。
     */
    @Builder.Default
    private IntentKind kind = IntentKind.KB;

    /**
     * 关联的向量集合名称。
     */
    private String collectionName;

    /**
     * 节点级检索 TopK。
     */
    private Integer topK;

    /**
     * 短提示片段。
     */
    private String promptSnippet;

    /**
     * 完整 Prompt 模板。
     */
    private String promptTemplate;

    /**
     * 判断当前节点是否属于知识库节点。
     *
     * @return true 表示当前节点是 KB 节点
     */
    public boolean isKB() {
        return kind == null || kind == IntentKind.KB;
    }

    /**
     * 判断当前节点是否属于系统问答节点。
     *
     * @return true 表示当前节点是 SYSTEM 节点
     */
    public boolean isSystem() {
        return kind == IntentKind.SYSTEM;
    }
}
