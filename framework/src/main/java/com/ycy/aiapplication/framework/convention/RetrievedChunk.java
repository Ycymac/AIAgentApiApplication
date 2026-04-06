package com.ycy.aiapplication.framework.convention;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * retieved：已检索的
 * RAG命中结果
 * 表示一次向量检索/想关心检索命中的单条chunk
 * 包含原始文档chunk、主键id、得分（余弦相似度/相关性分数）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class RetrievedChunk {

    @Schema(
            description = "唯一标识，向量库主键/文档id"
    )
    private String id;

    @Schema(
            description = "命中的文本chunk"
    )
    private String text;

    @Schema(
            description = "检索命中得分"
    )
    private Float score;
}
