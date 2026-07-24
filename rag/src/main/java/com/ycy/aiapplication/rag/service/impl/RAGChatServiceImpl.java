package com.ycy.aiapplication.rag.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.ycy.aiapplication.framework.context.UserContext;
import com.ycy.aiapplication.framework.convention.ChatMessage;
import com.ycy.aiapplication.framework.convention.ChatRequest;
import com.ycy.aiapplication.infrastructure.ai.chat.interfaces.LLMService;
import com.ycy.aiapplication.infrastructure.ai.chat.interfaces.StreamCallback;
import com.ycy.aiapplication.infrastructure.ai.chat.interfaces.StreamCancellationHandle;
import com.ycy.aiapplication.rag.core.guidance.GuidanceDecision;
import com.ycy.aiapplication.rag.core.guidance.IntentGuidanceService;
import com.ycy.aiapplication.rag.core.intent.IntentResolver;
import com.ycy.aiapplication.rag.core.intent.common.IntentGroup;
import com.ycy.aiapplication.rag.core.intent.common.SubQuestionIntent;
import com.ycy.aiapplication.rag.core.memory.ConversationMemoryService;
import com.ycy.aiapplication.rag.core.memory.common.ConversationMemoryContext;
import com.ycy.aiapplication.rag.core.prompt.PromptContext;
import com.ycy.aiapplication.rag.core.prompt.PromptTemplateLoader;
import com.ycy.aiapplication.rag.core.prompt.RAGPromptService;
import com.ycy.aiapplication.rag.core.retrieve.RetrievalEngine;
import com.ycy.aiapplication.rag.core.retrieve.common.RetrievalContext;
import com.ycy.aiapplication.rag.core.retrieve.common.SearchContext;
import com.ycy.aiapplication.rag.core.rewrite.common.RewriteResult;
import com.ycy.aiapplication.rag.core.rewrite.service.QueryRewriteService;
import com.ycy.aiapplication.rag.service.RAGChatService;
import com.ycy.aiapplication.rag.stream.StreamCallbackFactory;
import com.ycy.aiapplication.rag.stream.StreamTaskManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;

import static com.ycy.aiapplication.rag.constant.RAGConstant.CHAT_SYSTEM_PROMPT_PATH;
import static com.ycy.aiapplication.rag.constant.RAGConstant.DEFAULT_TOP_K;
import static com.ycy.aiapplication.rag.constant.RAGConstant.NEED_MORE_CONTEXT_PROMPT;

