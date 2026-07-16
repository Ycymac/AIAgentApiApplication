package com.ycy.aiapplication.rag.core.retrieve.channel.impls;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.ycy.aiapplication.framework.convention.RetrievedChunk;
import com.ycy.aiapplication.knowledge.dao.mapper.KnowledgeChunkDOMapper;
import com.ycy.aiapplication.rag.config.RAGRetrieveProperties;
import com.ycy.aiapplication.rag.core.retrieve.channel.SearchChannel;
import com.ycy.aiapplication.rag.core.retrieve.channel.SearchChannelResult;
import com.ycy.aiapplication.rag.core.retrieve.channel.retriver.CollectionParallelRetriever;
import com.ycy.aiapplication.rag.core.retrieve.common.QueryEmbeddingContext;
import com.ycy.aiapplication.rag.core.retrieve.common.SearchContext;
import com.ycy.aiapplication.rag.core.retrieve.common.SearchTask;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Base implementation for vector-backed retrieval channels.
 */
@Slf4j
public abstract class AbstractVectorSearchChannel implements SearchChannel {

    public static final String METADATA_INTENT_CHUNKS = "intentChunks";

    private final CollectionParallelRetriever collectionParallelRetriever;
    private final RAGRetrieveProperties retrieveProperties;
    private final KnowledgeChunkDOMapper knowledgeChunkDOMapper;

    protected AbstractVectorSearchChannel(CollectionParallelRetriever collectionParallelRetriever,
                                          RAGRetrieveProperties retrieveProperties,
                                          KnowledgeChunkDOMapper knowledgeChunkDOMapper) {
        this.collectionParallelRetriever = collectionParallelRetriever;
        this.retrieveProperties = retrieveProperties;
        this.knowledgeChunkDOMapper = knowledgeChunkDOMapper;
    }

    @Override
    public final List<SearchTask> plan(SearchContext context) {
        return buildTasks(context);
    }

    /**
     * 使用编排器统一准备的查询向量执行当前通道。
     */
    @Override
    public SearchChannelResult search(SearchContext context,
                                      List<SearchTask> tasks,
                                      QueryEmbeddingContext embeddingContext) {
        long start = System.currentTimeMillis();
        if (CollUtil.isEmpty(tasks)) {
            return buildResult(List.of(), Map.of(), 0L);
        }

        List<RetrievedChunk> mergedChunks = new ArrayList<>();
        Map<String, List<RetrievedChunk>> intentChunks = new LinkedHashMap<>();

        for (SearchTask task : tasks) {
            if (StrUtil.isBlank(task.question()) || CollUtil.isEmpty(task.collections()) || task.topK() <= 0) {
                continue;
            }
            Map<String, float[]> collectionVectors = new LinkedHashMap<>();
            for (String collection : task.collections()) {
                float[] vector = embeddingContext.resolveVector(collection, task.question());
                if (vector != null && vector.length > 0) {
                    collectionVectors.put(collection, vector);
                }
            }
            if (collectionVectors.isEmpty()) {
                continue;
            }
            // 每个任务内部会对多个 collection 做并行检索，适合意图节点命中多个知识库的场景。
            CollectionParallelRetriever.ParallelRetrievalResult result =
                    collectionParallelRetriever.executeParallelRetrievalWithTargets(
                            task.question(), collectionVectors, task.topK());
            //按照模式进行文本块清洗
            result = filterInvisibleChunks(result);

            mergedChunks.addAll(result.allChunks());
            // 将 collection 维度的结果重新映射回意图维度，便于上层感知 collection 细节。
            mergeIntentChunks(intentChunks, result.targetChunks(), task.collectionIntentKeys());
        }

        return buildResult(mergedChunks, intentChunks, System.currentTimeMillis() - start);
    }

