package com.ycy.aiapplication.rag.core.retrieve;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.ycy.aiapplication.framework.convention.RetrievedChunk;
import com.ycy.aiapplication.infrastructure.ai.rerank.RerankClient;
import com.ycy.aiapplication.rag.core.retrieve.channel.SearchChannel;
import com.ycy.aiapplication.rag.core.retrieve.channel.SearchChannelResult;
import com.ycy.aiapplication.rag.core.retrieve.channel.impls.AbstractVectorSearchChannel;
import com.ycy.aiapplication.rag.core.retrieve.common.RetrievalContext;
import com.ycy.aiapplication.rag.core.retrieve.common.QueryEmbeddingContext;
import com.ycy.aiapplication.rag.core.retrieve.common.SearchContext;
import com.ycy.aiapplication.rag.core.retrieve.common.SearchTask;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 检索执行总编排器。
 * <p>
 * 整体职责：
 * 1. 接收意图识别阶段产出的子问题与路由结果。
 * 2. 按优先级调度各个检索通道执行检索。
 * 3. 在通道结果合并前先对每个通道的分块结果执行 rerank。
 * 4. 对 rerank 后的结果做聚合、去重、排序，并构造成统一的检索上下文。
 */
@Service
public class RetrievalEngine {

    private final List<SearchChannel> searchChannels;
    private final RerankClient rerankClient;
    private final QueryEmbeddingBatcher queryEmbeddingBatcher;

    public RetrievalEngine(List<SearchChannel> searchChannels,
                           RerankClient rerankClient,
                           QueryEmbeddingBatcher queryEmbeddingBatcher) {
        this.searchChannels = searchChannels.stream()
                .sorted(Comparator.comparingInt(SearchChannel::getPriority))
                .toList();
        this.rerankClient = rerankClient;
        this.queryEmbeddingBatcher = queryEmbeddingBatcher;
    }

    /**
     * 执行一次完整的检索流程。
     * @return 检索上下文。若没有拿到有效知识库分块，则返回空上下文。
     */
    public RetrievalContext retrieve(SearchContext context) {
        if (CollUtil.isEmpty(context.getIntents())) {
            return RetrievalContext.empty();
        }

        // 所有启用通道先完成任务规划，再对整次请求统一执行模型映射和批量向量化。
        List<ChannelPlan> channelPlans = searchChannels.stream()
                .filter(channel -> channel.isEnabled(context))
                .map(channel -> new ChannelPlan(channel, channel.plan(context)))
                .toList();
        List<SearchTask> allTasks = channelPlans.stream()
                .flatMap(plan -> plan.tasks().stream())
                .toList();
        if (CollUtil.isEmpty(allTasks)) {
            return RetrievalContext.empty();
        }

        QueryEmbeddingContext embeddingContext = queryEmbeddingBatcher.prepare(allTasks);

        // 使用统一生成的请求级向量按通道执行检索，仅保留真正返回了分块结果的通道。
        List<SearchChannelResult> results = channelPlans.stream()
                .map(plan -> plan.channel().search(context, plan.tasks(), embeddingContext))
                .filter(Objects::nonNull)
                .filter(result -> CollUtil.isNotEmpty(result.getChunks()))
                .toList();
        if (CollUtil.isEmpty(results)) {
            return RetrievalContext.empty();
        }

        // 在通道结果进入合并逻辑前，先基于主问题对每个通道结果分别执行 rerank。
        List<SearchChannelResult> rerankedResults = rerankChannelResults(context, results);

        // 将各通道携带的“意图 -> chunks”映射合并，并在意图维度做去重和排序。
        Map<String, List<RetrievedChunk>> intentChunks = mergeIntentChunks(rerankedResults);
        String kbContext = buildKbContext(intentChunks);
        if (StrUtil.isBlank(kbContext)) {
            return RetrievalContext.empty();
        }

        return RetrievalContext.builder()
                .kbContext(kbContext)
                .intentChunks(intentChunks)
                .channelResults(rerankedResults)
                .build();
    }