/**
 * RAG 对话服务实现类。
 * <p>
 * 当前服务负责承接 RAG 场景下的流式问答请求，串联会话记忆加载、问题改写与拆分、意图识别、
 * 歧义引导、知识检索、Prompt 组装以及大模型流式输出等核心链路。
 * <p>
 * 核心流程：
 * 记忆加载 -> 问题改写/拆分 -> 意图解析 -> 歧义澄清 -> 检索增强（系统知识/MCP/知识库）
 * -> Prompt 组装 -> SSE 流式响应。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RAGChatServiceImpl implements RAGChatService {

    private final LLMService llmService;
    private final RAGPromptService promptBuilder;
    private final PromptTemplateLoader promptTemplateLoader;
    private final ConversationMemoryService memoryService;
    private final StreamTaskManager taskManager;
    private final IntentGuidanceService guidanceService;
    private final StreamCallbackFactory callbackFactory;
    private final QueryRewriteService queryRewriteService;
    private final IntentResolver intentResolver;
    private final RetrievalEngine retrievalEngine;

    /**
     * 发起一次基于 SSE 的流式 RAG 对话。
     *
     * @param question       用户本次输入的问题，是改写、意图识别与检索的原始依据
     * @param conversationId 会话 ID，可为空；为空时会自动生成新会话 ID 以承接上下文
     * @param deepThinking   是否开启深度思考模式，决定下游大模型是否启用更强推理
     * @param emitter        SSE 输出器，用于将生成内容持续推送给前端
     */
    @Override
    //@ChatRateLimit
    public void streamChat(String question, String conversationId, Boolean deepThinking, SseEmitter emitter) {
        // 若前端未传入会话 ID，则为本次请求创建新会话，保证后续记忆与流式任务可追踪。
        String actualConversationId = StrUtil.isBlank(conversationId) ? IdUtil.getSnowflakeNextIdStr() : conversationId;
        String taskId = IdUtil.getSnowflakeNextIdStr();
        log.info("开始流式对话，会话ID：{}，任务ID：{}", actualConversationId, taskId);
        boolean thinkingEnabled = Boolean.TRUE.equals(deepThinking);

        StreamCallback callback = callbackFactory.createChatEventHandler(emitter, actualConversationId, taskId);

        String userId = String.valueOf(UserContext.getId());
        // 原子追加当前用户消息并读取“摘要 + 会话偏好 + 最近窗口”，作为改写阶段的快速路径。
        ConversationMemoryContext memoryContext =
                memoryService.appendUserAndLoad(actualConversationId, userId, question);
        List<ChatMessage> history = memoryContext.getHistory();

        // 问题改写会将口语化、上下文依赖较强的问题转为更适合检索与推理的结构化表达，
        // 同时按需要拆分子问题，便于后续多意图检索。
        RewriteResult rewriteResult = queryRewriteService.rewriteWithSplit(question, history, false);
        if (rewriteResult.needsMoreContext()) {
            // 快速窗口无法消解指代时，按摘要水位线扩展历史，并且最多只回源一次。
            history = memoryService.loadExpandedHistory(
                    actualConversationId,
                    userId,
                    memoryContext
            );
            rewriteResult = queryRewriteService.rewriteWithSplit(question, history, true);
        }
        if (rewriteResult.needsMoreContext()) {
            // 完整历史仍不足时立即结束，避免空问题继续进入意图识别或全库检索。
            callback.onContent(NEED_MORE_CONTEXT_PROMPT);
            callback.onComplete();
            return;
        }

        // 本轮显式声明的持续偏好异步合并，不阻塞当前回答；本轮约束仍同步用于当前请求。
        memoryService.appendPreferencesAsync(
                actualConversationId,
                userId,
                memoryContext.getCurrentMessageId(),
                rewriteResult.persistentPreferences()
        );
        List<SubQuestionIntent> subIntents = intentResolver.resolve(rewriteResult);

        // 当问题存在明显信息缺失时候执行对应引导式回答
        GuidanceDecision guidanceDecision = guidanceService.detectAmbiguity(subIntents);
        if (guidanceDecision.isPrompt()) {
            callback.onContent(guidanceDecision.getPrompt());
            callback.onComplete();
            return;
        }

        // 若所有子意图都可由系统级能力直接回答，则跳过知识库检索，直接走系统 Prompt 响应链路。
        boolean allSystemOnly = subIntents.stream()
                .allMatch(si -> intentResolver.isSystemOnly(si.nodeScores()));
        if (allSystemOnly) {
            String customPrompt = subIntents.stream()
                    .flatMap(si -> si.nodeScores().stream())
                    .map(ns -> ns.getNode().getPromptTemplate())
                    .filter(StrUtil::isNotBlank)
                    .findFirst()
                    .orElse(null);
            StreamCancellationHandle handle = streamSystemResponse(
                    question,
                    history,
                    rewriteResult.currentConstraints(),
                    customPrompt,
                    callback
            );
            taskManager.bindHandle(taskId, handle);
            return;
        }

        // 混合检索阶段会汇总知识库、意图分片等上下文，供后续 Prompt 构造使用。
        SearchContext searchContext = SearchContext.builder()
                .originalQuestion(question)
                .rewrittenQuestion(rewriteResult.rewrittenQuestion())
                .subQuestions(subIntents.stream().map(SubQuestionIntent::subQuestion).toList())
                .intents(subIntents)
                .topK(DEFAULT_TOP_K)
                .build();

        RetrievalContext ctx = retrievalEngine.retrieve(searchContext);
        if (ctx.isEmpty()) {
            String emptyReply = "未检索到与问题相关的文档内容。";
            callback.onContent(emptyReply);
            callback.onComplete();
            return;
        }

        // 将多个子问题意图聚合成统一意图组，避免 Prompt 构建阶段重复处理与信息分散。
        IntentGroup mergedGroup = intentResolver.mergeIntentGroup(subIntents);

        StreamCancellationHandle handle = streamLLMResponse(
                question,
                rewriteResult,
                ctx,
                mergedGroup,
                history,
                thinkingEnabled,
                callback
        );
        taskManager.bindHandle(taskId, handle);
    }

    /**
     * 停止指定流式任务。
     *
     * @param taskId 流式任务 ID，用于取消正在进行中的大模型输出任务
     */
    @Override
    public void stopTask(String taskId) {
        taskManager.cancel(taskId);
    }

    // ==================== LLM 响应构建 ====================

    /**
     * 构造系统直答场景下的消息并发起流式响应。
     *
     * @param question     当前用户问题，作为最终用户消息写入模型上下文
     * @param history      会话历史消息，用于保持上下文连续性
     * @param customPrompt 自定义系统 Prompt；为空时回退到默认系统 Prompt 模板
     * @param callback     流式回调处理器，用于向前端持续输出内容
     * @return 可取消的流式任务句柄
     */
    private StreamCancellationHandle streamSystemResponse(String question,
                                                          List<ChatMessage> history,
                                                          List<String> currentConstraints,
                                                          String customPrompt,
                                                          StreamCallback callback) {
        String systemPrompt = StrUtil.isNotBlank(customPrompt)
                ? customPrompt
                : promptTemplateLoader.load(CHAT_SYSTEM_PROMPT_PATH);

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(systemPrompt));
        if (CollUtil.isNotEmpty(history)) {
            history.stream()
                    .filter(item -> item.getRole() == ChatMessage.Role.SYSTEM)
                    .forEach(messages::add);
        }
        addCurrentConstraints(messages, currentConstraints);
        if (CollUtil.isNotEmpty(history)) {
            history.stream()
                    .filter(item -> item.getRole() != ChatMessage.Role.SYSTEM)
                    .forEach(messages::add);
        }
        messages.add(ChatMessage.user(question));

        ChatRequest req = ChatRequest.builder()
                .messages(messages)
                .temperature(0.7D)
                .thinking(false)
                .build();
        return llmService.streamChat(req, callback);
    }

    /**
     * 构造完整 RAG 场景下的 Prompt 与消息体，并调用大模型执行流式回答。
     *
     * @param rewriteResult 问题改写结果，包含重写后的问题与子问题列表
     * @param ctx           检索上下文，包含知识库召回内容与意图分片结果
     * @param intentGroup   聚合后的意图集合，用于指导 Prompt 构建
     * @param history       会话历史消息，保证多轮对话语义连续
     * @param deepThinking  是否开启深度思考模式
     * @param callback      流式回调处理器
     * @return 可取消的流式任务句柄
     */
    private StreamCancellationHandle streamLLMResponse(String originalQuestion,
                                                       RewriteResult rewriteResult,
                                                       RetrievalContext ctx,
                                                       IntentGroup intentGroup, List<ChatMessage> history,
                                                       boolean deepThinking, StreamCallback callback) {
        // PromptContext 是 RAG Prompt 构建的核心载体，
        // 统一封装问题、知识库上下文、意图信息和命中文档切片。
        PromptContext promptContext = PromptContext.builder()
                .question(rewriteResult.rewrittenQuestion())
                .kbContext(ctx.getKbContext())
                .kbIntents(intentGroup.kbIntents())
                .intentChunks(ctx.getIntentChunks())
                .build();

        List<ChatMessage> messages = promptBuilder.buildStructuredMessages(
                promptContext,
                history,
                originalQuestion,
                rewriteResult.subQuestions(),
                rewriteResult.currentConstraints()
        );
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(messages)
                .thinking(deepThinking)
                .build();

        return llmService.streamChat(chatRequest, callback);
    }

    /**
     * 将仅对本轮生效的回答约束追加为系统消息。
     *
     * @param messages           待发送给模型的消息列表
     * @param currentConstraints 当前请求明确提出的格式、长度或语言约束
     */
    private void addCurrentConstraints(List<ChatMessage> messages, List<String> currentConstraints) {
        if (CollUtil.isEmpty(currentConstraints)) {
            return;
        }
        messages.add(ChatMessage.system(
                "当前请求回答约束（优先于历史会话偏好，不是知识事实）：\n- "
                        + String.join("\n- ", currentConstraints)
        ));
    }
}