    /**
     * 严格模式下基于 MySQL 执行分块可见性校验。
     *<p>
     * 执行思路：
     * 1. Milvus 仅负责召回候选分块，不保证数据实时一致性。
     *2. 当开启严格过滤时，以 MySQL 为唯一可信源，校验分块、文档、知识库的启用与删除状态。
     * 3. 若数据库查询失败或无可见分块，则视为数据库不可用，清空当前检索结果以避免脏数据进入后续流程。
     *
     * @param result 并行检索器返回的原始候选结果。
     * @return 经过可见性过滤后的检索结果；若校验失败则返回空结果。
     */
    private CollectionParallelRetriever.ParallelRetrievalResult filterInvisibleChunks(
            CollectionParallelRetriever.ParallelRetrievalResult result) {
        // 未开启严格模式或结果为空时直接放行，避免不必要的数据库开销。
        if (!retrieveProperties.getStrictDBFilterUse() || result == null || CollUtil.isEmpty(result.allChunks())) {
            return result;
        }

        try {
            // 提取所有候选分块的 ID 并去重，准备批量查询。
            List<String> chunkIds = result.allChunks().stream()
                    .filter(Objects::nonNull)
                    .map(RetrievedChunk::getId)
                    .filter(StrUtil::isNotBlank)
                    .distinct()
                    .toList();
            if (CollUtil.isEmpty(chunkIds)) {
                return emptyRetrievalResult(result);
            }
            // 调用 Mapper 批量查询在 MySQL 中仍处于“可见”状态的分块 ID。
            Set<String> visibleChunkIds = knowledgeChunkDOMapper.findVisibleChunkIds(chunkIds);
            if (CollUtil.isEmpty(visibleChunkIds)) {
                return emptyRetrievalResult(result);
            }
            // 根据可见 ID 集合过滤全量分块列表以及按 target 拆分的明细映射。
            List<RetrievedChunk> filteredAllChunks = filterChunksByVisibleIds(result.allChunks(), visibleChunkIds);
            Map<String, List<RetrievedChunk>> filteredTargetChunks = new LinkedHashMap<>();
            result.targetChunks().forEach((target, chunks) ->
                    filteredTargetChunks.put(target, filterChunksByVisibleIds(chunks, visibleChunkIds)));

            return new CollectionParallelRetriever.ParallelRetrievalResult(
                    filteredAllChunks,
                    filteredTargetChunks,
                    result.successCount(),
                    result.failureCount()
            );
        } catch (Exception ex) {
            // 数据库校验异常时采取保守策略：记录错误并返回空结果，防止不一致数据污染 RAG 上下文。
            log.error("严格模式下的数据库分块过滤失败，返回空检索结果。", ex);
            return emptyRetrievalResult(result);
        }
    }

    /**
     * 根据可见分块 ID 集合过滤分块列表。
     *
     * @param chunks 待过滤的分块列表。
     * @param visibleChunkIds 在 MySQL 中校验通过的分块 ID 集合。
     * @return 过滤后保留的分块列表。
     */
    private List<RetrievedChunk> filterChunksByVisibleIds(List<RetrievedChunk> chunks, Set<String> visibleChunkIds) {
        if (CollUtil.isEmpty(chunks) || CollUtil.isEmpty(visibleChunkIds)) {
            return List.of();
        }
        return chunks.stream()
                .filter(Objects::nonNull)
                .filter(chunk -> visibleChunkIds.contains(chunk.getId()))
                .toList();
    }

    /**
     * 构造空并行检索结果，用于校验失败或异常时的兜底返回。
     *
     * @param source 原始检索结果，用于继承成功/失败统计信息。
     * @return 分块列表清空检索结果对象。
     */
    private CollectionParallelRetriever.ParallelRetrievalResult emptyRetrievalResult(
            CollectionParallelRetriever.ParallelRetrievalResult source) {
        return new CollectionParallelRetriever.ParallelRetrievalResult(
                List.of(),
                Map.of(),
                source == null ? 0 : source.successCount(),
                source == null ? 0 : source.failureCount()
        );
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

}
