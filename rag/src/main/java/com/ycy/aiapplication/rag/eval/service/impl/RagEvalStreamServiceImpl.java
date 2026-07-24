package com.ycy.aiapplication.rag.eval.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.ycy.aiapplication.framework.convention.ChatMessage;
import com.ycy.aiapplication.framework.convention.ChatRequest;
import com.ycy.aiapplication.framework.convention.RetrievedChunk;
import com.ycy.aiapplication.framework.web.SseEmitterSender;
import com.ycy.aiapplication.infrastructure.ai.chat.interfaces.LLMService;
import com.ycy.aiapplication.infrastructure.ai.chat.interfaces.StreamCancellationHandle;
import com.ycy.aiapplication.infrastructure.ai.chat.interfaces.StreamCallback;
import com.ycy.aiapplication.rag.constant.RAGConstant;
import com.ycy.aiapplication.rag.core.guidance.GuidanceDecision;
import com.ycy.aiapplication.rag.core.guidance.IntentGuidanceService;
import com.ycy.aiapplication.rag.core.intent.IntentResolver;
import com.ycy.aiapplication.rag.core.intent.common.IntentGroup;
import com.ycy.aiapplication.rag.core.intent.common.SubQuestionIntent;
import com.ycy.aiapplication.rag.core.prompt.PromptContext;
import com.ycy.aiapplication.rag.core.prompt.PromptTemplateLoader;
import com.ycy.aiapplication.rag.core.prompt.RAGPromptService;
import com.ycy.aiapplication.rag.core.retrieve.RetrievalEngine;
import com.ycy.aiapplication.rag.core.retrieve.common.RetrievalContext;
import com.ycy.aiapplication.rag.core.retrieve.common.SearchContext;
import com.ycy.aiapplication.rag.core.rewrite.common.RewriteResult;
import com.ycy.aiapplication.rag.core.rewrite.service.QueryRewriteService;
import com.ycy.aiapplication.rag.eval.dto.RagEvalResponse;
import com.ycy.aiapplication.rag.eval.dto.RagEvalStreamEvents;
import com.ycy.aiapplication.rag.eval.intent.RagEvalIntentMode;
import com.ycy.aiapplication.rag.eval.intent.RagEvalIntentRouter;
import com.ycy.aiapplication.rag.eval.service.RagEvalChunkMetadataResolver;
import com.ycy.aiapplication.rag.eval.service.RagEvalStreamService;
import com.ycy.aiapplication.rag.eval.stream.RagEvalStreamCallback;
import com.ycy.aiapplication.rag.eval.trace.RagEvalTraceContext;
import com.ycy.aiapplication.rag.eval.trace.RagEvalTraceWriter;
import com.ycy.aiapplication.rag.stream.common.MessageDelta;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static com.ycy.aiapplication.rag.constant.RAGConstant.CHAT_SYSTEM_PROMPT_PATH;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.rag-eval.enabled", havingValue = "true")
public class RagEvalStreamServiceImpl implements RagEvalStreamService {

    private final QueryRewriteService queryRewriteService;
    private final IntentResolver intentResolver;
    private final RagEvalIntentRouter evalIntentRouter;
    private final IntentGuidanceService guidanceService;
    private final RetrievalEngine retrievalEngine;
    private final RAGPromptService promptBuilder;
    private final PromptTemplateLoader promptTemplateLoader;
    private final LLMService llmService;
    private final RagEvalChunkMetadataResolver metadataResolver;
    private final RagEvalTraceWriter traceWriter;

