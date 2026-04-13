package com.ycy.aiapplication.rag.core.retrieve.service;

import cn.hutool.core.util.StrUtil;
import com.ycy.aiapplication.framework.convention.RetrievedChunk;
import com.ycy.aiapplication.infrastructure.ai.config.RAGCollectionProperties;
import com.ycy.aiapplication.infrastructure.ai.embedding.EmbeddingService;
import com.ycy.aiapplication.rag.core.retrieve.common.RetrieveRequest;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.BaseVector;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.SearchResp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 基于 Milvus 的向量检索服务实现。
 * <p>
 * 整体职责：
 * 1. 负责把自然语言 Query 转换为向量。
 * 2. 组装 Milvus 检索请求并执行向量搜索。
 * 3. 将 Milvus 返回结果转换为系统内部统一的 RetrievedChunk 结构。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rag.vector.type", havingValue = "milvus", matchIfMissing = true)
public class MilvusRetrieverService implements RetrieverService {

    private final EmbeddingService embeddingService;
    private final MilvusClientV2 milvusClient;
    private final RAGCollectionProperties ragCollectionProperties;

    /**
     * 根据自然语言问题执行检索。
     *
     * @param retrieveParam 检索请求参数，其中 query 为必填，collectionName 可按需指定。
     * @return 检索命中的分块列表。
     */
    @Override
    public List<RetrievedChunk> retrieve(RetrieveRequest retrieveParam) {
        // 先把文本问题编码为向量，再走统一的向量检索逻辑。
        List<Float> embed = embeddingService.embed(retrieveParam.getQuery());
        float[] vec = toArray(embed);
        return retrieveByVector(vec, retrieveParam);
    }

    /**
     * 根据查询向量直接执行 Milvus 检索。
     *
     * @param vector 查询向量。
     * @param retrieveParam 检索请求参数，支持指定 topK 与 collectionName。
     * @return 检索命中的分块列表。
     */
    @Override
    public List<RetrievedChunk> retrieveByVector(float[] vector, RetrieveRequest retrieveParam) {
        List<BaseVector> vectors = List.of(new FloatVec(vector));

        Map<String, Object> params = new HashMap<>();
        params.put("metric_type", ragCollectionProperties.getMetricType());
        params.put("ef", 128);

        // collectionName 为空时回退到系统默认知识库，否则按调用方指定的 collection 检索。
        SearchReq req = SearchReq.builder()
                .collectionName(
                        StrUtil.isBlank(retrieveParam.getCollectionName())
                                ? ragCollectionProperties.getCollectionName()
                                : retrieveParam.getCollectionName()
                )
                .annsField("embedding")
                .data(vectors)
                .topK(retrieveParam.getTopK())
                .searchParams(params)
                .outputFields(List.of("id", "content", "metadata"))
                .build();

        SearchResp resp = milvusClient.search(req);
        List<List<SearchResp.SearchResult>> results = resp.getSearchResults();
        if (results == null || results.isEmpty()) {
            return List.of();
        }

        // 将 Milvus 原始返回结构转换为系统统一分块对象，便于上层通道与 Prompt 侧复用。
        return results.get(0).stream()
                .map(r -> new RetrievedChunk(
                        Objects.toString(r.getEntity().get("id"), ""),
                        Objects.toString(r.getEntity().get("content"), ""),
                        r.getScore()))
                .collect(Collectors.toList());
    }

    /**
     * 将向量列表转换为基础数组。
     *
     * @param list Embedding 服务返回的向量列表。
     * @return float 数组形式的向量。
     */
    private static float[] toArray(List<Float> list) {
        float[] arr = new float[list.size()];
        for (int i = 0; i < list.size(); i++) {
            arr[i] = list.get(i);
        }
        return arr;
    }

    /**
     * 对向量做归一化处理。
     * <p>
     * 当前实现默认不启用，仅作为后续检索调优的预留方法。
     *
     * @param v 原始向量。
     * @return 归一化后的向量。
     */
    @Deprecated
    private static float[] normalize(float[] v) {
        double sum = 0.0;
        for (float x : v) {
            sum += x * x;
        }
        double len = Math.sqrt(sum);
        float[] newV = new float[v.length];
        for (int i = 0; i < v.length; i++) {
            newV[i] = (float) (v[i] / len);
        }
        return newV;
    }
}
