package com.ycy.aiapplication.rag.eval.intent;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ycy.aiapplication.framework.convention.ChatMessage;
import com.ycy.aiapplication.framework.convention.ChatRequest;
import com.ycy.aiapplication.infrastructure.ai.chat.interfaces.LLMService;
import com.ycy.aiapplication.infrastructure.ai.toolkit.LLMResponseCleaner;
import com.ycy.aiapplication.rag.config.RAGIntentProperties;
import com.ycy.aiapplication.rag.core.intent.FirstLayerIntentDecision;
import com.ycy.aiapplication.rag.core.intent.common.IntentNode;
import com.ycy.aiapplication.rag.core.intent.common.NodeScore;
import com.ycy.aiapplication.rag.core.intent.common.SubQuestionIntent;
import com.ycy.aiapplication.rag.core.intent.common.enums.IntentKind;
import com.ycy.aiapplication.rag.core.intent.management.IntentNodeRegistry;
import com.ycy.aiapplication.rag.core.prompt.PromptTemplateLoader;
import com.ycy.aiapplication.rag.core.rewrite.common.RewriteResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Eval-only one-pass intent resolver.
 *
 * <p>SYSTEM and all enabled knowledge-base nodes participate in the same LLM scoring call.
 * The production layered resolver remains unchanged so this experiment can be A/B tested.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.rag-eval.enabled", havingValue = "true")
public class RagEvalCombinedIntentResolver {

    static final String PROMPT_PATH = "prompt/eval-combined-intent-classifier.st";
    static final String SYSTEM_NODE_ID = "SYSTEM";

    private static final IntentNode SYSTEM_NODE = IntentNode.builder()
            .id(SYSTEM_NODE_ID)
            .name("系统直答")
            .description("系统身份、能力说明、欢迎语、寒暄、感谢或情绪互动，不需要知识库证据。")
            .examples(List.of("你好", "你是谁", "你能做什么", "谢谢"))
            .kind(IntentKind.SYSTEM)
            .build();

    private final LLMService llmService;
    private final PromptTemplateLoader promptTemplateLoader;
    private final ObjectMapper objectMapper;
    private final IntentNodeRegistry intentNodeRegistry;
    private final RAGIntentProperties intentProperties;

    public List<SubQuestionIntent> resolve(RewriteResult rewriteResult) {
        List<String> questions = CollUtil.isNotEmpty(rewriteResult.subQuestions())
                ? rewriteResult.subQuestions()
                : List.of(rewriteResult.rewrittenQuestion());
        return questions.stream().map(this::resolveQuestion).toList();
    }

    SubQuestionIntent resolveQuestion(String question) {
        List<NodeScore> allScores = classifyTargets(question);
        double systemScore = findScore(allScores, SYSTEM_NODE_ID);
        List<NodeScore> qualifiedKnowledgeScores = allScores.stream()
                .filter(score -> score.getNode() != null && score.getNode().isKB())
                .filter(score -> score.getScore() >= knowledgeMinScore())
                .limit(knowledgeTopN())
                .toList();
        double bestKnowledgeScore = qualifiedKnowledgeScores.stream()
                .mapToDouble(NodeScore::getScore)
                .max()
                .orElse(0D);
        boolean knowledgeWins = CollUtil.isNotEmpty(qualifiedKnowledgeScores)
                && bestKnowledgeScore >= systemScore;

        FirstLayerIntentDecision diagnosticDecision = FirstLayerIntentDecision.builder()
                .ragScore(bestKnowledgeScore)
                .systemScore(systemScore)
                .shouldRetrieveKnowledgeBase(knowledgeWins)
                .nodeScores(allScores)
                .build();

        if (knowledgeWins) {
            return buildIntent(question, IntentKind.KB, diagnosticDecision, qualifiedKnowledgeScores);
        }
        if (systemScore >= firstLayerMinScore()) {
            NodeScore selectedSystem = allScores.stream()
                    .filter(score -> score.getNode() != null && score.getNode().isSystem())
                    .findFirst()
                    .orElse(NodeScore.builder().node(SYSTEM_NODE).score(systemScore).build());
            return buildIntent(question, IntentKind.SYSTEM, diagnosticDecision, List.of(selectedSystem));
        }
        if (CollUtil.isNotEmpty(qualifiedKnowledgeScores)) {
            return buildIntent(question, IntentKind.KB, diagnosticDecision, qualifiedKnowledgeScores);
        }
        return buildIntent(question, IntentKind.UNKNOWN, diagnosticDecision, List.of());
    }