    @Override
    public SseEmitter stream(
            String question,
            int topK,
            boolean deepThinking,
            RagEvalIntentMode intentMode,
            RetrievalEngine.RerankMode rerankMode,
            double rerankKeepRatio,
            String requestedTraceId,
            String runId,
            String queryId) {
        SseEmitter emitter = new SseEmitter(0L);
        SseEmitterSender sender = new SseEmitterSender(emitter);
        AtomicReference<StreamCancellationHandle> handleRef = new AtomicReference<>();
        emitter.onTimeout(() -> cancelQuietly(handleRef.get()));
        emitter.onError(error -> cancelQuietly(handleRef.get()));

        String traceId = StrUtil.blankToDefault(requestedTraceId, IdUtil.fastSimpleUUID());
        String taskId = IdUtil.getSnowflakeNextIdStr();
        int resolvedTopK = topK > 0 ? topK : RAGConstant.DEFAULT_TOP_K;
        RagEvalIntentMode resolvedIntentMode = intentMode == null ? RagEvalIntentMode.COMBINED : intentMode;
        RetrievalEngine.RerankMode resolvedRerankMode = rerankMode == null
                ? RetrievalEngine.RerankMode.PER_CHANNEL
                : rerankMode;
        long totalStartedAt = System.nanoTime();
        Map<String, Long> timings = new LinkedHashMap<>();

        try (RagEvalTraceContext.Scope ignored = RagEvalTraceContext.open(traceId, runId, queryId)) {
            sender.sendEvent("meta", new RagEvalStreamEvents.MetaEvent(
                    traceId,
                    runId,
                    queryId,
                    taskId,
                    resolvedIntentMode.value(),
                    resolvedRerankMode.name().toLowerCase(),
                    rerankKeepRatio));
            traceWriter.write("eval.stream.started", details(
                    "question", question,
                    "topK", resolvedTopK,
                    "deepThinking", deepThinking,
                    "intentMode", resolvedIntentMode.value(),
                    "rerankMode", resolvedRerankMode.name().toLowerCase(),
                    "rerankKeepRatio", rerankKeepRatio));

            long rewriteStartedAt = System.nanoTime();
            RewriteResult rewriteResult = queryRewriteService.rewriteWithSplit(question, List.of());
            timings.put("rewriteMs", elapsedMs(rewriteStartedAt));
            if (rewriteResult.needsMoreContext()) {
                // 无会话历史的评测问题无法消解指代时直接引导，不再进入意图识别和知识库检索。
                timings.put("guidanceMs", 0L);
                sendRetrievalEvent(sender, traceId, "GUIDANCE", List.of(), List.of(), timings, List.of());
                sendDirectResponse(sender, traceId, runId, queryId, timings, totalStartedAt,
                        RAGConstant.NEED_MORE_CONTEXT_PROMPT);
                return emitter;
            }

            long intentStartedAt = System.nanoTime();
            List<SubQuestionIntent> subIntents = evalIntentRouter.resolve(rewriteResult, resolvedIntentMode);
            timings.put("intentMs", elapsedMs(intentStartedAt));

            long guidanceStartedAt = System.nanoTime();
            GuidanceDecision guidanceDecision = guidanceService.detectAmbiguity(subIntents);
            timings.put("guidanceMs", elapsedMs(guidanceStartedAt));
            if (guidanceDecision.isPrompt()) {
                sendRetrievalEvent(sender, traceId, "GUIDANCE", List.of(), List.of(), timings, List.of());
                sendDirectResponse(sender, traceId, runId, queryId, timings, totalStartedAt, guidanceDecision.getPrompt());
                return emitter;
            }

            boolean allSystemOnly = subIntents.stream()
                    .allMatch(intent -> intentResolver.isSystemOnly(intent.nodeScores()));
            if (allSystemOnly) {
                sendRetrievalEvent(sender, traceId, "SYSTEM", List.of(), List.of(), timings, List.of());
                ChatRequest chatRequest = buildSystemRequest(rewriteResult, subIntents);
                sendPromptEvent(sender, traceId, chatRequest, timings);
                StreamCallback callback = callback(sender, traceId, runId, queryId, timings, totalStartedAt);
                handleRef.set(llmService.streamChat(chatRequest, callback));
                return emitter;
            }

            SearchContext searchContext = SearchContext.builder()
                    .originalQuestion(question)
                    .rewrittenQuestion(rewriteResult.rewrittenQuestion())
                    .subQuestions(subIntents.stream().map(SubQuestionIntent::subQuestion).toList())
                    .intents(subIntents)
                    .topK(resolvedTopK)
                    .build();

            long retrievalStartedAt = System.nanoTime();
            RetrievalContext retrievalContext = retrievalEngine.retrieve(
                    searchContext, resolvedRerankMode, rerankKeepRatio);
            timings.put("retrievalMs", elapsedMs(retrievalStartedAt));
            List<RetrievedChunk> finalChunks = flattenFinalChunks(retrievalContext);
            RagEvalChunkMetadataResolver.Metadata metadata = metadataResolver.resolve(
                    retrievalContext.getChannelResults(), finalChunks);
            List<RagEvalResponse.ChunkView> chunks = metadataResolver.buildChunkViews(finalChunks, metadata, true);
            List<RagEvalResponse.ChannelView> channels = metadataResolver.buildChannelViews(
                    retrievalContext.getChannelResults(), metadata, true);
            String route = retrievalContext.isEmpty() ? "KB_EMPTY" : "KB";
            List<RagEvalStreamEvents.ChunkContentDetail> chunkContents = buildChunkContentDetails(finalChunks);
            sendRetrievalEvent(sender, traceId, route, chunks, channels, timings, chunkContents);
            if (retrievalContext.isEmpty()) {
                sendDirectResponse(sender, traceId, runId, queryId, timings, totalStartedAt,
                        "未检索到与问题相关的文档内容。");
                return emitter;
            }

            long promptStartedAt = System.nanoTime();
            IntentGroup mergedGroup = intentResolver.mergeIntentGroup(subIntents);
            PromptContext promptContext = PromptContext.builder()
                    .question(rewriteResult.rewrittenQuestion())
                    .kbContext(retrievalContext.getKbContext())
                    .kbIntents(mergedGroup.kbIntents())
                    .intentChunks(retrievalContext.getIntentChunks())
                    .build();
            List<ChatMessage> messages = promptBuilder.buildStructuredMessages(
                    promptContext,
                    List.of(),
                    rewriteResult.rewrittenQuestion(),
                    rewriteResult.subQuestions(),
                    List.of());
            timings.put("promptMs", elapsedMs(promptStartedAt));

            ChatRequest chatRequest = ChatRequest.builder()
                    .messages(messages)
                    .thinking(deepThinking)
                    .build();
            sendPromptEvent(sender, traceId, chatRequest, timings);
            StreamCallback callback = callback(sender, traceId, runId, queryId, timings, totalStartedAt);
            handleRef.set(llmService.streamChat(chatRequest, callback));
            return emitter;
        } catch (RuntimeException exception) {
            traceWriter.write("eval.stream.failed", details(
                    "question", question,
                    "error", exception.toString()));
            sendError(sender, timings, elapsedMs(totalStartedAt), exception);
            return emitter;
        }
    }

