/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ycy.aiapplication.rag.core.prompt;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.ycy.aiapplication.framework.convention.ChatMessage;
import com.ycy.aiapplication.framework.convention.RetrievedChunk;
import com.ycy.aiapplication.rag.core.intent.IntentNode;
import com.ycy.aiapplication.rag.core.intent.NodeScore;
import com.ycy.aiapplication.rag.core.prompt.plan.PromptPlan;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.ycy.aiapplication.rag.constant.RAGConstant.RAG_ENTERPRISE_PROMPT_PATH;

/**
 * RAG Prompt 编排服务。
 * <p>
 * 当前实现聚焦 KB 问答场景，负责选择系统提示词模板，并组装最终发送给 LLM 的消息序列。
 */
@Service
@RequiredArgsConstructor
public class RAGPromptService {

    private static final String KB_CONTEXT_HEADER = "## 文档内容";

    /**
     * 提示词模板加载器。
     */
    private final PromptTemplateLoader promptTemplateLoader;

    /**
     * 构造系统提示词，并对模板文本做格式清理。
     */
    public String buildSystemPrompt(PromptContext context) {
        String template = resolveKbPromptTemplate(context);
        return StrUtil.isBlank(template) ? "" : PromptTemplateUtils.cleanupPrompt(template);
    }

    /**
     * 组装发送给 LLM 的完整消息列表：system + evidence + history + user。
     */
    public List<ChatMessage> buildStructuredMessages(PromptContext context,
                                                     List<ChatMessage> history,
                                                     String question,
                                                     List<String> subQuestions) {
        List<ChatMessage> messages = new ArrayList<>();
        String systemPrompt = buildSystemPrompt(context);
        if (StrUtil.isNotBlank(systemPrompt)) {
            messages.add(ChatMessage.system(systemPrompt));
        }
        if (StrUtil.isNotBlank(context.getKbContext())) {
            messages.add(ChatMessage.user(formatKbEvidence(KB_CONTEXT_HEADER, context.getKbContext())));
        }
        if (CollUtil.isNotEmpty(history)) {
            messages.addAll(history);
        }

        // 多子问题场景下显式编号，降低模型漏答和错位回答的风险。
        if (CollUtil.isNotEmpty(subQuestions) && subQuestions.size() > 1) {
            StringBuilder userMessage = new StringBuilder();
            userMessage.append("请基于上述文档内容，回答以下问题：\n\n");
            for (int i = 0; i < subQuestions.size(); i++) {
                userMessage.append(i + 1).append(". ").append(subQuestions.get(i)).append("\n");
            }
            messages.add(ChatMessage.user(userMessage.toString().trim()));
        } else if (StrUtil.isNotBlank(question)) {
            messages.add(ChatMessage.user(question));
        }

        return messages;
    }

    /**
     * 基于已检索到的意图结果，选择要使用的 Prompt 方案。
     *
     * @param intents 意图识别结果
     * @param intentChunks 各意图命中的检索分块
     */
    private PromptPlan selectPromptPlan(List<NodeScore> intents, Map<String, List<RetrievedChunk>> intentChunks) {
        List<NodeScore> safeIntents = intents == null ? Collections.emptyList() : intents;

        // 只保留真正检索出内容的意图，避免空意图参与模板选择。
        List<NodeScore> retained = safeIntents.stream()
                .filter(ns -> {
                    IntentNode node = ns.getNode();
                    String key = resolveIntentChunkKey(node);
                    List<RetrievedChunk> chunks = intentChunks == null ? null : intentChunks.get(key);
                    return CollUtil.isNotEmpty(chunks);
                })
                .toList();

        if (retained.isEmpty()) {
            // 没有可用意图时不指定专属模板，后续回退到默认 KB 模板。
            return new PromptPlan(Collections.emptyList(), null);
        }

        if (retained.size() == 1) {
            IntentNode only = retained.get(0).getNode();
            String template = StrUtil.emptyIfNull(only.getPromptTemplate()).trim();

            if (StrUtil.isNotBlank(template)) {
                // 单意图且节点自带完整模板时，优先使用该模板。
                return new PromptPlan(retained, template);
            }
        }

        // 多意图或未配置专属模板时，统一回退默认 KB 模板。
        return new PromptPlan(retained, null);
    }

    /**
     * 为 KB 场景解析最终要使用的 Prompt 模板。
     */
    private String resolveKbPromptTemplate(PromptContext context) {
        if (!context.hasKb()) {
            throw new IllegalStateException("提示词上下文缺少知识库内容，无法构建 KB Prompt。");
        }

        PromptPlan plan = selectPromptPlan(context.getKbIntents(), context.getIntentChunks());
        return StrUtil.isNotBlank(plan.getBaseTemplate())
                ? plan.getBaseTemplate()
                : loadDefaultTemplate(PromptScene.KB);
    }

    /**
     * 加载场景默认模板。
     */
    private String loadDefaultTemplate(PromptScene scene) {
        return switch (scene) {
            case KB -> promptTemplateLoader.load(RAG_ENTERPRISE_PROMPT_PATH);
            case EMPTY -> "";
        };
    }

    /**
     * 组装知识库证据块。
     */
    private String formatKbEvidence(String header, String body) {
        return header + "\n" + body.trim();
    }

    /**
     * 从意图节点中提取检索结果映射使用的 key。
     */
    private static String resolveIntentChunkKey(IntentNode node) {
        if (node == null) {
            return "";
        }
        if (StrUtil.isNotBlank(node.getId())) {
            return node.getId();
        }
        return String.valueOf(node.getId());
    }
}