    /**
     * 对各检索通道的结果分别执行 rerank。
     *
     * @param context 检索上下文，用于提供 rerank 查询语句。
     * @param results 原始通道结果列表。
     * @return rerank 后的通道结果列表；若 rerank 失败，则回退到原始结果。
     */
    private List<SearchChannelResult> rerankChannelResults(SearchContext context, List<SearchChannelResult> results) {
        if (rerankClient == null || StrUtil.isBlank(context.getMainQuestion())) {
            return results;
        }

        return results.stream()
                .map(result -> rerankSingleChannelResult(context.getMainQuestion(), result))
                .toList();
    }

    /**
     * 对单个通道结果执行 rerank，并同步刷新 metadata 中的意图分块映射顺序。
     *
     * @param query rerank 使用的问题文本。
     * @param result 单个通道结果。
     * @return rerank 后的新结果；若失败则直接返回原结果。
     */
    @SuppressWarnings("unchecked")
    private SearchChannelResult rerankSingleChannelResult(String query, SearchChannelResult result) {
        if (result == null || CollUtil.isEmpty(result.getChunks())) {
            return result;
        }

        try {
            List<RetrievedChunk> rerankedChunks = rerankClient.rerank(query, result.getChunks(), result.getChunks().size());
            if (CollUtil.isEmpty(rerankedChunks)) {
                return result;
            }

            Map<String, Object> metadata = new LinkedHashMap<>(result.getMetadata());
            Object rawIntentChunks = metadata.get(AbstractVectorSearchChannel.METADATA_INTENT_CHUNKS);
            if (rawIntentChunks instanceof Map<?, ?> rawMap) {
                Map<String, List<RetrievedChunk>> reorderedIntentChunks = reorderIntentChunksByRerank(rawMap, rerankedChunks);
                metadata.put(AbstractVectorSearchChannel.METADATA_INTENT_CHUNKS, reorderedIntentChunks);
            }

            return SearchChannelResult.builder()
                    .channelType(result.getChannelType())
                    .channelName(result.getChannelName())
                    .chunks(rerankedChunks)
                    .confidence(result.getConfidence())
                    .latencyMs(result.getLatencyMs())
                    .metadata(metadata)
                    .build();
        } catch (Exception ex) {
            return result;
        }
    }

    /**
     * 按 rerank 后的全量顺序重排通道内部的意图分块映射。
     *
     * @param rawMap metadata 中原始的意图分块映射。
     * @param rerankedChunks 通道 rerank 后的分块顺序。
     * @return 与 rerank 顺序保持一致的意图分块映射。
     */
    private Map<String, List<RetrievedChunk>> reorderIntentChunksByRerank(Map<?, ?> rawMap, List<RetrievedChunk> rerankedChunks) {
        Map<String, List<RetrievedChunk>> source = new LinkedHashMap<>();
        rawMap.forEach((key, value) -> {
            if (!(key instanceof String intentKey) || !(value instanceof List<?> chunkList)) {
                return;
            }
            List<RetrievedChunk> chunks = chunkList.stream()
                    .filter(RetrievedChunk.class::isInstance)
                    .map(RetrievedChunk.class::cast)
                    .toList();
            source.put(intentKey, chunks);
        });

        Map<String, List<RetrievedChunk>> reordered = new LinkedHashMap<>();
        for (RetrievedChunk rerankedChunk : rerankedChunks) {
            String rerankedKey = resolveChunkKey(rerankedChunk);
            for (Map.Entry<String, List<RetrievedChunk>> entry : source.entrySet()) {
                boolean matched = entry.getValue().stream()
                        .anyMatch(chunk -> resolveChunkKey(chunk).equals(rerankedKey));
                if (matched) {
                    reordered.computeIfAbsent(entry.getKey(), ignored -> new ArrayList<>()).add(rerankedChunk);
                }
            }
        }
        return reordered;
    }

