package com.ycy.aiapplication.rag.core.retrieve.channel.impls;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.ycy.aiapplication.framework.convention.RetrievedChunk;
import com.ycy.aiapplication.rag.core.retrieve.common.SearchContext;
import com.ycy.aiapplication.rag.core.retrieve.channel.SearchChannel;
import com.ycy.aiapplication.rag.core.retrieve.channel.SearchChannelResult;
import com.ycy.aiapplication.rag.core.retrieve.channel.retriver.AbstractParallelRetriever;
import com.ycy.aiapplication.rag.core.retrieve.channel.retriver.CollectionParallelRetriever;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 向量检索通道抽象父类。
 * <p>
 * 整体职责：
 * 1. 统一封装基于 collection 的并行向量检索执行流程。
 * 2. 负责收集检索耗时、合并分块结果、回填“意图 -> chunk”映射。
 * 3. 将通道间通用逻辑沉淀到父类，具体子类只负责构造检索任务参数。
 */
public abstract class AbstractVectorSearchChannel implements SearchChannel {

    public static final String METADATA_INTENT_CHUNKS = "intentChunks";

    private final CollectionParallelRetriever collectionParallelRetriever;

    protected AbstractVectorSearchChannel(CollectionParallelRetriever collectionParallelRetriever) {
        this.collectionParallelRetriever = collectionParallelRetriever;
    }

    /**
     * 执行当前向量检索通道。
     *
     * @param context 检索上下文，包含子问题、意图识别结果以及基准 topK 等信息。
     * @return 当前通道的统一检索结果对象，metadata 中会额外携带意图分块映射。
     */
    @Override
    public SearchChannelResult search(SearchContext context) {
        long start = System.currentTimeMillis();
        // 由子类先给出当前通道应该执行的检索任务列表。
        List<SearchTask> tasks = buildTasks(context);
        if (CollUtil.isEmpty(tasks)) {
            return buildResult(List.of(), Map.of(), 0L);
        }

        List<RetrievedChunk> mergedChunks = new ArrayList<>();
        Map<String, List<RetrievedChunk>> intentChunks = new LinkedHashMap<>();

        for (SearchTask task : tasks) {
            if (StrUtil.isBlank(task.question()) || CollUtil.isEmpty(task.collections()) || task.topK() <= 0) {
                continue;
            }
            // 每个任务内部会对多个 collection 做并行检索，适合意图节点命中多个知识库的场景。
            AbstractParallelRetriever.ParallelRetrievalResult<String> result =
                    collectionParallelRetriever.executeParallelRetrievalWithTargets(task.question(), task.collections(), task.topK());
            mergedChunks.addAll(result.allChunks());
            // 将 collection 维度的结果重新映射回意图维度，避免上层感知 collection 细节。
            mergeIntentChunks(intentChunks, result.targetChunks(), task.collectionIntentKeys());
        }

        return buildResult(mergedChunks, intentChunks, System.currentTimeMillis() - start);
    }

    /**
     * 构造当前通道的标准返回结果。
     *
     * @param chunks 当前通道检索到的全部分块。
     * @param intentChunks 当前通道内部整理好的“意图 -> 分块”映射。
     * @param latencyMs 当前通道总耗时，单位毫秒。
     * @return 通道标准结果对象。
     */
    private SearchChannelResult buildResult(List<RetrievedChunk> chunks,
                                            Map<String, List<RetrievedChunk>> intentChunks,
                                            long latencyMs) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(METADATA_INTENT_CHUNKS, intentChunks);
        metadata.put("chunkCount", chunks.size());
        metadata.put("intentCount", intentChunks.size());
        return SearchChannelResult.builder()
                .channelType(getType())
                .channelName(getName())
                .chunks(chunks)
                .confidence(resolveConfidence(chunks, intentChunks))
                .latencyMs(latencyMs)
                .metadata(metadata)
                .build();
    }

    /**
     * 将 collection 维度的检索结果合并到意图维度。
     *
     * @param intentChunks 最终的“意图 -> 分块”映射。
     * @param collectionChunks 并行检索器返回的“collection -> 分块”映射。
     * @param collectionIntentKeys collection 与意图节点之间的映射关系。
     */
    private void mergeIntentChunks(Map<String, List<RetrievedChunk>> intentChunks,
                                   Map<String, List<RetrievedChunk>> collectionChunks,
                                   Map<String, List<String>> collectionIntentKeys) {
        collectionChunks.forEach((collection, chunks) -> {
            List<String> intentKeys = collectionIntentKeys.getOrDefault(collection, List.of());
            for (String intentKey : intentKeys) {
                // 同一 collection 可能服务于多个意图节点，因此这里允许一份结果映射到多个意图 key。
                intentChunks.computeIfAbsent(intentKey, key -> new ArrayList<>()).addAll(chunks);
            }
        });
    }

    /**
     * 计算当前通道结果置信度。
     *
     * @param chunks 当前通道命中的全部分块。
     * @param intentChunks 当前通道整理出的意图分块映射。
     * @return 通道置信度。当前默认策略为“有结果即返回 1，否则返回 0”。
     */
    protected double resolveConfidence(List<RetrievedChunk> chunks, Map<String, List<RetrievedChunk>> intentChunks) {
        return CollUtil.isEmpty(chunks) ? 0D : 1D;
    }

    /**
     * 由具体通道实现构造检索任务。
     *
     * @param context 检索上下文。
     * @return 当前通道需要执行的检索任务列表。
     */
    protected abstract List<SearchTask> buildTasks(SearchContext context);

    /**
     * 单个检索任务描述对象。
     *
     * @param question 当前任务对应的查询问题，通常为某个子问题。
     * @param collections 当前任务要命中的 collection 列表。
     * @param topK 当前任务的实际检索条数。
     * @param collectionIntentKeys collection 到意图节点 ID 列表的映射，用于回填结果。
     */
    protected record SearchTask(
            String question,
            List<String> collections,
            int topK,
            Map<String, List<String>> collectionIntentKeys
    ) {
    }
}
