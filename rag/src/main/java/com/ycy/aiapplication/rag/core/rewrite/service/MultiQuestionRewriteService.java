package com.ycy.aiapplication.rag.core.rewrite.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ycy.aiapplication.framework.convention.ChatMessage;
import com.ycy.aiapplication.framework.convention.ChatRequest;
import com.ycy.aiapplication.infrastructure.ai.chat.interfaces.LLMService;
import com.ycy.aiapplication.infrastructure.ai.toolkit.LLMResponseCleaner;
import com.ycy.aiapplication.rag.config.RAGReWriteProperties;
import com.ycy.aiapplication.rag.core.prompt.PromptTemplateLoader;
import com.ycy.aiapplication.rag.core.rewrite.common.RewriteResult;
import com.ycy.aiapplication.rag.core.rewrite.common.RewriteStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static com.ycy.aiapplication.rag.constant.RAGConstant.QUERY_REWRITE_AND_SPLIT_PROMPT_PATH;

/**
 * 查询预处理：改写 + 拆分多问句
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MultiQuestionRewriteService implements QueryRewriteService {

    private final LLMService llmService;
    private final RAGReWriteProperties ragRewriteProperties;
    private final QueryTermMappingService queryTermMappingService;
    private final PromptTemplateLoader promptTemplateLoader;

    /**
     * 获取重写之后的问题
     */
    @Override
    public String rewrite(String userQuestion) {
        return rewriteAndSplit(userQuestion).rewrittenQuestion();
    }

    /**
     * 返回重写并拆分的重写结果
     */
    @Override
    public RewriteResult rewriteWithSplit(String userQuestion) {
        return rewriteAndSplit(userQuestion);
    }

    /**
     * 带有历史纪录的问题重写
     */
    @Override
    public RewriteResult rewriteWithSplit(String userQuestion, List<ChatMessage> history) {
        return rewriteWithSplit(userQuestion, history, false);
    }

    /**
     * 使用指定范围的会话历史执行问题改写、拆分、约束及偏好提取。
     *
     * @param userQuestion    当前用户问题
     * @param history         本次允许模型读取的会话历史
     * @param historyComplete true 表示已经加载摘要水位线之后的全部可用历史
     * @return 包含改写状态、子问题、当前约束及会话偏好的结构化结果
     */
    @Override
    public RewriteResult rewriteWithSplit(String userQuestion,
                                          List<ChatMessage> history,
                                          boolean historyComplete) {
        if (!ragRewriteProperties.getQueryRewriteEnabled()) {
            String normalized = queryTermMappingService.normalize(userQuestion);
            List<String> subs = ruleBasedSplit(normalized);
            return RewriteResult.success(normalized, subs);
        }

        String normalizedQuestion = queryTermMappingService.normalize(userQuestion);

        return callLLMRewriteAndSplit(normalizedQuestion, userQuestion, history, historyComplete);
    }

    /**
     * 不带历史纪录的重写拆分问题
     * -先用默认改写做归一化，再进行多问句拆分。
     */
    private RewriteResult rewriteAndSplit(String userQuestion) {
        // 开关关闭：直接做规则归一化 + 规则拆分
        if (!ragRewriteProperties.getQueryRewriteEnabled()) {
            String normalized = queryTermMappingService.normalize(userQuestion);
            List<String> subs = ruleBasedSplit(normalized);
            return RewriteResult.success(normalized, subs);
        }
        //开关开启，归一化术语之后调用大模型拆分
        String normalizedQuestion = queryTermMappingService.normalize(userQuestion);

        return callLLMRewriteAndSplit(normalizedQuestion, userQuestion, List.of(), true);
    }

    /**
     * 调用大模型完成统一改写，并在调用或解析失败时回退到归一化问题。
     *
     * @param normalizedQuestion 术语归一化后的问题
     * @param originalQuestion   用户原始问题，仅用于日志定位
     * @param history            本次提供给模型的历史
     * @param historyComplete    历史是否已经完整扩展
     * @return 可直接进入意图识别的改写结果
     */
    private RewriteResult callLLMRewriteAndSplit(String normalizedQuestion,
                                                 String originalQuestion,
                                                 List<ChatMessage> history,
                                                 boolean historyComplete) {
        String systemPrompt = promptTemplateLoader.load(QUERY_REWRITE_AND_SPLIT_PROMPT_PATH);
        ChatRequest req = buildRewriteRequest(systemPrompt, normalizedQuestion, history, historyComplete);

        try {
            String raw = llmService.chat(req);
            RewriteResult parsed = parseRewriteAndSplit(raw);

            if (parsed != null) {
                log.info("""
                        RAG用户问题查询改写+拆分：
                        原始问题：{}
                        归一化后：{}
                        改写结果：{}
                        子问题：{}
                        """, originalQuestion, normalizedQuestion, parsed.rewrittenQuestion(), parsed.subQuestions());
                return parsed;
            }

            log.warn("查询改写+拆分解析失败，使用归一化问题兜底 - normalizedQuestion={}", normalizedQuestion);
        } catch (Exception e) {
            log.warn("查询改写+拆分 LLM 调用失败，使用归一化问题兜底 - question={}，normalizedQuestion={}", originalQuestion, normalizedQuestion, e);
        }

        // 统一兜底逻辑
        return RewriteResult.success(normalizedQuestion, List.of(normalizedQuestion));
    }

    /**
     * 构架重写请求
     * @param systemPrompt 提示词
     * @param question 问题
     * @param history 历史消息（问答都包含）
     * @param historyComplete 历史是否已经完整扩展
     * @return 关闭随机采样并携带历史范围声明的改写请求
     */
    private ChatRequest buildRewriteRequest(String systemPrompt,
                                            String question,
                                            List<ChatMessage> history,
                                            boolean historyComplete) {
        List<ChatMessage> messages = new ArrayList<>();
        //体统提示词不为空，添加到消息当中
        if (StrUtil.isNotBlank(systemPrompt)) {
            messages.add(ChatMessage.system(systemPrompt));
        }

        if (CollUtil.isNotEmpty(history)) {
            // 摘要和会话偏好属于系统级记忆，完整保留在普通对话历史之前。
            history.stream()
                    .filter(msg -> msg.getRole() == ChatMessage.Role.SYSTEM)
                    .forEach(messages::add);

            // 普通问答只取最近配置窗口，控制改写调用的上下文长度。
            List<ChatMessage> conversationalHistory = history.stream()
                    .filter(msg -> msg.getRole() == ChatMessage.Role.USER
                            || msg.getRole() == ChatMessage.Role.ASSISTANT)
                    .toList();
            int maxMessages = Math.max(
                    1,
                    java.util.Optional.ofNullable(ragRewriteProperties.getQueryRewriteMaxHistoryMessages())
                            .orElse(4)
            );
            List<ChatMessage> recentHistory = conversationalHistory.stream()
                    .skip(Math.max(0, conversationalHistory.size() - maxMessages))
                    .toList();
            recentHistory = trimByCharacters(
                    recentHistory,
                    java.util.Optional.ofNullable(ragRewriteProperties.getQueryRewriteMaxHistoryChars())
                            .orElse(500)
            );
            messages.addAll(recentHistory);
        }

        // 明确历史范围，防止模型在已经完成扩展后再次要求无意义的历史回溯。
        messages.add(ChatMessage.system(historyComplete
                ? "已提供摘要水位线之后的全部可用历史；不得再次因历史窗口不足请求回溯。"
                : "当前只提供最近历史；只有确实无法完成指代消解时才返回 NEED_MORE_CONTEXT。"));
        messages.add(ChatMessage.user(question));

        return ChatRequest.builder()
                .messages(messages)
                // 改写结果参与后续路由，使用零温度保证同一上下文尽量产生一致结构。
                .temperature(0D)
                .topP(0.3D)
                .thinking(false)
                .build();
    }


    /**
     * LLM重写的问题响应拆分并转换为RewriteResult对象
     * @param raw LLM生成的原始回答
     */
    private RewriteResult parseRewriteAndSplit(String raw) {
        try {
            // 移除可能存在的 Markdown 代码块标记
            String cleaned = LLMResponseCleaner.stripMarkdownCodeFence(raw);
            
            // 使用宽松模式解析 JSON，容忍轻微格式错误
            JsonElement root;
            try {
                root = JsonParser.parseString(cleaned);
            } catch (com.google.gson.JsonSyntaxException e) {
                log.warn("JSON 解析失败，尝试宽松模式 - raw={}", raw);
                // 宽松模式：允许不规范的 JSON
                com.google.gson.Gson gson = new com.google.gson.GsonBuilder()
                    .setLenient()
                    .create();
                root = gson.fromJson(cleaned, JsonElement.class);
            }
            
            if (!root.isJsonObject()) {
                return null;
            }
            JsonObject obj = root.getAsJsonObject();
            // status 决定后续是正常进入意图识别，还是回源加载更完整历史。
            RewriteStatus status = parseStatus(obj);
            String rewrite = obj.has("rewrite") ? obj.get("rewrite").getAsString().trim() : "";
            List<String> subs = parseStringArray(obj, "sub_questions");
            List<String> currentConstraints = parseStringArray(obj, "current_constraints");
            List<String> persistentPreferences = parseStringArray(obj, "persistent_preferences");
            if (status == RewriteStatus.SUCCESS && StrUtil.isBlank(rewrite)) {
                return null;
            }
            if (status == RewriteStatus.NEED_MORE_CONTEXT && StrUtil.isBlank(rewrite)) {
                rewrite = "";
            }
            if (CollUtil.isEmpty(subs)) {
                subs = StrUtil.isBlank(rewrite) ? List.of() : List.of(rewrite);
            }
            return new RewriteResult(
                    status,
                    rewrite,
                    subs,
                    currentConstraints,
                    persistentPreferences
            );
        } catch (Exception e) {
            log.warn("解析改写+拆分结果失败，raw={}", raw, e);
            return null;
        }
    }

    /**
     * 解析改写状态，并兼容尚未返回 status 字段的旧模型输出。
     *
     * @param obj 改写模型返回的 JSON 对象
     * @return 合法状态；缺失或非法时按旧协议回退为 SUCCESS
     */
    private RewriteStatus parseStatus(JsonObject obj) {
        if (!obj.has("status") || obj.get("status").isJsonNull()) {
            return RewriteStatus.SUCCESS;
        }
        try {
            return RewriteStatus.valueOf(obj.get("status").getAsString().trim().toUpperCase());
        } catch (Exception ex) {
            return RewriteStatus.SUCCESS;
        }
    }

    /**
     * 提取并去重指定字符串数组字段。
     *
     * @param obj   改写模型返回的 JSON 对象
     * @param field 待提取字段名
     * @return 去除空值和重复项后的不可变列表
     */
    private List<String> parseStringArray(JsonObject obj, String field) {
        if (!obj.has(field) || !obj.get(field).isJsonArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        JsonArray array = obj.getAsJsonArray(field);
        for (JsonElement element : array) {
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                String value = element.getAsString().trim();
                if (StrUtil.isNotBlank(value) && !values.contains(value)) {
                    values.add(value);
                }
            }
        }
        return List.copyOf(values);
    }

    /**
     * 从最新消息向前保留不超过字符预算的会话历史。
     *
     * @param history       已按时间正序排列的普通问答历史
     * @param maxCharacters 最大字符预算
     * @return 保持原时间顺序的截断结果
     */
    private List<ChatMessage> trimByCharacters(List<ChatMessage> history, int maxCharacters) {
        if (history.isEmpty() || maxCharacters <= 0) {
            return List.of();
        }
        List<ChatMessage> reversed = new ArrayList<>();
        int used = 0;
        for (int index = history.size() - 1; index >= 0; index--) {
            ChatMessage message = history.get(index);
            int length = StrUtil.length(message.getContent());
            if (!reversed.isEmpty() && used + length > maxCharacters) {
                break;
            }
            reversed.add(message);
            used += length;
        }
        java.util.Collections.reverse(reversed);
        return List.copyOf(reversed);
    }

    /**
     * 兜底：按照常见标点符号拆分之后提取问句
     */
    private List<String> ruleBasedSplit(String question) {
        // 兜底：按常见分隔符拆分
        List<String> parts = Arrays.stream(question.split("[?？。；;\\n]+"))
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toList());

        if (CollUtil.isEmpty(parts)) {
            return List.of(question);
        }
        return parts.stream()
                .map(s -> s.endsWith("？") || s.endsWith("?") ? s : s + "？")
                .toList();
    }
}
