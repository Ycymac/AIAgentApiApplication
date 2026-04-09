package com.ycy.aiapplication.rag.core.intent.classifier;

import com.ycy.aiapplication.rag.core.intent.common.NodeScore;

import java.util.List;

/**
 * 意图分类器统一接口。
 * 作用：
 * 1. 统一第一层粗分类器和第二层知识库分类器的输出结构。
 * 2. 约束所有分类器最终都返回 {@link NodeScore} 列表。
 */
public interface IntentClassifier {

    /**
     * 执行意图分类。
     *
     * @param question 当前待识别的问题
     * @return 节点得分列表
     */
    List<NodeScore> classifyTargets(String question);

    /**
     * 执行分类后，按分数阈值和 TopN 裁剪结果。
     *
     * @param question 当前待识别的问题
     * @param topN     最大保留结果数
     * @param minScore 最低分值阈值
     * @return 过滤后的节点得分列表
     */
    default List<NodeScore> topKAboveThreshold(String question, int topN, double minScore) {
        return classifyTargets(question).stream()
                .filter(each -> each.getScore() >= minScore)
                .limit(topN)
                .toList();
    }
}
