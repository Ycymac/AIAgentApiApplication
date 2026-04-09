package com.ycy.aiapplication.rag.core.intent.common;

import com.ycy.aiapplication.rag.core.intent.FirstLayerIntentDecision;
import com.ycy.aiapplication.rag.core.intent.common.enums.IntentKind;
import lombok.Builder;

import java.util.List;

/**
 * 单个子问题的意图识别结果。
 * 作用：
 * 1. 记录子问题文本、最终路由类型和节点命中信息。
 * 2. 作为三层意图识别完成后的标准输出对象。
 */
@Builder
public record SubQuestionIntent(
        String subQuestion,
        IntentKind routeKind,
        boolean globalKbFallback,
        FirstLayerIntentDecision firstLayerDecision,
        List<NodeScore> nodeScores
) {
}