    private ChatRequest buildSystemRequest(RewriteResult rewriteResult, List<SubQuestionIntent> subIntents) {
        String customPrompt = subIntents.stream()
                .filter(intent -> intent.nodeScores() != null)
                .flatMap(intent -> intent.nodeScores().stream())
                .filter(nodeScore -> nodeScore.getNode() != null)
                .map(nodeScore -> nodeScore.getNode().getPromptTemplate())
                .filter(StrUtil::isNotBlank)
                .findFirst()
                .orElse(null);
        String systemPrompt = StrUtil.isNotBlank(customPrompt)
                ? customPrompt
                : promptTemplateLoader.load(CHAT_SYSTEM_PROMPT_PATH);
        return ChatRequest.builder()
                .messages(List.of(
                        ChatMessage.system(systemPrompt),
                        ChatMessage.user(rewriteResult.rewrittenQuestion())))
                .temperature(0.7D)
                .thinking(false)
                .build();
    }

    private StreamCallback callback(
            SseEmitterSender sender,
            String traceId,
            String runId,
            String queryId,
            Map<String, Long> timings,
            long totalStartedAt) {
        return new RagEvalStreamCallback(
                sender,
                traceWriter,
                traceId,
                runId,
                queryId,
                timings,
                () -> elapsedMs(totalStartedAt));
    }

    private void sendRetrievalEvent(
            SseEmitterSender sender,
            String traceId,
            String route,
            List<RagEvalResponse.ChunkView> chunks,
            List<RagEvalResponse.ChannelView> channels,
            Map<String, Long> timings,
            List<RagEvalStreamEvents.ChunkContentDetail> chunkContents) {
        traceWriter.write("eval.stream.retrieval", details(
                "route", route,
                "chunkCount", chunks.size(),
                "metadataResolvedCount", chunks.stream().filter(chunk -> StrUtil.isNotBlank(chunk.docId())).count()));
        sender.sendEvent("retrieval", new RagEvalStreamEvents.RetrievalEvent(
                traceId,
                route,
                chunks,
                channels,
                new LinkedHashMap<>(timings),
                chunkContents));
    }