    List<NodeScore> classifyTargets(String question) {
        if (StrUtil.isBlank(question)) {
            return List.of();
        }
        List<IntentNode> knowledgeNodes = intentNodeRegistry.listKnowledgeNodes();
        List<IntentNode> candidates = new ArrayList<>();
        candidates.add(SYSTEM_NODE);
        if (CollUtil.isNotEmpty(knowledgeNodes)) {
            candidates.addAll(knowledgeNodes.stream().filter(IntentNode::isKB).toList());
        }

        String prompt = promptTemplateLoader.render(
                PROMPT_PATH,
                Map.of("intent_list", buildIntentList(candidates)));
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(ChatMessage.system(prompt), ChatMessage.user(question)))
                .temperature(0.1D)
                .topP(0.3D)
                .thinking(false)
                .build();
        return parseNodeScores(llmService.chat(request), candidates);
    }

    List<NodeScore> parseNodeScores(String raw, List<IntentNode> candidates) {
        try {
            String cleaned = LLMResponseCleaner.stripMarkdownCodeFence(raw);
            JsonNode root = objectMapper.readTree(cleaned);
            JsonNode array = root.isArray() ? root : root.path("results");
            if (!array.isArray()) {
                return List.of();
            }

            Map<String, IntentNode> candidateMap = candidates.stream()
                    .filter(node -> StrUtil.isNotBlank(node.getId()))
                    .collect(Collectors.toMap(
                            IntentNode::getId,
                            Function.identity(),
                            (left, right) -> left,
                            LinkedHashMap::new));
            Map<String, Double> scoreById = new LinkedHashMap<>();
            for (JsonNode item : array) {
                String id = item.path("id").asText(null);
                if (StrUtil.isBlank(id) || !candidateMap.containsKey(id)) {
                    continue;
                }
                double score = Math.max(0D, Math.min(1D, item.path("score").asDouble(0D)));
                scoreById.merge(id, score, Math::max);
            }
            return scoreById.entrySet().stream()
                    .map(entry -> NodeScore.builder()
                            .node(candidateMap.get(entry.getKey()))
                            .score(entry.getValue())
                            .build())
                    .sorted(Comparator.comparingDouble(NodeScore::getScore).reversed())
                    .toList();
        } catch (Exception exception) {
            log.warn("解析 eval 综合意图识别结果失败, raw={}", raw, exception);
            return List.of();
        }
    }

    private SubQuestionIntent buildIntent(
            String question,
            IntentKind routeKind,
            FirstLayerIntentDecision decision,
            List<NodeScore> selectedScores) {
        return SubQuestionIntent.builder()
                .subQuestion(question)
                .routeKind(routeKind)
                .globalKbFallback(false)
                .firstLayerDecision(decision)
                .nodeScores(selectedScores)
                .build();
    }

    private String buildIntentList(List<IntentNode> nodes) {
        StringBuilder builder = new StringBuilder();
        for (IntentNode node : nodes) {
            builder.append("- id=").append(node.getId()).append('\n');
            builder.append("  type=").append(node.isSystem() ? "SYSTEM" : "KB").append('\n');
            builder.append("  name=").append(StrUtil.blankToDefault(node.getName(), "")).append('\n');
            builder.append("  kbId=").append(StrUtil.blankToDefault(node.getKbId(), "")).append('\n');
            builder.append("  description=")
                    .append(StrUtil.blankToDefault(node.getDescription(), ""))
                    .append('\n');
            if (CollUtil.isNotEmpty(node.getExamples())) {
                builder.append("  examples=").append(String.join(" / ", node.getExamples())).append('\n');
            }
            builder.append('\n');
        }
        return builder.toString();
    }

    private double findScore(List<NodeScore> scores, String nodeId) {
        return scores.stream()
                .filter(score -> score.getNode() != null && nodeId.equals(score.getNode().getId()))
                .map(NodeScore::getScore)
                .findFirst()
                .orElse(0D);
    }

    private double knowledgeMinScore() {
        Double value = intentProperties.getKnowledgeMinScore();
        return value == null ? 0.45D : value;
    }

    private double firstLayerMinScore() {
        Double value = intentProperties.getFirstLayerMinScore();
        return value == null ? 0.55D : value;
    }

    private int knowledgeTopN() {
        Integer value = intentProperties.getKnowledgeTopN();
        return value == null || value <= 0 ? 3 : value;
    }
}
