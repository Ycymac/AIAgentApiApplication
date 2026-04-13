package com.ycy.aiapplication.rag.core.retrieve.channel.retriver;

import com.ycy.aiapplication.framework.convention.RetrievedChunk;
import com.ycy.aiapplication.rag.core.retrieve.common.RetrieveRequest;
import com.ycy.aiapplication.rag.core.retrieve.service.RetrieverService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * 基于 collection 的并行向量检索器。
 * <p>
 * 整体职责：
 * 1. 继承并行检索模板类，聚焦“单个 collection 如何执行检索”。
 * 2. 调用底层 RetrieverService 完成向量检索。
 * 3. 供各类 SearchChannel 复用，避免通道层重复处理线程池与异常逻辑。
 */
@Slf4j
@Component
public class CollectionParallelRetriever extends AbstractParallelRetriever<String> {

    private final RetrieverService retrieverService;

    public CollectionParallelRetriever(RetrieverService retrieverService,
                                       @Qualifier("ragInnerRetrievalThreadPoolExecutor") Executor executor) {
        super(executor);
        this.retrieverService = retrieverService;
    }

    /**
     * 执行单个 collection 的向量检索。
     *
     * @param question 当前检索问题。
     * @param collectionName 要检索的 collection 名称。
     * @param topK 当前 collection 需要返回的分块条数。
     * @return 当前 collection 命中的检索分块；失败时返回空列表。
     */
    @Override
    protected List<RetrievedChunk> createRetrievalTask(String question, String collectionName, int topK) {
        try {
            // 这里显式传入 collectionName，使上层通道可以自由决定是全库检索还是节点定向检索。
            return retrieverService.retrieve(RetrieveRequest.builder()
                    .collectionName(collectionName)
                    .query(question)
                    .topK(topK)
                    .build());
        } catch (Exception ex) {
            log.error("retrieve failed, collection={}", collectionName, ex);
            return List.of();
        }
    }

    /**
     * 返回当前目标的日志标识。
     *
     * @param target 当前检索目标，即 collection 名称。
     * @return 日志标识。
     */
    @Override
    protected String getTargetIdentifier(String target) {
        return target;
    }

    /**
     * 返回当前检索器的统计名称。
     *
     * @return 统计名称。
     */
    @Override
    protected String getStatisticsName() {
        return "collection-parallel-retrieval";
    }
}
