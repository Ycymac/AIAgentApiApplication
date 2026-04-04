package com.ycy.aiapplication.vector.common.constant;

public final class MilvusVectorServiceConstant {

    /**
     * Milvus 字段名常量，避免散落的魔法字符串。
     */
    public static final String FIELD_ID = "id";
    public static final String FIELD_CONTENT = "content";
    public static final String FIELD_METADATA = "metadata";
    public static final String FIELD_EMBEDDING = "embedding";

    /**
     * metadata 字段 key。
     */
    public static final String META_COLLECTION_NAME = "collection_name";
    public static final String META_DOC_ID = "doc_id";
    public static final String META_CHUNK_INDEX = "chunk_index";

    /**
     * 与 Milvus collection schema 中 content 字段长度保持一致。
     */
    public static final int MAX_CONTENT_LENGTH = 65535;
}
