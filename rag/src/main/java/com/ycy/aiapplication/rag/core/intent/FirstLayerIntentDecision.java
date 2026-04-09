package com.ycy.aiapplication.rag.core.intent;

import com.ycy.aiapplication.rag.core.intent.common.NodeScore;
import lombok.Builder;

import java.util.List;

/**
 * 第一层粗分类决策结果。
 * 作用：
 * 1. 封装 RAG 分和 SYSTEM 分。
 * 2. 记录当前问题是否应该继续进入第二层知识库节点识别。
 */
@Builder
public record FirstLayerIntentDecision(
        double ragScore,
        double systemScore,
        boolean shouldRetrieveKnowledgeBase,
        List<NodeScore> nodeScores
) {
}
