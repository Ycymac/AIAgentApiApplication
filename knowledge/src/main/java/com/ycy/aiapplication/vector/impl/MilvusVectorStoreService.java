package com.ycy.aiapplication.vector.impl;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.IdUtil;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.ycy.aiapplication.chunk.VectorChunk;
import com.ycy.aiapplication.framework.exception.ClientException;
import com.ycy.aiapplication.vector.VectorStoreService;
import com.ycy.aiapplication.vector.config.RAGDefaultProperties;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.UpsertReq;
import io.milvus.v2.service.vector.response.DeleteResp;
import io.milvus.v2.service.vector.response.InsertResp;
import io.milvus.v2.service.vector.response.UpsertResp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import static com.ycy.aiapplication.vector.common.constant.MilvusVectorServiceConstant.*;

/**
 * Milvus 向量存储服务。
 *
 * <p>仅负责将业务侧的 {@link VectorChunk} 转换为 Milvus 所需的行结构，
 * 并执行 insert、upsert、delete 等操作。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rag.vector.type", havingValue = "milvus", matchIfMissing = true)
public class MilvusVectorStoreService implements VectorStoreService {

    private static final Gson GSON = new Gson();


    private final MilvusClientV2 milvusClient;
    private final RAGDefaultProperties ragDefaultProperties;

    /**
     * 批量写入文档切片向量。
     */
    @Override
    public void indexDocumentChunks(String collectionName, String docId, List<VectorChunk> chunks) {
        validateCollectionName(collectionName);
        validateDocId(docId);
        Assert.isFalse(chunks == null || chunks.isEmpty(), () -> new ClientException("文档切片不能为空"));
        //构建向量数据库行数据
        List<JsonObject> rows = new ArrayList<>(chunks.size());
        for (VectorChunk chunk : chunks) {
            rows.add(buildRow(collectionName, docId, chunk));
        }

        InsertReq req = InsertReq.builder()
                .collectionName(collectionName)
                .data(rows)
                .build();

        InsertResp resp = milvusClient.insert(req);
        log.info("Milvus 批量写入向量成功, collection={}, rows={}", collectionName, resp.getInsertCnt());
    }

    /**
     * 单条切片更新，底层使用 upsert 保证主键存在时更新，不存在时插入。
     */
    @Override
    public void updateChunk(String collectionName, String docId, VectorChunk chunk) {
        validateCollectionName(collectionName);
        validateDocId(docId);
        Assert.notNull(chunk, () -> new ClientException("Chunk对象不能为空"));

        JsonObject row = buildRow(collectionName, docId, chunk);
        String chunkId = row.get(FIELD_ID).getAsString();

        UpsertReq req = UpsertReq.builder()
                .collectionName(collectionName)
                .data(List.of(row))
                .build();

        UpsertResp resp = milvusClient.upsert(req);
        log.info("Milvus 更新 chunk 向量成功, collection={}, docId={}, chunkId={}, upsertCnt={}",
                collectionName, docId, chunkId, resp.getUpsertCnt());
    }

    /**
     * 按文档删除该文档下的全部向量。
     */
    @Override
    public void deleteDocumentVectors(String collectionName, String docId) {
        validateCollectionName(collectionName);
        validateDocId(docId);

        DeleteReq req = DeleteReq.builder()
                .collectionName(collectionName)
                .filter(buildDocumentFilter(docId))
                .build();

        DeleteResp resp = milvusClient.delete(req);
        log.info("Milvus 删除文档向量成功, collection={}, docId={}, deleteCnt={}",
                collectionName, docId, resp.getDeleteCnt());
    }

    /**
     * 按 chunk 主键删除单条向量。
     */
    @Override
    public void deleteChunkById(String collectionName, String chunkId) {
        validateCollectionName(collectionName);
        Assert.notBlank(chunkId, () -> new ClientException("ChunkId不能为空"));

        DeleteReq req = DeleteReq.builder()
                .collectionName(collectionName)
                .filter(buildChunkFilter(chunkId))
                .build();

        DeleteResp resp = milvusClient.delete(req);
        log.info("Milvus 删除 chunk 向量成功, collection={}, chunkId={}, deleteCnt={}",
                collectionName, chunkId, resp.getDeleteCnt());
    }

    /**
     * 统一构建 Milvus row，收敛 insert / upsert 的重复组装逻辑。
     */
    private JsonObject buildRow(String collectionName, String docId, VectorChunk chunk) {
        Assert.notNull(chunk, () -> new ClientException("Chunk对象不能为空"));

        String chunkId = chunk.getChunkId() != null ? chunk.getChunkId() : IdUtil.getSnowflakeNextIdStr();
        float[] vector = extractVector(chunk, ragDefaultProperties.getDimension());

        JsonObject row = new JsonObject();
        row.addProperty(FIELD_ID, chunkId);
        row.addProperty(FIELD_CONTENT, normalizeContent(chunk.getContent()));
        row.add(FIELD_METADATA, buildMetadata(collectionName, docId, chunk));
        row.add(FIELD_EMBEDDING, toJsonArray(vector));
        return row;
    }

    /**
     * 校验并提取单条 chunk 的 embedding。
     */
    private float[] extractVector(VectorChunk chunk, int expectedDim) {
        float[] vector = chunk.getEmbedding();
        if (vector == null || vector.length == 0) {
            throw new ClientException("向量不能为空");
        }
        if (vector.length != expectedDim) {
            throw new ClientException("向量维度不匹配，期望维度为：" + expectedDim);
        }
        return vector;
    }

    /**
     * 统一处理 content 长度，避免 insert / upsert 的截断规则不一致。
     */
    private String normalizeContent(String content) {
        String finalContent = content == null ? "" : content;
        return finalContent.length() > MAX_CONTENT_LENGTH
                ? finalContent.substring(0, MAX_CONTENT_LENGTH)
                : finalContent;
    }

    /**
     * 将向量数组转换为 Milvus SDK 所需的 JsonArray。
     */
    private JsonArray toJsonArray(float[] vector) {
        JsonArray arr = new JsonArray(vector.length);
        for (float item : vector) {
            arr.add(item);
        }
        return arr;
    }

    /**
     * 构建 metadata，合并 chunk 原始 metadata 与系统字段。
     */
    private JsonObject buildMetadata(String collectionName, String docId, VectorChunk chunk) {
        JsonObject metadata = new JsonObject();
        if (chunk.getMetadata() != null) {
            chunk.getMetadata().forEach((key, value) -> metadata.add(key, GSON.toJsonTree(value)));
        }
        metadata.addProperty(META_COLLECTION_NAME, collectionName);
        metadata.addProperty(META_DOC_ID, docId);
        metadata.addProperty(META_CHUNK_INDEX, chunk.getIndex());
        return metadata;
    }

    /**
     * 构建按 docId 删除的过滤条件。
     */
    private String buildDocumentFilter(String docId) {
        return "metadata[\"" + META_DOC_ID + "\"] == \"" + docId + "\"";
    }

    /**
     * 构建按主键删除的过滤条件。
     */
    private String buildChunkFilter(String chunkId) {
        return FIELD_ID + " == \"" + chunkId + "\"";
    }

    /**
     * 断言检查向量数据库Collection名称是否为空
     */
    private void validateCollectionName(String collectionName) {
        Assert.notBlank(collectionName, () -> new ClientException("Collection name不能为空"));
    }

    /**
     * 检查DocId是否为空
     */
    private void validateDocId(String docId) {
        Assert.notBlank(docId, () -> new ClientException("DocId不能为空"));
    }
}
