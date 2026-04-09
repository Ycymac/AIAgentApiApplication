package com.ycy.aiapplication.rag.core.intent;

import cn.hutool.core.collection.CollUtil;
import com.ycy.aiapplication.rag.config.RAGIntentProperties;
import com.ycy.aiapplication.rag.core.intent.classifier.impl.SecondLayerIntentClassifier;
import com.ycy.aiapplication.rag.core.intent.classifier.impl.FirstLayerIntentClassifier;
import com.ycy.aiapplication.rag.core.intent.common.IntentGroup;
import com.ycy.aiapplication.rag.core.intent.common.IntentNode;
import com.ycy.aiapplication.rag.core.intent.common.NodeScore;
import com.ycy.aiapplication.rag.core.intent.common.SubQuestionIntent;
import com.ycy.aiapplication.rag.core.intent.common.enums.IntentKind;
import com.ycy.aiapplication.rag.core.rewrite.common.RewriteResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 三层意图识别总编排器。
 * 作用：
 * 1. 串联第一层粗分类、第二层知识库打分和第三层最终路由决策。
 * 2. 将改写后的主问题或子问题转换为可执行的检索路由结果。
 * 3. 统一处理 KB 命中、全局检索兜底、SYSTEM 路由和 UNKNOWN 路由。
 * 整体执行顺序：
 * 1. {@link #resolve(RewriteResult)} 从改写结果中取出子问题。
 * 2. 对每个子问题调用 {@link #resolveSingleQuestion(String)}。
 * 3. 在单题处理中，先做第一层粗分类，再视情况进入第二层知识库节点打分。
 * 4. 最后根据规则输出 KB / SYSTEM / UNKNOWN，并决定是否启用全局检索兜底。
 */
@Service
@RequiredArgsConstructor
public class IntentResolver {

    private final FirstLayerIntentClassifier firstLayerIntentClassifier;
    private final SecondLayerIntentClassifier secondLayerIntentClassifier;
    private final RAGIntentProperties intentProperties;

    /**
     * 对改写结果中的所有问题执行三层意图识别。
     *<p>
     * @param rewriteResult 查询改写结果，包含改写后的问题和拆分出的子问题列表
     * @return 每个子问题各自对应的最终路由结果
     */
    public List<SubQuestionIntent> resolve(RewriteResult rewriteResult) {
        //将拆分后的问题进行三层意图识别
        List<String> subQuestions = CollUtil.isNotEmpty(rewriteResult.subQuestions())
                ? rewriteResult.subQuestions()
                : List.of(rewriteResult.rewrittenQuestion());
        return subQuestions.stream()
                .map(this::resolveSingleQuestion)
                .toList();
    }

    /**
     * 聚合多个子问题中的知识库命中节点。
     *<p>
     * @param subIntents 子问题路由结果列表
     * @return 聚合后的知识库节点集合
     */
    public IntentGroup mergeIntentGroup(List<SubQuestionIntent> subIntents) {
        List<NodeScore> kbIntents = new ArrayList<>();
        for (SubQuestionIntent each : subIntents) {
            if (each.routeKind() == IntentKind.KB && CollUtil.isNotEmpty(each.nodeScores())) {
                kbIntents.addAll(each.nodeScores());
            }
        }
        return new IntentGroup(kbIntents);
    }

    /**
     * 判断当前结果是否是纯 SYSTEM 路由。
     *<p>
     * @param nodeScores 当前问题对应的节点得分列表
     * @return true 表示当前问题只走系统问答
     */
    public boolean isSystemOnly(List<NodeScore> nodeScores) {
        return CollUtil.isNotEmpty(nodeScores)
                && nodeScores.size() == 1
                && nodeScores.get(0).getNode() != null
                && nodeScores.get(0).getNode().isSystem();
    }

    /**
     * 对单个问题执行三层路由决策。
     *
     * @param question 单个子问题文本
     * @return 子问题的最终意图路由结果
     */
    private SubQuestionIntent resolveSingleQuestion(String question) {
        FirstLayerIntentDecision firstLayerDecision = firstLayerIntentClassifier.decide(question);
        List<NodeScore> kbScores = List.of();
        if (firstLayerDecision.shouldRetrieveKnowledgeBase()) {
            // 第一层判定值得检索时，才进入第二层知识库节点打分。
            kbScores = secondLayerIntentClassifier.topKAboveThreshold(
                    question,
                    defaultTopN(intentProperties.getKnowledgeTopN()),
                    defaultMinScore(intentProperties.getKnowledgeMinScore())
            );
        }

        double ragScore = firstLayerDecision.ragScore();
        double systemScore = firstLayerDecision.systemScore();
        double firstLayerMinScore = defaultMinScore(intentProperties.getFirstLayerMinScore());

        if (CollUtil.isNotEmpty(kbScores)) {
            // 第一层偏向 RAG 且第二层已有知识库节点过阈值，直接走指定 KB 检索。
            return SubQuestionIntent.builder()
                    .subQuestion(question)
                    .routeKind(IntentKind.KB)
                    .globalKbFallback(false)
                    .firstLayerDecision(firstLayerDecision)
                    .nodeScores(kbScores)
                    .build();
        }
        if (ragScore >= firstLayerMinScore) {
            // 第一层偏向 RAG，但第二层没有任何节点过阈值，此时走全局检索兜底。
            return SubQuestionIntent.builder()
                    .subQuestion(question)
                    .routeKind(IntentKind.KB)
                    .globalKbFallback(true)
                    .firstLayerDecision(firstLayerDecision)
                    .nodeScores(List.of())
                    .build();
        }
        if (systemScore >= firstLayerMinScore) {
            // SYSTEM 分足够高，且没有知识库节点命中，则直接走系统问答。
            return SubQuestionIntent.builder()
                    .subQuestion(question)
                    .routeKind(IntentKind.SYSTEM)
                    .globalKbFallback(false)
                    .firstLayerDecision(firstLayerDecision)
                    .nodeScores(List.of(NodeScore.builder()
                            .node(IntentNode.builder()
                                    .id("SYSTEM")
                                    .name("SYSTEM")
                                    .kind(IntentKind.SYSTEM)
                                    .build())
                            .score(systemScore)
                            .build()))
                    .build();
        }

        // 两层信号都很弱，且没有命中知识库列表，则标记为 UNKNOWN，交给后续通用能力兜底。
        return SubQuestionIntent.builder()
                .subQuestion(question)
                .routeKind(IntentKind.UNKNOWN)
                .globalKbFallback(false)
                .firstLayerDecision(firstLayerDecision)
                .nodeScores(List.of())
                .build();
    }

    /**
     * 获取 TopN 配置的可用值。
     *
     * @param value 配置中的 TopN
     * @return 合法 TopN，非法时默认 3
     */
    private int defaultTopN(Integer value) {
        return value == null || value <= 0 ? 3 : value;
    }

    /**
     * 获取分值阈值配置的可用值。
     *
     * @param value 配置中的阈值
     * @return 合法阈值，未配置时默认 0.45
     */
    private double defaultMinScore(Double value) {
        return value == null ? 0.45D : value;
    }
}
