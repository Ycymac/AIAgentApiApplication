package com.ycy.aiapplication.rag.core.intent.classifier.impl;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ycy.aiapplication.framework.convention.ChatMessage;
import com.ycy.aiapplication.framework.convention.ChatRequest;
import com.ycy.aiapplication.infrastructure.ai.chat.interfaces.LLMService;
import com.ycy.aiapplication.infrastructure.ai.toolkit.LLMResponseCleaner;
import com.ycy.aiapplication.rag.config.RAGIntentProperties;
import com.ycy.aiapplication.rag.core.intent.FirstLayerIntentDecision;
import com.ycy.aiapplication.rag.core.intent.classifier.IntentClassifier;
import com.ycy.aiapplication.rag.core.intent.common.IntentNode;
import com.ycy.aiapplication.rag.core.intent.common.NodeScore;
import com.ycy.aiapplication.rag.core.intent.common.enums.IntentKind;
import com.ycy.aiapplication.rag.core.prompt.PromptTemplateLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.ycy.aiapplication.rag.constant.RAGConstant.INTENT_FIRST_LAYER_PROMPT_PATH;

/**
 * 第一层粗分类器。
 * <p>
 * 作用：
 * 1. 只在固定的两个节点 RAG / SYSTEM 之间做粗粒度判断。
 * 2. 输出两类分数，并给出“是否值得进入第二层知识库识别”的初步结论。
 * 3. 为后续三层意图解析提供第一层信号。
 * 执行顺序：
 * 1. {@code IntentResolver} 调用 {@link #decide(String)}。
 * 2. {@link #decide(String)} 内部先调用 {@link #classifyTargets(String)} 让模型打分。
 * 3. 再基于阈值和分数关系封装为 {@link FirstLayerIntentDecision}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FirstLayerIntentClassifier implements IntentClassifier {

    /**
     * 固定的 RAG 粗分类节点。
     */
    private static final IntentNode RAG_NODE = IntentNode.builder()
            .id("RAG")
            .name("RAG")
            .kind(IntentKind.KB)
            .description("判断当前问题是否值得进入知识库检索流程")
            .build();

    /**
     * 固定的 SYSTEM 粗分类节点。
     */
    private static final IntentNode SYSTEM_NODE = IntentNode.builder()
            .id("SYSTEM")
            .name("SYSTEM")
            .kind(IntentKind.SYSTEM)
            .description("判断当前问题是否属于系统问答、系统介绍、欢迎语或情绪互动")
            .build();

    private final LLMService llmService;
    private final PromptTemplateLoader promptTemplateLoader;
    private final ObjectMapper objectMapper;
    private final RAGIntentProperties intentProperties;

    /**
     * 执行第一层粗分类。
     *
     * @param question 当前待判断的问题
     * @return 固定节点 RAG / SYSTEM 的打分结果列表
     */
    @Override
    public List<NodeScore> classifyTargets(String question) {
        if (StrUtil.isBlank(question)) {
            return List.of();
        }
        String prompt = promptTemplateLoader.load(INTENT_FIRST_LAYER_PROMPT_PATH);
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(
                        ChatMessage.system(prompt),
                        ChatMessage.user(question)
                ))
                .temperature(0.1D)
                .topP(0.3D)
                .thinking(false)
                .build();
        return parseNodeScores(llmService.chat(request));
    }

    /**
     * 生成第一层决策对象。
     *
     * @param question 当前待判断的问题
     * @return 包含 RAG 分、SYSTEM 分以及是否值得进入知识库层的决策结果
     */
    public FirstLayerIntentDecision decide(String question) {
        List<NodeScore> scores = classifyTargets(question);
        double ragScore = findScore(scores, RAG_NODE.getId());
        double systemScore = findScore(scores, SYSTEM_NODE.getId());

        // 只要 RAG 分达到阈值，或者至少不弱于 SYSTEM，就允许进入第二层知识库识别。
        boolean shouldRetrieveKnowledgeBase = ragScore >= defaultScore(intentProperties.getFirstLayerMinScore())
                || ragScore >= systemScore;
        return FirstLayerIntentDecision.builder()
                .ragScore(ragScore)
                .systemScore(systemScore)
                .shouldRetrieveKnowledgeBase(shouldRetrieveKnowledgeBase)
                .nodeScores(scores)
                .build();
    }

    /**
     * 解析第一层粗分类模型输出。
     *
     * @param raw 模型原始输出
     * @return 固定节点打分结果；解析失败时返回空列表
     */
    private List<NodeScore> parseNodeScores(String raw) {
        try {
            // 先去除可能出现的 ```json 包裹，保证后续 JSON 解析稳定。
            String cleaned = LLMResponseCleaner.stripMarkdownCodeFence(raw);
            JsonNode jsonNode = objectMapper.readTree(cleaned);
            JsonNode arrayNode = jsonNode.isArray() ? jsonNode : jsonNode.path("results");
            if (!arrayNode.isArray()) {
                return List.of();
            }

            List<NodeScore> result = new ArrayList<>();
            for (JsonNode each : arrayNode) {
                String id = each.path("id").asText(null);
                IntentNode node = resolveFixedNode(id);
                if (node == null) {
                    continue;
                }
                result.add(NodeScore.builder()
                        .node(node)
                        .score(each.path("score").asDouble(0D))
                        .build());
            }
            result.sort(Comparator.comparingDouble(NodeScore::getScore).reversed());
            return result;
        } catch (Exception ex) {
            log.warn("解析一层意图识别结果失败, raw={}", raw, ex);
            return List.of();
        }
    }

    /**
     * 将固定节点 ID 还原为节点对象。
     *
     * @param id 模型返回的节点 ID
     * @return 固定节点对象；未知节点时返回 null
     */
    private IntentNode resolveFixedNode(String id) {
        return switch (StrUtil.blankToDefault(id, "")) {
            case "RAG" -> RAG_NODE;
            case "SYSTEM" -> SYSTEM_NODE;
            default -> null;
        };
    }

    /**
     * 从打分列表中提取指定节点的分数。
     *
     * @param scores 打分列表
     * @param nodeId 固定节点 ID
     * @return 对应分数；未命中时返回 0
     */
    private double findScore(List<NodeScore> scores, String nodeId) {
        return scores.stream()
                .filter(each -> each.getNode() != null && nodeId.equals(each.getNode().getId()))
                .map(NodeScore::getScore)
                .findFirst()
                .orElse(0D);
    }

    /**
     * 获取阈值配置的可用值。
     *
     * @param score 配置文件中的阈值
     * @return 实际使用的阈值；未配置时默认 0.55
     */
    private double defaultScore(Double score) {
        return score == null ? 0.55D : score;
    }
}
