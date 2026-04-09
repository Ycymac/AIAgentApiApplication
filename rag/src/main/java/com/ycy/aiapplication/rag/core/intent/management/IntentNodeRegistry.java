package com.ycy.aiapplication.rag.core.intent.management;

import com.ycy.aiapplication.rag.core.intent.common.IntentNode;

import java.util.List;

/**
 * 意图节点注册表接口。
 * 作用：
 * 1. 为分类器或其他组件提供按 ID 查询节点的能力。
 * 2. 提供当前知识库节点列表的统一访问入口。
 */
public interface IntentNodeRegistry {

    /**
     * 根据节点 ID 查询单个知识库节点。
     *
     * @param id 节点 ID
     * @return 命中的节点；未命中时返回 null
     */
    IntentNode getNodeById(String id);

    /**
     * 获取当前全部知识库意图节点。
     *
     * @return 知识库节点列表
     */
    List<IntentNode> listKnowledgeNodes();
}
