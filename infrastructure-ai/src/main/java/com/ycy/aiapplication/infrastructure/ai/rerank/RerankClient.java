package com.ycy.aiapplication.infrastructure.ai.rerank;

import com.ycy.aiapplication.framework.convention.RetrievedChunk;

import java.util.List;

/**
 * Rerank重排序客户端接口
 * 用于对检索到的文档片进行重排序，提高检索结果相关性
 * 这里使用客户端内降级策略，替代原本的服务端降级策略，为同一平台提供可替换的多种rerank模型
 */
public interface RerankClient {

    /**
     * 获取Rerank服务提供商名称
     * @return 提供商名称，EX：“bailian”等
     */
    String provider();

    /**
     * 对检索到的文本片段进行重排序
     * @param query 用户问题文本
     * @param candidates 候选文档片段列表
     * @param topN 返回前N个结果
     * @return 重排序之后的列表
     */
    List<RetrievedChunk> rerank(String query, List<RetrievedChunk>candidates, int topN);

}