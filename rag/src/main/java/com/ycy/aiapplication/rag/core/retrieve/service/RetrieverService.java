package com.ycy.aiapplication.rag.core.retrieve.service;

import com.ycy.aiapplication.framework.convention.RetrievedChunk;
import com.ycy.aiapplication.rag.core.retrieve.common.RetrieveRequest;

import java.util.List;

/**
 * 向量检索服务接口。
 * <p>
 * 整体职责：
 * 1. 封装底层向量数据库的检索能力。
 * 2. 对上层屏蔽 Milvus、pgVector 等具体实现差异。
 * 3. 为检索通道提供统一的 query 检索与向量检索入口。
 */
public interface RetrieverService {

    /**
     * 根据自然语言 Query 执行检索。
     *
     * @param query 用户问题。
     * @param topK 期望返回的分块数。
     * @return 检索命中的分块列表。
     */
    default List<RetrievedChunk> retrieve(String query, int topK) {
        RetrieveRequest req = RetrieveRequest.builder()
                .query(query)
                .topK(topK)
                .build();
        return retrieve(req);
    }

    /**
     * 根据完整检索请求执行检索。
     *
     * @param retrieveParam 检索请求参数，支持指定 collection 等扩展信息。
     * @return 检索命中的分块列表。
     */
    List<RetrievedChunk> retrieve(RetrieveRequest retrieveParam);

    /**
     * 根据已生成的向量直接执行检索。
     *
     * @param vector 查询向量。
     * @param retrieveParam 检索请求参数。
     * @return 检索命中的分块列表。
     */
    List<RetrievedChunk> retrieveByVector(float[] vector, RetrieveRequest retrieveParam);
}
