package com.ycy.aiapplication.rag.core.intent.common;

import java.util.List;

/**
 * 多子问题知识库命中结果的聚合对象。
 * 作用：
 * 1. 合并多个子问题命中的 KB 节点。
 * 2. 供后续检索和 Prompt 规划阶段消费。
 */
public record IntentGroup(List<NodeScore> kbIntents) {
}
