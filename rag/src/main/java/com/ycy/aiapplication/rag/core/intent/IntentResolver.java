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

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
     * 聚合多个子问题中命中的知识库节点。
     * <p>
     * 说明：
     * 1. 只聚合最终被判定为 KB 路由的节点结果。
     * 2. 同一节点若在多个子问题中重复命中，则保留最高分那一条。
     *
     * @param subIntents 子问题意图列表。
     * @return 去重并按分数降序排列后的知识库节点集合。
     */
    public IntentGroup mergeIntentGroup(List<SubQuestionIntent> subIntents) {
        Map<String, NodeScore> kbIntentMap = new LinkedHashMap<>();
        for (SubQuestionIntent each : subIntents) {
            if (each.routeKind() == IntentKind.KB && CollUtil.isNotEmpty(each.nodeScores())) {
                for (NodeScore nodeScore : each.nodeScores()) {
                    if (nodeScore.getNode() == null || nodeScore.getNode().getId() == null) {
                        continue;
                    }
                    // 同一节点在多个子问题中可能重复出现，这里按最高分保留，便于后续 Prompt 规划。
                    kbIntentMap.merge(nodeScore.getNode().getId(), nodeScore,
                            (left, right) -> left.getScore() >= right.getScore() ? left : right);
                }
            }
        }
        List<NodeScore> kbIntents = kbIntentMap.values().stream()
                .sorted(Comparator.comparingDouble(NodeScore::getScore).reversed())
                .toList();
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
     * <p>
     * 决策顺序：
     * 1. 第一层判断问题是否需要进入知识库相关性识别；
     * 2. 第二层保留原始分数，并按定向阈值筛选具体知识库；
     * 3. 未达到定向阈值时，仅允许可信弱相关问题进入全库检索；
     * 4. 完全无关的问题转入 SYSTEM，避免低分问题无条件扫描全部知识库。
     *
     * @param question 单个子问题文本
     * @return 子问题的最终意图路由结果
     */
    private SubQuestionIntent resolveSingleQuestion(String question) {
        // 第一层只决定是否进入二层，不直接授予知识库检索权限。
        FirstLayerIntentDecision firstLayerDecision = firstLayerIntentClassifier.decide(question);
        List<NodeScore> rawKbScores = List.of();
        if (firstLayerDecision.shouldRetrieveKnowledgeBase()) {
            // 必须保留二层原始分数，避免把弱相关与完全无关都折叠成“无定向节点”。
            rawKbScores = secondLayerIntentClassifier.classifyTargets(question);
        }

        double ragScore = firstLayerDecision.ragScore();
        double systemScore = firstLayerDecision.systemScore();
        double firstLayerMinScore = defaultMinScore(intentProperties.getFirstLayerMinScore());
        double directedMinScore = defaultMinScore(intentProperties.getKnowledgeMinScore());
        double globalMinScore = defaultGlobalMinScore(intentProperties.getKnowledgeGlobalMinScore());

        // 第一档：达到定向阈值的节点按分数顺序保留 TopN，交给定向检索通道。
        List<NodeScore> directedKbScores = rawKbScores.stream()
                .filter(each -> each.getScore() >= directedMinScore)
                .limit(defaultTopN(intentProperties.getKnowledgeTopN()))
                .toList();

        if (CollUtil.isNotEmpty(directedKbScores)) {
            // 第一层偏向 RAG 且第二层已有知识库节点过阈值，直接走指定 KB 检索。
            return SubQuestionIntent.builder()
                    .subQuestion(question)
                    .routeKind(IntentKind.KB)
                    .globalKbFallback(false)
                    .firstLayerDecision(firstLayerDecision)
                    .nodeScores(directedKbScores)
                    .build();
        }

        // 第二档只关心最高相关分，判断是否至少存在一个可信的弱相关知识库信号。
        double highestKbScore = rawKbScores.stream()
                .mapToDouble(NodeScore::getScore)
                .max()
                .orElse(0D);
        if (ragScore >= firstLayerMinScore && highestKbScore >= globalMinScore) {
            // 二层存在可信的弱相关信号，但不足以定向到单个知识库，才允许全库检索。
            return SubQuestionIntent.builder()
                    .subQuestion(question)
                    .routeKind(IntentKind.KB)
                    .globalKbFallback(true)
                    .firstLayerDecision(firstLayerDecision)
                    .nodeScores(List.of())
                    .build();
        }

        // 第三档：技术问题与现有知识库无关时也走 SYSTEM，由系统提示词说明能力边界。
        if (ragScore >= firstLayerMinScore || systemScore >= firstLayerMinScore) {
            return systemIntent(question, firstLayerDecision, systemScore);
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
     * 构建统一的 SYSTEM 路由结果。
     *
     * @param question           当前子问题
     * @param firstLayerDecision 第一层打分及判定结果
     * @param systemScore        第一层 SYSTEM 分数
     * @return 不启用全库检索的 SYSTEM 意图
     */
    private SubQuestionIntent systemIntent(
            String question,
            FirstLayerIntentDecision firstLayerDecision,
            double systemScore) {
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

    /**
     * 获取全库检索相关性下限。
     *
     * @param value 配置的全库检索最低分
     * @return 配置值；未配置时使用 0.20
     */
    private double defaultGlobalMinScore(Double value) {
        return value == null ? 0.20D : value;
    }
}
