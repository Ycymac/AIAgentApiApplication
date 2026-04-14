package com.ycy.aiapplication.rag.core.guidance;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.ycy.aiapplication.rag.config.GuidanceProperties;
import com.ycy.aiapplication.rag.constant.RAGConstant;
import com.ycy.aiapplication.rag.core.intent.classifier.impl.SecondLayerIntentClassifier;
import com.ycy.aiapplication.rag.core.intent.common.IntentNode;
import com.ycy.aiapplication.rag.core.intent.common.NodeScore;
import com.ycy.aiapplication.rag.core.intent.common.SubQuestionIntent;
import com.ycy.aiapplication.rag.core.intent.common.enums.IntentKind;
import com.ycy.aiapplication.rag.core.intent.management.IntentNodeRegistry;
import com.ycy.aiapplication.rag.core.prompt.PromptTemplateLoader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 引导式问答服务。
 * <p>
 * 当前实现以本项目的意图识别结果为准，只对最终路由为指定类型的子问题触发 guidance。
 * 目前默认触发类型为 {@link IntentKind#UNKNOWN}，并构建二层引导体系：
 * 1. UNKNOWN 且存在达到阈值的二层候选结点时，返回候选式引导回答。
 * 2. UNKNOWN 且候选结点都低于阈值时，返回补充信息式引导回答。
 */
@Service
@RequiredArgsConstructor
public class IntentGuidanceService {

    private static final String SUPPLEMENT_GUIDANCE_PROMPT_PATH = "prompt/guidance-supplement-prompt.st";
    private static final String DEFAULT_TOPIC_NAME = "当前问题";

    private final GuidanceProperties guidanceProperties;
    private final IntentNodeRegistry intentNodeRegistry;
    private final SecondLayerIntentClassifier secondLayerIntentClassifier;
    private final PromptTemplateLoader promptTemplateLoader;

    /**
     * 为当前请求生成引导式提示。
     * @param subIntents 当前请求对应的子问题意图列表
     * @return 若存在需要引导的 UNKNOWN 子问题，则返回对应 prompt；否则返回 none
     */
    public GuidanceDecision detectAmbiguity(List<SubQuestionIntent> subIntents) {
        if (!Boolean.TRUE.equals(guidanceProperties.getEnabled()) || CollUtil.isEmpty(subIntents)) {
            return GuidanceDecision.none();
        }

        List<SubQuestionIntent> guidanceTargets = findGuidanceTargets(subIntents);
        if (CollUtil.isEmpty(guidanceTargets)) {
            return GuidanceDecision.none();
        }

        List<String> prompts = new ArrayList<>();
        for (SubQuestionIntent subIntent : guidanceTargets) {
            String subQuestion = subIntent.subQuestion();
            if (StrUtil.isBlank(subQuestion)) {
                continue;
            }

            // 仅对 UNKNOWN 子问题重新执行二层识别，用于决定走候选式还是补充信息式引导。
            List<NodeScore> candidates = secondLayerIntentClassifier.classifyTargets(subQuestion);
            List<NodeScore> qualifiedCandidates = findQualifiedCandidates(candidates);

            if (CollUtil.isNotEmpty(qualifiedCandidates)) {
                List<String> optionIds = collectCandidateNodeOptions(qualifiedCandidates);
                if (CollUtil.isEmpty(optionIds)) {
                    continue;
                }

                List<String> optionNames = resolveOptionNames(optionIds);
                if (shouldSkipGuidance(subQuestion, optionNames)) {
                    continue;
                }
                prompts.add(buildCandidatePrompt(subQuestion, optionIds));
                continue;
            }

            prompts.add(buildSupplementPrompt(subQuestion));
        }

        if (CollUtil.isEmpty(prompts)) {
            return GuidanceDecision.none();
        }
        return GuidanceDecision.prompt(String.join("\n\n", prompts));
    }

    /**
     * 找出需要进入 guidance 的子问题。
     *
     * @param subIntents 子问题意图列表
     * @return 命中 guidance 触发类型的子问题集合
     */
    private List<SubQuestionIntent> findGuidanceTargets(List<SubQuestionIntent> subIntents) {
        IntentKind triggerType = Optional.ofNullable(guidanceProperties.getAmbiguityIntentType())
                .orElse(IntentKind.UNKNOWN);
        return subIntents.stream()
                .filter(subIntent -> subIntent != null && subIntent.routeKind() == triggerType)
                .toList();
    }

    /**
     * 从二层候选中找出达到引导分数阈值的结点。
     *
     * @param scores 二层意图识别返回的候选列表
     * @return 分数达到 guidance 阈值且属于知识库结点的候选列表
     */
    private List<NodeScore> findQualifiedCandidates(List<NodeScore> scores) {
        if (CollUtil.isEmpty(scores)) {
            return List.of();
        }
        double minScore = Optional.ofNullable(guidanceProperties.getAmbiguityMinScore()).orElse(0.25D);
        return scores.stream()
                .filter(score -> score != null && score.getNode() != null)
                .filter(score -> score.getNode().isKB())
                .filter(score -> score.getScore() >= minScore)
                .limit(Optional.ofNullable(guidanceProperties.getMaxOptions()).orElse(6))
                .toList();
    }

    /**
     * 将高分候选结点整理为可展示的选项列表。
     *
     * @param qualifiedCandidates 达到阈值的二层候选结点
     * @return 去重并截断后的候选结点 ID 列表
     */
    private List<String> collectCandidateNodeOptions(List<NodeScore> qualifiedCandidates) {
        Set<String> ordered = new LinkedHashSet<>();
        for (NodeScore candidate : qualifiedCandidates) {
            IntentNode node = candidate.getNode();
            if (node == null || StrUtil.isBlank(node.getId())) {
                continue;
            }
            ordered.add(node.getId());
            if (ordered.size() >= Optional.ofNullable(guidanceProperties.getMaxOptions()).orElse(6)) {
                break;
            }
        }
        return new ArrayList<>(ordered);
    }

    /**
     * 判断用户是否已经明确提到了某个候选结点名称。
     *
     * @param question 子问题文本
     * @param optionNames 候选结点名称列表
     * @return 若问题中已包含候选名称，则无需重复引导
     */
    private boolean shouldSkipGuidance(String question, List<String> optionNames) {
        if (StrUtil.isBlank(question) || CollUtil.isEmpty(optionNames)) {
            return false;
        }
        String normalizedQuestion = normalizeName(question);
        for (String name : optionNames) {
            if (StrUtil.isBlank(name)) {
                continue;
            }
            String alias = normalizeName(name);
            if (alias.length() >= 2 && normalizedQuestion.contains(alias)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 根据结点 ID 解析结点名称。
     *
     * @param optionIds 候选结点 ID 列表
     * @return 候选结点名称列表
     */
    private List<String> resolveOptionNames(List<String> optionIds) {
        if (CollUtil.isEmpty(optionIds)) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (String id : optionIds) {
            IntentNode node = intentNodeRegistry.getNodeById(id);
            if (node != null) {
                names.add(StrUtil.blankToDefault(node.getName(), node.getId()));
            }
        }
        return names;
    }

    /**
     * 构建候选式引导 prompt。
     *
     * @param topicName 当前子问题文本
     * @param optionIds 达到 guidance 分数阈值的候选结点 ID 列表
     * @return 基于 guidance-prompt.st 渲染后的提示语
     */
    private String buildCandidatePrompt(String topicName, List<String> optionIds) {
        return promptTemplateLoader.render(
                RAGConstant.GUIDANCE_PROMPT_PATH,
                Map.of(
                        "topic_name", StrUtil.blankToDefault(topicName, DEFAULT_TOPIC_NAME),
                        "options", renderOptions(optionIds)
                )
        );
    }

    /**
     * 构建补充信息式引导 prompt。
     *
     * @param topicName 当前子问题文本
     * @return 基于 guidance-supplement-prompt.st 渲染后的提示语
     */
    private String buildSupplementPrompt(String topicName) {
        return promptTemplateLoader.render(
                SUPPLEMENT_GUIDANCE_PROMPT_PATH,
                Map.of("topic_name", StrUtil.blankToDefault(topicName, DEFAULT_TOPIC_NAME))
        );
    }

    /**
     * 将候选结点渲染为编号选项。
     *
     * @param optionIds 候选结点 ID 列表
     * @return 可直接填充到 guidance 模板中的选项文本
     */
    private String renderOptions(List<String> optionIds) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < optionIds.size(); i++) {
            String id = optionIds.get(i);
            IntentNode node = intentNodeRegistry.getNodeById(id);
            String name = node == null || StrUtil.isBlank(node.getName()) ? id : node.getName();
            sb.append(i + 1).append(") ").append(name).append("\n");
        }
        return sb.toString().trim();
    }

    /**
     * 对文本做归一化处理，便于名称匹配。
     *
     * @param name 原始文本
     * @return 去掉标点和空白后的归一化结果
     */
    private String normalizeName(String name) {
        if (name == null) {
            return "";
        }
        String cleaned = name.trim().toLowerCase(Locale.ROOT);
        return cleaned.replaceAll("[\\p{Punct}\\s]+", "");
    }
}
