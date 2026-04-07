/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ycy.aiapplication.vector.impl;

import cn.hutool.core.lang.Assert;
import com.ycy.aiapplication.framework.exception.ClientException;
import com.ycy.aiapplication.vector.VectorStoreAdmin;
import com.ycy.aiapplication.vector.config.RAGDefaultProperties;
import com.ycy.aiapplication.vector.common.VectorSpaceId;
import com.ycy.aiapplication.vector.common.VectorSpaceSpec;
import com.ycy.aiapplication.vector.exception.VectorCollectionAlreadyExistsException;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.ConsistencyLevel;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.DropCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.request.ReleaseCollectionReq;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rag.vector.type", havingValue = "milvus", matchIfMissing = true)
public class MilvusVectorStoreAdmin implements VectorStoreAdmin {

    private final MilvusClientV2 milvusClient;
    private final RAGDefaultProperties ragDefaultProperties;

    @Override
    public void ensureVectorSpace(VectorSpaceSpec spec) {
        String logicalName = spec.getSpaceId().getLogicalName();
        boolean exists = Boolean.TRUE.equals(milvusClient.hasCollection(
                HasCollectionReq.builder().collectionName(logicalName).build()
        ));
        if (exists) {
            throw new VectorCollectionAlreadyExistsException(logicalName);
        }
        //定义字段schema（数据结构设计）
        List<CreateCollectionReq.FieldSchema> fieldSchemaList = new ArrayList<>();

        fieldSchemaList.add(
                CreateCollectionReq.FieldSchema.builder()
                        .name("id")
                        .dataType(DataType.VarChar)
                        .maxLength(36)
                        .isPrimaryKey(true)
                        .autoID(false)
                        .build()
        );

        fieldSchemaList.add(
                CreateCollectionReq.FieldSchema.builder()
                        .name("content")
                        .dataType(DataType.VarChar)
                        .maxLength(65535)
                        .build()
        );

        fieldSchemaList.add(
                CreateCollectionReq.FieldSchema.builder()
                        .name("metadata")
                        .dataType(DataType.JSON)
                        .build()
        );

        fieldSchemaList.add(
                CreateCollectionReq.FieldSchema.builder()
                        .name("embedding")
                        .dataType(DataType.FloatVector)
                        .dimension(ragDefaultProperties.getDimension())
                        .build()
        );

        CreateCollectionReq.CollectionSchema collectionSchema = CreateCollectionReq.CollectionSchema
                .builder()
                .fieldSchemaList(fieldSchemaList)
                .build();

        //构建索引参数
        //使用HNSW作为索引算法
        IndexParam hnswIndex = IndexParam.builder()
                .fieldName("embedding")
                .indexType(IndexParam.IndexType.HNSW)//可分层导航小地图算法
                .metricType(IndexParam.MetricType.COSINE)//相似度算法：余弦算法
                .indexName("embedding")
                .extraParams(Map.of(
                        "M", "48",//每个结点最大边数
                        "efConstruction", "200",//创建时候的选集大小
                        "mmap.enabled", "false"//禁用内存映射（占用纯内存，更快，也更占内存）
                ))
                .build();

        CreateCollectionReq createReq = CreateCollectionReq.builder()
                .collectionName(logicalName)
                .collectionSchema(collectionSchema)
                .primaryFieldName("id")
                .vectorFieldName("embedding")
                .metricType(ragDefaultProperties.getMetricType())
                .consistencyLevel(ConsistencyLevel.BOUNDED)
                .indexParams(List.of(hnswIndex))
                .description(spec.getRemark())
                .build();

        milvusClient.createCollection(createReq);
    }

    @Override
    public boolean vectorSpaceExists(VectorSpaceId spaceId) {
        String logicalName = spaceId.getLogicalName();
        return milvusClient.hasCollection(
                HasCollectionReq.builder().collectionName(logicalName).build()
        );
    }

    @Override
    public void deleteVectorSpace(VectorSpaceId spaceId) {
        Assert.notNull(spaceId, () -> new ClientException("VectorSpaceId不能为空"));
        String logicalName = spaceId.getLogicalName();
        Assert.notBlank(logicalName, () -> new ClientException("Collection name不能为空"));
        if (!Boolean.TRUE.equals(milvusClient.hasCollection(
                HasCollectionReq.builder().collectionName(logicalName).build()
        ))) {
            log.info("Milvus collection不存在，跳过删除, collection={}", logicalName);
            return;
        }

        milvusClient.releaseCollection(ReleaseCollectionReq.builder()
                .collectionName(logicalName)
                .build());
        milvusClient.dropCollection(DropCollectionReq.builder()
                .collectionName(logicalName)
                .build());
        log.info("Milvus collection删除成功, collection={}", logicalName);
    }
}
