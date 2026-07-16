package com.ycy.aiapplication.rag.eval.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.ycy.aiapplication.framework.convention.ChatMessage;
import com.ycy.aiapplication.framework.convention.ChatRequest;
import com.ycy.aiapplication.framework.convention.RetrievedChunk;
import com.ycy.aiapplication.infrastructure.ai.chat.interfaces.LLMService;
import com.ycy.aiapplication.infrastructure.ai.toolkit.LLMResponseCleaner;
import com.ycy.aiapplication.knowledge.dao.entity.KnowledgeChunkDO;
import com.ycy.aiapplication.knowledge.dao.entity.KnowledgeDocumentDO;
import com.ycy.aiapplication.knowledge.dao.mapper.KnowledgeChunkDOMapper;
import com.ycy.aiapplication.knowledge.dao.mapper.KnowledgeDocumentMapper;
import com.ycy.aiapplication.rag.constant.RAGConstant;
import com.ycy.aiapplication.rag.core.guidance.GuidanceDecision;
import com.ycy.aiapplication.rag.core.guidance.IntentGuidanceService;
import com.ycy.aiapplication.rag.core.intent.FirstLayerIntentDecision;
import com.ycy.aiapplication.rag.core.intent.IntentResolver;
import com.ycy.aiapplication.rag.core.intent.common.IntentGroup;
import com.ycy.aiapplication.rag.core.intent.common.SubQuestionIntent;
import com.ycy.aiapplication.rag.core.prompt.PromptContext;
import com.ycy.aiapplication.rag.core.prompt.RAGPromptService;
import com.ycy.aiapplication.rag.core.retrieve.RetrievalEngine;
import com.ycy.aiapplication.rag.core.retrieve.channel.SearchChannelResult;
import com.ycy.aiapplication.rag.core.retrieve.common.RetrievalContext;
import com.ycy.aiapplication.rag.core.retrieve.common.SearchContext;
import com.ycy.aiapplication.rag.core.rewrite.common.RewriteResult;
import com.ycy.aiapplication.rag.core.rewrite.service.QueryRewriteService;
import com.ycy.aiapplication.rag.eval.dto.RagEvalChunkProbeRequest;
import com.ycy.aiapplication.rag.eval.dto.RagEvalChunkProbeResponse;
import com.ycy.aiapplication.rag.eval.dto.RagEvalResponse;
import com.ycy.aiapplication.rag.eval.intent.RagEvalIntentMode;
import com.ycy.aiapplication.rag.eval.intent.RagEvalIntentRouter;
import com.ycy.aiapplication.rag.eval.service.RagEvalChunkMetadataResolver;
import com.ycy.aiapplication.rag.eval.service.RagEvalService;
import com.ycy.aiapplication.rag.eval.trace.RagEvalTraceContext;
import com.ycy.aiapplication.rag.eval.trace.RagEvalTraceWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.rag-eval.enabled", havingValue = "true")
public class RagEvalServiceImpl implements RagEvalService {

    private final QueryRewriteService queryRewriteService;
    private final IntentResolver intentResolver;
    private final RagEvalIntentRouter evalIntentRouter;
    private final IntentGuidanceService guidanceService;
    private final RetrievalEngine retrievalEngine;
    private final RAGPromptService promptBuilder;
    private final LLMService llmService;
    private final KnowledgeChunkDOMapper knowledgeChunkDOMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final RagEvalChunkMetadataResolver metadataResolver;
    private final RagEvalTraceWriter traceWriter;

