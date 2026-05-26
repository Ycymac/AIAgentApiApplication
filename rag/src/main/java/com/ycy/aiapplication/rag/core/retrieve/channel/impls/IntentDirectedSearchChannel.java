package com.ycy.aiapplication.rag.core.retrieve.channel.impls;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.ycy.aiapplication.knowledge.dao.mapper.KnowledgeChunkDOMapper;
import com.ycy.aiapplication.rag.config.RAGRetrieveProperties;
import com.ycy.aiapplication.rag.core.intent.common.IntentNode;
import com.ycy.aiapplication.rag.core.intent.common.NodeScore;
import com.ycy.aiapplication.rag.core.intent.common.SubQuestionIntent;
import com.ycy.aiapplication.rag.core.intent.common.enums.IntentKind;
import com.ycy.aiapplication.rag.core.retrieve.common.SearchContext;
import com.ycy.aiapplication.rag.core.retrieve.channel.SearchChannelType;
import com.ycy.aiapplication.rag.core.retrieve.channel.retriver.CollectionParallelRetriever;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 意图定向检索通道。
 * <p>
 * 整体职责：
 * 1. 处理“意图识别阶段已经明确命中的知识库节点”场景。
 * 2. 直接从节点上提取 collectionName 并执行定向向量检索。
 * 3. 不再在检索阶段重复判断节点分数是否达标，因为低分节点已在意图识别阶段被过滤。
 */
@Component
public class IntentDirectedSearchChannel extends AbstractVectorSearchChannel {

    private final RAGRetrieveProperties retrieveProperties;

    public IntentDirectedSearchChannel(CollectionParallelRetriever collectionParallelRetriever,
                                       RAGRetrieveProperties retrieveProperties,
                                       KnowledgeChunkDOMapper knowledgeChunkDOMapper) {
        super(collectionParallelRetriever, retrieveProperties, knowledgeChunkDOMapper);
        this.retrieveProperties = retrieveProperties;
    }

    @Override
    public String getName() {
        return "intent-directed-search-channel";
    }

    @Override
    public int getPriority() {
        return 10;
    }

    /**
     * 判断当前通道是否需要启用。
     *
     * @param context 检索上下文。
     * @return true 表示当前存在需要走“节点对应知识库检索”的子问题。
     */
    @Override
    public boolean isEnabled(SearchContext context) {
        return retrieveProperties.getChannels().getIntentDirected().isEnabled()
                && context != null
                && context.getIntents() != null
                && context.getIntents().stream().anyMatch(this::isIntentDirected);
    }

    @Override
    public SearchChannelType getType() {
        return SearchChannelType.INTENT_DIRECTED;
    }

    /**
     * 构造意图定向检索任务。
     *
     * @param context 检索上下文。
     * @return 每个需要定向检索的子问题都会被转换为一个检索任务。
     */
    @Override
    protected List<SearchTask> buildTasks(SearchContext context) {
        return context.getIntents().stream()
                .filter(this::isIntentDirected)
                .map(intent -> new SearchTask(
                        intent.subQuestion(),
                        resolveCollections(intent),
                        resolveTopK(context, intent),
                        resolveCollectionIntentKeys(intent)))
                .filter(task -> CollUtil.isNotEmpty(task.collections()))
                .toList();
    }

    /**
     * 判断一个子问题是否属于意图定向检索场景。
     *
     * @param intent 单个子问题的意图识别结果。
     * @return true 表示当前子问题已经明确命中节点，应直接走节点对应知识库检索。
     */
    private boolean isIntentDirected(SubQuestionIntent intent) {
        return intent != null
                && intent.routeKind() == IntentKind.KB
                && !intent.globalKbFallback()
                && CollUtil.isNotEmpty(intent.nodeScores());
    }

    /**
     * 从意图节点中提取要检索的 collection 列表。
     *
     * @param intent 单个子问题的意图结果。
     * @return collection 名称列表，已去重。
     */
    private List<String> resolveCollections(SubQuestionIntent intent) {
        return intent.nodeScores().stream()
                .map(NodeScore::getNode)
                .filter(node -> node != null && StrUtil.isNotBlank(node.getCollectionName()))
                .map(IntentNode::getCollectionName)
                .distinct()
                .toList();
    }

    /**
     * 计算当前定向检索任务的实际 topK。
     *
     * @param context 检索上下文，提供全局基准 topK。
     * @param intent 单个子问题的意图结果，节点上可能带有个性化 topK。
     * @return 最终用于检索的 topK，至少为全局默认值，并结合通道倍率放大。
     */
    private int resolveTopK(SearchContext context, SubQuestionIntent intent) {
        int defaultTopK = Math.max(1, context.getTopK() * retrieveProperties.getChannels().getIntentDirected().getTopKMultiplier());
        int nodeTopK = intent.nodeScores().stream()
                .map(NodeScore::getNode)
                .filter(node -> node != null && node.getTopK() != null && node.getTopK() > 0)
                .map(node -> node.getTopK() * retrieveProperties.getChannels().getIntentDirected().getTopKMultiplier())
                .max(Integer::compareTo)
                .orElse(defaultTopK);
        return Math.max(defaultTopK, nodeTopK);
    }

    /**
     * 构造 collection 到意图节点 ID 的映射。
     *
     * @param intent 单个子问题的意图结果。
     * @return 用于把 collection 检索结果回填到对应意图节点的映射关系。
     */
    private Map<String, List<String>> resolveCollectionIntentKeys(SubQuestionIntent intent) {
        Map<String, List<String>> mapping = new LinkedHashMap<>();
        for (NodeScore nodeScore : intent.nodeScores()) {
            if (nodeScore.getNode() == null
                    || StrUtil.isBlank(nodeScore.getNode().getCollectionName())
                    || StrUtil.isBlank(nodeScore.getNode().getId())) {
                continue;
            }
            // 一个 collection 可能同时被多个意图节点复用，因此 value 设计为列表。
            mapping.computeIfAbsent(nodeScore.getNode().getCollectionName(), key -> new java.util.ArrayList<>())
                    .add(nodeScore.getNode().getId());
        }
        return mapping;
    }
}
