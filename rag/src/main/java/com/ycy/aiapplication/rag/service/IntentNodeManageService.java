package com.ycy.aiapplication.rag.service;

import com.ycy.aiapplication.rag.core.intent.common.IntentNode;

import java.util.List;

/**
 * 知识库意图节点管理服务接口。
 * 作用：
 * 1. 对外暴露意图节点装载能力。
 * 2. 封装知识库节点同步与缓存刷新能力。
 */
public interface IntentNodeManageService {

    /**
     * 查询当前启用的知识库意图节点。
     *
     * @return 可参与第二层意图识别的知识库节点列表
     */
    List<IntentNode> listEnabledIntentNodes();

    /**
     * 主动刷新知识库意图节点缓存。
     */
    void refreshIntentNodeCache();
}