    @Override
    public RagEvalResponse evaluate(
            String question,
            int topK,
            boolean includeContexts,
            RagEvalIntentMode intentMode,
            String requestedTraceId,
            String runId,
            String queryId) {
        String traceId = StrUtil.blankToDefault(requestedTraceId, IdUtil.fastSimpleUUID());
        int resolvedTopK = topK > 0 ? topK : RAGConstant.DEFAULT_TOP_K;
        RagEvalIntentMode resolvedIntentMode = intentMode == null ? RagEvalIntentMode.COMBINED : intentMode;

        try (RagEvalTraceContext.Scope ignored = RagEvalTraceContext.open(traceId, runId, queryId)) {
            long totalStartedAt = System.nanoTime();
            try {
                traceWriter.write("eval.request.started", details(
                        "question", question,
                        "topK", resolvedTopK,
                        "includeContexts", includeContexts,
                        "intentMode", resolvedIntentMode.value()));

                long rewriteStartedAt = System.nanoTime();
                RewriteResult rewriteResult = queryRewriteService.rewriteWithSplit(question, List.of());
                long rewriteMs = elapsedMs(rewriteStartedAt);
                traceWriter.write("eval.rewrite.completed", details(
                        "latencyMs", rewriteMs,
                        "rewrittenQuestion", rewriteResult.rewrittenQuestion(),
                        "subQuestions", rewriteResult.subQuestions()));

                long intentStartedAt = System.nanoTime();
                List<SubQuestionIntent> subIntents = evalIntentRouter.resolve(rewriteResult, resolvedIntentMode);
                long intentMs = elapsedMs(intentStartedAt);
                traceWriter.write("eval.intent.completed", details(
                        "latencyMs", intentMs,
                        "intentMode", resolvedIntentMode.value(),
                        "intents", buildIntentViews(subIntents)));

                long guidanceStartedAt = System.nanoTime();
                GuidanceDecision guidanceDecision = guidanceService.detectAmbiguity(subIntents);
                long guidanceMs = elapsedMs(guidanceStartedAt);
                traceWriter.write("eval.guidance.completed", details(
                        "latencyMs", guidanceMs,
                        "prompt", guidanceDecision.isPrompt(),
                        "guidancePrompt", guidanceDecision.getPrompt()));
                if (guidanceDecision.isPrompt()) {
                    return completeWithoutRetrieval(
                            traceId, runId, queryId, question, rewriteResult, subIntents, resolvedIntentMode,
                            "GUIDANCE", guidanceDecision.getPrompt(), totalStartedAt, rewriteMs, intentMs, guidanceMs);
                }

                boolean allSystem = subIntents.stream()
                        .allMatch(intent -> intentResolver.isSystemOnly(intent.nodeScores()));
                if (allSystem) {
                    return completeWithoutRetrieval(
                            traceId, runId, queryId, question, rewriteResult, subIntents, resolvedIntentMode,
                            "SYSTEM", null, totalStartedAt, rewriteMs, intentMs, guidanceMs);
                }

                SearchContext searchContext = SearchContext.builder()
                        .originalQuestion(question)
                        .rewrittenQuestion(rewriteResult.rewrittenQuestion())
                        .subQuestions(subIntents.stream().map(SubQuestionIntent::subQuestion).toList())
                        .intents(subIntents)
                        .topK(resolvedTopK)
                        .build();
                long retrievalStartedAt = System.nanoTime();
                RetrievalContext retrievalContext = retrievalEngine.retrieve(searchContext);
                long retrievalMs = elapsedMs(retrievalStartedAt);
                traceWriter.write("eval.retrieval.completed", details(
                        "latencyMs", retrievalMs,
                        "channelCount", retrievalContext.getChannelResults().size(),
                        "intentChunkGroups", retrievalContext.getIntentChunks().keySet()));

                List<RetrievedChunk> finalChunks = flattenFinalChunks(retrievalContext);
                RagEvalChunkMetadataResolver.Metadata metadata = metadataResolver.resolve(
                        retrievalContext.getChannelResults(), finalChunks);
                List<RagEvalResponse.ChunkView> chunks = metadataResolver.buildChunkViews(
                        finalChunks, metadata, includeContexts);
                List<RagEvalResponse.ChannelView> channels = metadataResolver.buildChannelViews(
                        retrievalContext.getChannelResults(), metadata, includeContexts);

                long promptStartedAt = System.nanoTime();
                int promptMessageCount = 0;
                if (!retrievalContext.isEmpty()) {
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
                            rewriteResult.subQuestions());
                    promptMessageCount = messages.size();
                }
                long promptMs = elapsedMs(promptStartedAt);

                traceWriter.write("eval.prompt.context.finalized", details(
                        "latencyMs", promptMs,
                        "messageCount", promptMessageCount,
                        "retrievedChunkIds", chunks.stream().map(RagEvalResponse.ChunkView::chunkId).toList(),
                        "retrievedDocIds", chunks.stream().map(RagEvalResponse.ChunkView::docId).toList(),
                        "retrievedContexts", includeContexts
                                ? chunks.stream().map(RagEvalResponse.ChunkView::text).toList()
                                : List.of()));

                long totalMs = elapsedMs(totalStartedAt);
                String route = retrievalContext.isEmpty() ? "KB_EMPTY" : "KB";
                RagEvalResponse response = response(
                        traceId, runId, queryId, question, rewriteResult, subIntents, resolvedIntentMode,
                        route, null, chunks, channels,
                        new RagEvalResponse.TimingView(totalMs, rewriteMs, intentMs, guidanceMs, retrievalMs, promptMs));
                traceWriter.write("eval.request.completed", details(
                        "route", route,
                        "latencyMs", totalMs,
                        "chunkCount", chunks.size()));
                return response;
            } catch (RuntimeException exception) {
                traceWriter.write("eval.request.failed", details(
                        "question", question,
                        "error", exception.toString()));
                throw exception;
            }
        }
    }

    @Override
    public RagEvalChunkProbeResponse generateChunkProbes(RagEvalChunkProbeRequest request) {
        if (request == null || StrUtil.isBlank(request.kbId())) {
            throw new IllegalArgumentException("kbId is required");
        }
        int limit = request.limit() == null || request.limit() <= 0 ? 100 : request.limit();
        int questionsPerChunk = request.questionsPerChunk() == null
                ? 1
                : Math.max(1, Math.min(3, request.questionsPerChunk()));
        int minChars = request.minChars() == null || request.minChars() <= 0 ? 80 : request.minChars();
        long seed = request.seed() == null ? System.currentTimeMillis() : request.seed();

        List<KnowledgeDocumentDO> documents = knowledgeDocumentMapper.selectList(
                Wrappers.lambdaQuery(KnowledgeDocumentDO.class)
                        .eq(KnowledgeDocumentDO::getKbId, request.kbId())
                        .eq(KnowledgeDocumentDO::getEnabled, 1)
                        .eq(KnowledgeDocumentDO::getStatus, "success")
                        .in(request.docIds() != null && !request.docIds().isEmpty(),
                                KnowledgeDocumentDO::getId,
                                request.docIds())
                        .orderByAsc(KnowledgeDocumentDO::getCreateTime));
        Map<String, KnowledgeDocumentDO> documentById = documents.stream()
                .collect(Collectors.toMap(
                        KnowledgeDocumentDO::getId,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new));
        if (documentById.isEmpty()) {
            return new RagEvalChunkProbeResponse(request.kbId(), limit, 0, seed, List.of());
        }

        List<KnowledgeChunkDO> chunks = knowledgeChunkDOMapper.selectList(
                Wrappers.lambdaQuery(KnowledgeChunkDO.class)
                        .eq(KnowledgeChunkDO::getKbId, request.kbId())
                        .eq(KnowledgeChunkDO::getEnabled, 1)
                        .in(KnowledgeChunkDO::getDocId, documentById.keySet())
                        .in(request.chunkIds() != null && !request.chunkIds().isEmpty(),
                                KnowledgeChunkDO::getId,
                                request.chunkIds())
                        .orderByAsc(KnowledgeChunkDO::getDocId)
                        .orderByAsc(KnowledgeChunkDO::getChunkIndex));

        List<KnowledgeChunkDO> candidates = chunks.stream()
                .filter(chunk -> isEligibleProbeChunk(chunk, minChars))
                .collect(Collectors.toCollection(ArrayList::new));
        Collections.shuffle(candidates, new Random(seed));
        if (candidates.size() > limit) {
            candidates = new ArrayList<>(candidates.subList(0, limit));
        }

        List<RagEvalChunkProbeResponse.ProbeSample> samples = new ArrayList<>();
        int index = 1;
        for (KnowledgeChunkDO chunk : candidates) {
            KnowledgeDocumentDO document = documentById.get(chunk.getDocId());
            if (document == null) {
                continue;
            }
            List<String> questions = generateQuestionsForChunk(chunk, document, questionsPerChunk);
            for (String question : questions) {
                samples.add(new RagEvalChunkProbeResponse.ProbeSample(
                        "CHUNK-%04d".formatted(index++),
                        question,
                        StrUtil.blankToDefault(document.getDocName(), "knowledge_chunk"),
                        "medium",
                        true,
                        List.of(chunk.getId()),
                        List.of(document.getId()),
                        List.of(document.getDocName()),
                        "KB",
                        chunk.getKbId(),
                        document.getId(),
                        document.getDocName(),
                        chunk.getChunkIndex(),
                        preview(chunk.getContent(), 240),
                        seed));
            }
        }
        return new RagEvalChunkProbeResponse(request.kbId(), limit, samples.size(), seed, samples);
    }

    private boolean isEligibleProbeChunk(KnowledgeChunkDO chunk, int minChars) {
        String content = chunk == null ? null : chunk.getContent();
        if (StrUtil.isBlank(content)) {
            return false;
        }
        String normalized = content.replaceAll("\\s+", " ").trim();
        if (normalized.length() < minChars) {
            return false;
        }
        if (normalized.matches("(?i)^(image\\d+\\.(png|jpg|jpeg|gif|webp)\\s*)+$")) {
            return false;
        }
        long informativeChars = normalized.codePoints()
                .filter(Character::isLetterOrDigit)
                .count();
        return informativeChars >= Math.min(30, Math.max(10, minChars / 3));
    }

    private List<String> generateQuestionsForChunk(
            KnowledgeChunkDO chunk,
            KnowledgeDocumentDO document,
            int questionsPerChunk) {
        String systemPrompt = """
                You generate stable RAG evaluation questions.
                Return JSON only: {"questions":["..."]}.
                Each question must be answerable primarily from the supplied chunk.
                Avoid mentioning "this chunk", document ids, or line numbers.
                Keep questions concise and in Chinese.
                """;
        String userPrompt = """
                Document: %s
                Chunk index: %s
                Required question count: %d

                Chunk:
                %s
                """.formatted(
                StrUtil.blankToDefault(document.getDocName(), document.getId()),
                chunk.getChunkIndex(),
                questionsPerChunk,
                preview(chunk.getContent(), 1800));
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(List.of(ChatMessage.system(systemPrompt), ChatMessage.user(userPrompt)))
                .temperature(0.2D)
                .maxTokens(512)
                .thinking(false)
                .build();
        String raw = llmService.chat(chatRequest);
        List<String> parsed = parseGeneratedQuestions(raw);
        if (parsed.isEmpty()) {
            parsed = List.of(buildFallbackQuestion(chunk, document));
        }
        return parsed.stream()
                .filter(StrUtil::isNotBlank)
                .map(String::trim)
                .distinct()
                .limit(questionsPerChunk)
                .toList();
    }

    private List<String> parseGeneratedQuestions(String raw) {
        if (StrUtil.isBlank(raw)) {
            return List.of();
        }
        String cleaned = LLMResponseCleaner.stripMarkdownCodeFence(raw);
        try {
            if (cleaned.startsWith("{")) {
                JSONObject object = JSON.parseObject(cleaned);
                JSONArray questions = object.getJSONArray("questions");
                return questions == null
                        ? List.of()
                        : questions.stream()
                        .map(String::valueOf)
                        .filter(StrUtil::isNotBlank)
                        .toList();
            }
            if (cleaned.startsWith("[")) {
                JSONArray questions = JSON.parseArray(cleaned);
                return questions.stream()
                        .map(String::valueOf)
                        .filter(StrUtil::isNotBlank)
                        .toList();
            }
        } catch (RuntimeException ignored) {
            // Fall through to line parsing.
        }
        return cleaned.lines()
                .map(line -> line.replaceFirst("^[-*\\d.、\\s]+", "").trim())
                .filter(line -> line.endsWith("?") || line.endsWith("？"))
                .toList();
    }

    private String buildFallbackQuestion(KnowledgeChunkDO chunk, KnowledgeDocumentDO document) {
        String docName = StrUtil.blankToDefault(document.getDocName(), "该文档");
        return "请概括%s中第%s个分块的核心内容。".formatted(docName, chunk.getChunkIndex());
    }

    private String preview(String content, int maxChars) {
        if (content == null) {
            return "";
        }
        String normalized = content.replaceAll("\\s+", " ").trim();
        return normalized.length() <= maxChars ? normalized : normalized.substring(0, maxChars);
    }

    private RagEvalResponse completeWithoutRetrieval(
            String traceId,
            String runId,
            String queryId,
            String question,
            RewriteResult rewriteResult,
            List<SubQuestionIntent> subIntents,
            RagEvalIntentMode intentMode,
            String route,
            String guidancePrompt,
            long totalStartedAt,
            long rewriteMs,
            long intentMs,
            long guidanceMs) {
        long totalMs = elapsedMs(totalStartedAt);
        RagEvalResponse response = response(
                traceId, runId, queryId, question, rewriteResult, subIntents, intentMode,
                route, guidancePrompt, List.of(), List.of(),
                new RagEvalResponse.TimingView(totalMs, rewriteMs, intentMs, guidanceMs, 0, 0));
        traceWriter.write("eval.request.completed", details(
                "route", route,
                "latencyMs", totalMs,
                "chunkCount", 0));
        return response;
    }

    private RagEvalResponse response(
            String traceId,
            String runId,
            String queryId,
            String question,
            RewriteResult rewriteResult,
            List<SubQuestionIntent> subIntents,
            RagEvalIntentMode intentMode,
            String route,
            String guidancePrompt,
            List<RagEvalResponse.ChunkView> chunks,
            List<RagEvalResponse.ChannelView> channels,
            RagEvalResponse.TimingView timings) {
        List<String> docIds = chunks.stream()
                .map(RagEvalResponse.ChunkView::docId)
                .filter(StrUtil::isNotBlank)
                .distinct()
                .toList();
        List<String> docNames = chunks.stream()
                .map(RagEvalResponse.ChunkView::docName)
                .filter(StrUtil::isNotBlank)
                .distinct()
                .toList();
        List<String> intentLeafIds = buildIntentViews(subIntents).stream()
                .flatMap(intent -> intent.nodes().stream())
                .map(RagEvalResponse.IntentNodeView::id)
                .filter(StrUtil::isNotBlank)
                .distinct()
                .toList();
        return new RagEvalResponse(
                traceId,
                runId,
                queryId,
                question,
                rewriteResult.rewrittenQuestion(),
                rewriteResult.subQuestions(),
                route,
                intentMode.value(),
                guidancePrompt,
                !chunks.isEmpty(),
                false,
                docIds,
                docNames,
                chunks.stream().map(RagEvalResponse.ChunkView::chunkId).toList(),
                chunks.stream().map(RagEvalResponse.ChunkView::text).filter(Objects::nonNull).toList(),
                chunks.stream().map(RagEvalResponse.ChunkView::docId).toList(),
                intentLeafIds,
                buildIntentViews(subIntents),
                chunks,
                channels,
                timings);
    }

    private List<RagEvalResponse.IntentView> buildIntentViews(List<SubQuestionIntent> subIntents) {
        return subIntents.stream().filter(Objects::nonNull).map(intent -> {
            FirstLayerIntentDecision firstLayer = intent.firstLayerDecision();
            List<RagEvalResponse.IntentNodeView> nodes = buildIntentNodeViews(intent.nodeScores());
            List<RagEvalResponse.IntentNodeView> candidates = firstLayer == null
                    ? List.of()
                    : buildIntentNodeViews(firstLayer.nodeScores());
            return new RagEvalResponse.IntentView(
                    intent.subQuestion(),
                    intent.routeKind() == null ? null : intent.routeKind().name(),
                    intent.globalKbFallback(),
                    firstLayer == null ? null : firstLayer.ragScore(),
                    firstLayer == null ? null : firstLayer.systemScore(),
                    nodes,
                    candidates);
        }).toList();
    }

    private List<RagEvalResponse.IntentNodeView> buildIntentNodeViews(List<com.ycy.aiapplication.rag.core.intent.common.NodeScore> scores) {
        if (scores == null) {
            return List.of();
        }
        return scores.stream()
                .filter(Objects::nonNull)
                .filter(node -> node.getNode() != null)
                .map(node -> new RagEvalResponse.IntentNodeView(
                        node.getNode().getId(),
                        node.getNode().getName(),
                        node.getNode().getKbId(),
                        node.getNode().getCollectionName(),
                        node.getNode().getKind() == null ? null : node.getNode().getKind().name(),
                        node.getScore()))
                .toList();
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

    private Map<String, KnowledgeChunkDO> loadChunkMetadata(RetrievalContext retrievalContext) {
        List<String> chunkIds = retrievalContext.getChannelResults().stream()
                .flatMap(channel -> channel.getChunks().stream())
                .map(RetrievedChunk::getId)
                .filter(StrUtil::isNotBlank)
                .distinct()
                .toList();
        if (chunkIds.isEmpty()) {
            return Map.of();
        }
        return knowledgeChunkDOMapper.selectBatchIds(chunkIds).stream()
                .collect(Collectors.toMap(
                        KnowledgeChunkDO::getId,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new));
    }

    private Map<String, KnowledgeDocumentDO> loadDocumentMetadata(Collection<KnowledgeChunkDO> chunks) {
        List<String> documentIds = chunks.stream()
                .map(KnowledgeChunkDO::getDocId)
                .filter(StrUtil::isNotBlank)
                .distinct()
                .toList();
        if (documentIds.isEmpty()) {
            return Map.of();
        }
        return knowledgeDocumentMapper.selectBatchIds(documentIds).stream()
                .collect(Collectors.toMap(
                        KnowledgeDocumentDO::getId,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new));
    }

    private List<RagEvalResponse.ChunkView> buildChunkViews(
            List<RetrievedChunk> chunks,
            Map<String, KnowledgeChunkDO> chunkById,
            Map<String, KnowledgeDocumentDO> documentById,
            boolean includeContexts) {
        List<RagEvalResponse.ChunkView> views = new ArrayList<>();
        for (int index = 0; index < chunks.size(); index++) {
            RetrievedChunk chunk = chunks.get(index);
            KnowledgeChunkDO chunkDO = chunkById.get(chunk.getId());
            KnowledgeDocumentDO documentDO = chunkDO == null ? null : documentById.get(chunkDO.getDocId());
            views.add(new RagEvalResponse.ChunkView(
                    index + 1,
                    chunkDO == null ? chunk.getId() : chunkDO.getId(),
                    chunk.getId(),
                    chunkDO == null ? null : chunkDO.getDocId(),
                    documentDO == null ? null : documentDO.getDocName(),
                    chunkDO == null ? null : chunkDO.getKbId(),
                    chunk.getScore(),
                    includeContexts ? chunk.getText() : null));
        }
        return views;
    }

    private List<RagEvalResponse.ChannelView> buildChannelViews(
            List<SearchChannelResult> channelResults,
            Map<String, KnowledgeChunkDO> chunkById,
            Map<String, KnowledgeDocumentDO> documentById,
            boolean includeContexts) {
        return channelResults.stream()
                .map(channel -> new RagEvalResponse.ChannelView(
                        channel.getChannelName(),
                        channel.getChannelType() == null ? null : channel.getChannelType().name(),
                        channel.getConfidence(),
                        channel.getLatencyMs(),
                        buildChunkViews(channel.getChunks(), chunkById, documentById, includeContexts)))
                .toList();
    }

    private Map<String, Object> details(Object... keyValues) {
        Map<String, Object> details = new LinkedHashMap<>();
        for (int index = 0; index < keyValues.length; index += 2) {
            details.put((String) keyValues[index], keyValues[index + 1]);
        }
        return details;
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