    private void sendPromptEvent(
            SseEmitterSender sender,
            String traceId,
            ChatRequest chatRequest,
            Map<String, Long> timings) {
        List<ChatMessage> messages = chatRequest.getMessages() == null
                ? List.of()
                : chatRequest.getMessages();
        List<RagEvalStreamEvents.PromptMessageView> messageViews = messages.stream()
                .map(message -> new RagEvalStreamEvents.PromptMessageView(
                        message.getRole() == null ? null : message.getRole().name().toLowerCase(),
                        message.getContent()))
                .toList();
        int totalChars = messages.stream()
                .map(ChatMessage::getContent)
                .filter(content -> content != null)
                .mapToInt(String::length)
                .sum();
        RagEvalStreamEvents.ChatRequestView requestView = new RagEvalStreamEvents.ChatRequestView(
                chatRequest.getProvider(),
                chatRequest.getModelId(),
                chatRequest.getTemperature(),
                chatRequest.getTopP(),
                chatRequest.getTopK(),
                chatRequest.getMaxTokens(),
                chatRequest.getThinking(),
                chatRequest.getEnableTools());
        traceWriter.write("eval.stream.prompt.finalized", details(
                "messageCount", messageViews.size(),
                "totalChars", totalChars,
                "provider", chatRequest.getProvider(),
                "modelId", chatRequest.getModelId(),
                "temperature", chatRequest.getTemperature(),
                "topP", chatRequest.getTopP(),
                "topK", chatRequest.getTopK(),
                "maxTokens", chatRequest.getMaxTokens(),
                "thinking", chatRequest.getThinking(),
                "enableTools", chatRequest.getEnableTools(),
                "timings", new LinkedHashMap<>(timings)));
        sender.sendEvent("prompt", new RagEvalStreamEvents.PromptEvent(
                traceId,
                messageViews,
                messageViews.size(),
                totalChars,
                requestView,
                new LinkedHashMap<>(timings)));
    }

    private void sendDirectResponse(
            SseEmitterSender sender,
            String traceId,
            String runId,
            String queryId,
            Map<String, Long> timings,
            long totalStartedAt,
            String content) {
        String actualContent = StrUtil.blankToDefault(content, "");
        if (StrUtil.isNotBlank(actualContent)) {
            sender.sendEvent("message", new MessageDelta("response", actualContent));
        }
        Map<String, Long> finalTimings = new LinkedHashMap<>(timings);
        finalTimings.put("answerCompleteMs", elapsedMs(totalStartedAt));
        try (RagEvalTraceContext.Scope ignored = RagEvalTraceContext.open(traceId, runId, queryId)) {
            traceWriter.write("eval.stream.completed", details(
                    "status", "success",
                    "responseChars", actualContent.length(),
                    "timings", finalTimings));
        }
        sender.sendEvent("finish", new RagEvalStreamEvents.FinishEvent(
                "success",
                null,
                actualContent.length(),
                finalTimings));
        sender.sendEvent("done", "[DONE]");
        sender.complete();
    }

    private void sendError(
            SseEmitterSender sender,
            Map<String, Long> timings,
            long answerCompleteMs,
            RuntimeException exception) {
        Map<String, Long> finalTimings = new LinkedHashMap<>(timings);
        finalTimings.put("answerCompleteMs", answerCompleteMs);
        sender.sendEvent("finish", new RagEvalStreamEvents.FinishEvent(
                "error",
                exception.toString(),
                0,
                finalTimings));
        sender.sendEvent("done", "[DONE]");
        sender.complete();
    }

    private List<RetrievedChunk> flattenFinalChunks(RetrievalContext retrievalContext) {
        Map<String, RetrievedChunk> deduplicated = new LinkedHashMap<>();
        retrievalContext.getIntentChunks().values().stream()
                .flatMap(Collection::stream)
                .sorted(Comparator.comparing(
                        RetrievedChunk::getScore,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .forEach(chunk -> deduplicated.putIfAbsent(chunk.getId(), chunk));
        return new ArrayList<>(deduplicated.values());
    }

    private List<RagEvalStreamEvents.ChunkContentDetail> buildChunkContentDetails(List<RetrievedChunk> chunks) {
        List<RagEvalStreamEvents.ChunkContentDetail> details = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            RetrievedChunk chunk = chunks.get(i);
            details.add(new RagEvalStreamEvents.ChunkContentDetail(
                    chunk.getId(),
                    chunk.getText(),
                    chunk.getScore() != null ? chunk.getScore().doubleValue() : null,
                    i + 1));
        }
        return details;
    }

    private void cancelQuietly(StreamCancellationHandle handle) {
        if (handle != null) {
            handle.cancel();
        }
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private Map<String, Object> details(Object... keyValues) {
        Map<String, Object> details = new LinkedHashMap<>();
        for (int index = 0; index < keyValues.length; index += 2) {
            details.put((String) keyValues[index], keyValues[index + 1]);
        }
        return details;
    }
}
