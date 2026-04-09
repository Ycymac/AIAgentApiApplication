package com.ycy.aiapplication.rag.core.intent.classifier.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ycy.aiapplication.framework.convention.ChatMessage;
import com.ycy.aiapplication.framework.convention.ChatRequest;
import com.ycy.aiapplication.infrastructure.ai.chat.interfaces.LLMService;
import com.ycy.aiapplication.infrastructure.ai.toolkit.LLMResponseCleaner;
import com.ycy.aiapplication.rag.core.intent.classifier.IntentClassifier;
import com.ycy.aiapplication.rag.core.intent.common.IntentNode;
import com.ycy.aiapplication.rag.core.intent.common.NodeScore;
import com.ycy.aiapplication.rag.core.intent.management.IntentNodeRegistry;
import com.ycy.aiapplication.rag.core.intent.management.IntentTreeCacheManager;
import com.ycy.aiapplication.rag.core.prompt.PromptTemplateLoader;
import com.ycy.aiapplication.rag.service.IntentNodeManageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.ycy.aiapplication.rag.constant.RAGConstant.INTENT_CLASSIFIER_PROMPT_PATH;

/**
 * 二层知识库意图分类器。
 * <p>
 * 作用：
 * 1. 加载当前可用的知识库意图节点。
 * 2. 将节点信息拼装为提示词，交给大模型对知识库节点进行打分。
 * 3. 把模型返回的 JSON 结果解析为 {@link NodeScore}，供意图解析器继续做阈值和 TopN 处理。
 * 整体调用顺序：
 * 1. {@code IntentResolver} 先完成第一层粗分类。
 * 2. 当第一层认为问题值得检索知识库时，调用当前类执行第二层知识库节点打分。
 * 3. 当前类只负责打分，不直接决定最终走 KB、SYSTEM 还是 UNKNOWN。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SecondLayerIntentClassifier implements IntentClassifier, IntentNodeRegistry {

    private final LLMService llmService;
    private final PromptTemplateLoader promptTemplateLoader;
    private final IntentTreeCacheManager intentTreeCacheManager;
    private final IntentNodeManageService intentNodeManageService;
    private final ObjectMapper objectMapper;

    /**
     * 执行第二层知识库节点打分。
     *
     * @param question 用户问题，通常是改写和拆分后的单个子问题
     * @return 按分值从高到低排序的知识库节点列表；没有候选节点或问题为空时返回空列表
     */
    @Override
    public List<NodeScore> classifyTargets(String question) {
        List<IntentNode> knowledgeNodes = listKnowledgeNodes();
        if (CollUtil.isEmpty(knowledgeNodes) || StrUtil.isBlank(question)) {
            return List.of();
        }

        // 将候选知识库节点展开为提示词文本，限制模型只能在这些节点中打分。
        String prompt = promptTemplateLoader.render(
                INTENT_CLASSIFIER_PROMPT_PATH,
                Map.of("intent_list", buildIntentList(knowledgeNodes))
        );
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(
                        ChatMessage.system(prompt),
                        ChatMessage.user(question)
                ))
                .temperature(0.1D)
                .topP(0.3D)
                .thinking(false)
                .build();

        String raw = llmService.chat(request);
        return parseNodeScores(raw, knowledgeNodes);
    }

    /**
     * 按节点 ID 查询知识库意图节点。
     *
     * @param id 意图节点 ID
     * @return 命中的节点；未命中时返回 null
     */
    @Override
    public IntentNode getNodeById(String id) {
        if (StrUtil.isBlank(id)) {
            return null;
        }
        return listKnowledgeNodes().stream()
                .filter(each -> id.equals(each.getId()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 获取当前启用的知识库意图节点。
     *
     * @return 节点列表，优先走 Redis 缓存，缓存未命中时回源数据库并回填缓存
     */
    @Override
    public List<IntentNode> listKnowledgeNodes() {
        List<IntentNode> cached = intentTreeCacheManager.getKnowledgeNodesFromCache();
        if (CollUtil.isNotEmpty(cached)) {
            return cached;
        }

        // 缓存未命中时从管理服务加载，管理服务内部会先做知识库和节点表同步。
        List<IntentNode> loaded = intentNodeManageService.listEnabledIntentNodes();
        if (CollUtil.isNotEmpty(loaded)) {
            intentTreeCacheManager.saveKnowledgeNodesToCache(loaded);
        }
        return loaded;
    }

    /**
     * 将知识库节点转换为提示词中的候选节点列表描述。
     *
     * @param nodes 当前参与打分的知识库节点
     * @return 可直接填充到提示词模板中的文本
     */
    private String buildIntentList(List<IntentNode> nodes) {
        StringBuilder builder = new StringBuilder();
        for (IntentNode node : nodes) {
            builder.append("- id=").append(node.getId()).append("\n");
            builder.append("  kbId=").append(StrUtil.blankToDefault(node.getKbId(), "")).append("\n");
            builder.append("  name=").append(StrUtil.blankToDefault(node.getName(), "")).append("\n");
            builder.append("  description=").append(StrUtil.blankToDefault(node.getDescription(), "")).append("\n");
            if (CollUtil.isNotEmpty(node.getExamples())) {
                builder.append("  examples=").append(String.join(" / ", node.getExamples())).append("\n");
            }
            builder.append("\n");
        }
        return builder.toString();
    }

    /**
     * 解析大模型返回的第二层知识库打分结果。
     *
     * @param raw   模型原始输出
     * @param nodes 本轮参与打分的知识库节点，用于校验模型返回是否合法
     * @return 合法且按分数降序排列的结果列表；解析失败时返回空列表
     */
    private List<NodeScore> parseNodeScores(String raw, List<IntentNode> nodes) {
        try {
            // 模型可能把 JSON 包装在 markdown 代码块中，先清洗后解析。
            String cleaned = LLMResponseCleaner.stripMarkdownCodeFence(raw);
            JsonNode jsonNode = objectMapper.readTree(cleaned);
            JsonNode arrayNode = jsonNode.isArray() ? jsonNode : jsonNode.path("results");
            if (!arrayNode.isArray()) {
                return List.of();
            }

            // 建立节点索引，防止模型返回候选集合之外的非法节点 ID。
            Map<String, IntentNode> nodeMap = nodes.stream()
                    .collect(Collectors.toMap(IntentNode::getId, Function.identity(), (left, right) -> left));
            List<NodeScore> result = new ArrayList<>();
            for (JsonNode each : arrayNode) {
                String id = each.path("id").asText(null);
                if (StrUtil.isBlank(id) || !nodeMap.containsKey(id)) {
                    continue;
                }
                result.add(NodeScore.builder()
                        .node(nodeMap.get(id))
                        .score(each.path("score").asDouble(0D))
                        .build());
            }

            // 后续需要按高分优先做阈值过滤和 TopN 截断，这里统一先做降序排序。
            result.sort(Comparator.comparingDouble(NodeScore::getScore).reversed());
            return result;
        } catch (Exception ex) {
            log.warn("解析二层知识库意图识别结果失败, raw={}", raw, ex);
            return List.of();
        }
    }
}