    /**
     * 合并多通道返回的意图分块映射。
     *
     * @param results 各检索通道的结果，metadata 中会携带当前通道命中的意图分块映射。
     * @return 合并后的“意图标识 -> 检索分块列表”映射，并已完成去重与分数排序。
     */
    @SuppressWarnings("unchecked")
    private Map<String, List<RetrievedChunk>> mergeIntentChunks(List<SearchChannelResult> results) {
        Map<String, List<RetrievedChunk>> merged = new LinkedHashMap<>();
        for (SearchChannelResult result : results) {
            Object raw = result.getMetadata().get(AbstractVectorSearchChannel.METADATA_INTENT_CHUNKS);
            if (!(raw instanceof Map<?, ?> rawMap)) {
                continue;
            }
            rawMap.forEach((key, value) -> {
                if (!(key instanceof String intentKey) || !(value instanceof List<?> chunkList)) {
                    return;
                }
                // 仅保留合法的分块对象，避免 metadata 中混入其他结构。
                List<RetrievedChunk> chunks = chunkList.stream()
                        .filter(RetrievedChunk.class::isInstance)
                        .map(RetrievedChunk.class::cast)
                        .toList();
                merged.computeIfAbsent(intentKey, ignored -> new ArrayList<>()).addAll(chunks);
            });
        }

        // 不同通道、不同 collection 可能命中同一分块，这里统一按分数排序后去重。
        merged.replaceAll((key, chunks) -> deduplicateAndSort(chunks));
        return merged;
    }

    /**
     * 构造最终给 Prompt 使用的知识库正文。
     *
     * @param intentChunks 按意图聚合后的分块映射。
     * @return 适合直接注入 Prompt 的文本块，按分数从高到低编号排列。
     */
    private String buildKbContext(Map<String, List<RetrievedChunk>> intentChunks) {
        List<RetrievedChunk> mergedChunks = intentChunks.values().stream()
                .flatMap(List::stream)
                .toList();
        List<RetrievedChunk> deduplicated = deduplicateAndSort(mergedChunks);
        if (CollUtil.isEmpty(deduplicated)) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < deduplicated.size(); i++) {
            RetrievedChunk chunk = deduplicated.get(i);
            // 显式编号有助于模型在回答时引用不同证据片段，降低串段风险。
            builder.append("[").append(i + 1).append("]\n");
            builder.append(StrUtil.blankToDefault(chunk.getText(), ""));
            if (i < deduplicated.size() - 1) {
                builder.append("\n\n");
            }
        }
        return builder.toString();
    }

    /**
     * 对检索分块进行去重并按分数降序排序。
     *
     * @param chunks 原始分块列表，可能来自多个 collection 或多个检索通道。
     * @return 去重后的分块列表。若同一分块重复命中，则保留排序后最先出现的一条。
     */
    private List<RetrievedChunk> deduplicateAndSort(List<RetrievedChunk> chunks) {
        Map<String, RetrievedChunk> deduplicated = new LinkedHashMap<>();
        chunks.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing((RetrievedChunk chunk) -> chunk.getScore() == null ? 0F : chunk.getScore()).reversed())
                .forEach(chunk -> deduplicated.putIfAbsent(resolveChunkKey(chunk), chunk));
        return new ArrayList<>(deduplicated.values());
    }

    /**
     * 生成分块的去重键。
     *
     * @param chunk 当前检索命中的分块对象。
     * @return 优先使用分块主键 ID；若缺失则退化为文本内容。
     */
    private String resolveChunkKey(RetrievedChunk chunk) {
        if (StrUtil.isNotBlank(chunk.getId())) {
            return chunk.getId();
        }
        return StrUtil.blankToDefault(chunk.getText(), "");
    }

    private record ChannelPlan(SearchChannel channel, List<SearchTask> tasks) {
    }
}
