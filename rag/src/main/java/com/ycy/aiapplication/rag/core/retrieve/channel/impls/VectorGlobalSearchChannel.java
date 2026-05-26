package com.ycy.aiapplication.rag.core.retrieve.channel.impls;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.ycy.aiapplication.knowledge.dao.mapper.KnowledgeChunkDOMapper;
import com.ycy.aiapplication.rag.config.RAGRetrieveProperties;
import com.ycy.aiapplication.rag.core.intent.common.IntentNode;
import com.ycy.aiapplication.rag.core.intent.common.SubQuestionIntent;
import com.ycy.aiapplication.rag.core.intent.common.enums.IntentKind;
import com.ycy.aiapplication.rag.core.intent.management.IntentNodeRegistry;
import com.ycy.aiapplication.rag.core.retrieve.common.SearchContext;
import com.ycy.aiapplication.rag.core.retrieve.channel.SearchChannelType;
import com.ycy.aiapplication.rag.core.retrieve.channel.retriver.CollectionParallelRetriever;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.ycy.aiapplication.rag.constant.RAGConstant.MULTI_CHANNEL_KEY;

/**
 * 全知识库检索通道。
 * <p>
 * 整体职责：
 * 1. 处理“第一层判定值得走 RAG，但第二层未命中任何具体节点”的兜底场景。
 * 2. 将所有可用知识库节点对应的 collection 聚合后做全局并行检索。
 * 3. 将检索结果统一回填到全局兜底 key，供通用 KB Prompt 使用。
 */
@Component
public class VectorGlobalSearchChannel extends AbstractVectorSearchChannel {

    private final RAGRetrieveProperties retrieveProperties;
    private final IntentNodeRegistry intentNodeRegistry;

    public VectorGlobalSearchChannel(CollectionParallelRetriever collectionParallelRetriever,
                                     RAGRetrieveProperties retrieveProperties,
                                     IntentNodeRegistry intentNodeRegistry,
                                     KnowledgeChunkDOMapper knowledgeChunkDOMapper) {
        super(collectionParallelRetriever, retrieveProperties, knowledgeChunkDOMapper);
        this.retrieveProperties = retrieveProperties;
        this.intentNodeRegistry = intentNodeRegistry;
    }

    @Override
    public String getName() {
        return "vector-global-search-channel";
    }

    @Override
    public int getPriority() {
        return 20;
    }

    /**
     * 判断当前是否需要启用全知识库检索通道。
     *
     * @param context 检索上下文。
     * @return true 表示当前至少有一个子问题需要全库兜底检索。
     */
    @Override
    public boolean isEnabled(SearchContext context) {
        return retrieveProperties.getChannels().getVectorGlobal().isEnabled()
                && context != null
                && context.getIntents() != null
                && context.getIntents().stream().anyMatch(this::needsGlobalFallback);
    }

    @Override
    public SearchChannelType getType() {
        return SearchChannelType.VECTOR_GLOBAL;
    }

    /**
     * 构造全知识库检索任务。
     *
     * @param context 检索上下文。
     * @return 每个需要全局兜底的子问题都会对应一个“查所有 collection”的检索任务。
     */
    @Override
    protected List<SearchTask> buildTasks(SearchContext context) {
        // 先收集当前系统中所有可用知识库节点对应的 collection，用于全库并行检索。
        List<String> allCollections = intentNodeRegistry.listKnowledgeNodes().stream()
                .map(IntentNode::getCollectionName)
                .filter(StrUtil::isNotBlank)
                .distinct()
                .toList();
        if (CollUtil.isEmpty(allCollections)) {
            return List.of();
        }

        int topK = Math.max(1, context.getTopK() * retrieveProperties.getChannels().getVectorGlobal().getTopKMultiplier());
        return context.getIntents().stream()
                .filter(this::needsGlobalFallback)
                .map(intent -> new SearchTask(
                        intent.subQuestion(),
                        allCollections,
                        topK,
                        buildIntentKeys(allCollections)))
                .toList();
    }

    /**
     * 判断一个子问题是否需要走全知识库兜底检索。
     *
     * @param intent 单个子问题的意图识别结果。
     * @return true 表示当前子问题属于“KB 路由，但未命中具体节点”的场景。
     */
    private boolean needsGlobalFallback(SubQuestionIntent intent) {
        return intent != null
                && intent.routeKind() == IntentKind.KB
                && intent.globalKbFallback();
    }

    /**
     * 构造全局兜底场景下的 collection 回填关系。
     *
     * @param collections 当前要检索的全部 collection 列表。
     * @return 所有 collection 都会回填到统一的全局兜底 key。
     */
    private Map<String, List<String>> buildIntentKeys(List<String> collections) {
        Map<String, List<String>> mapping = new LinkedHashMap<>();
        // 全库兜底场景不对应具体节点，因此统一挂到 multi_channel 占位 key 下。
        collections.forEach(collection -> mapping.put(collection, List.of(MULTI_CHANNEL_KEY)));
        return mapping;
    }
}
