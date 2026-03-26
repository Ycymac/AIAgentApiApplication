package com.ycy.aiapplication.common.pojo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * api对应回答
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiEvaluationResp {
    /**
     * 面试官评价
     */
    @Schema(
            description = "api评价",
            example = "回答基本正确，对核心概念理解清晰，但较为深入的部分回答的较为模糊。"

    )
    private String comment;
    /**
     * 完整度 0-10
     */
    @Schema(
            description = "完整度",
            example = "10"

    )
    private int completeness;
    /**
     * 详细度 0-10
     */
    @Schema(
            description = "详细度",
            example = "9"

    )
    private int levelOfDetail;
    /**
     * 准确度 0-10
     */
    @Schema(
            description = "准确度",
            example = "10"

    )
    private int accuracy;

    @Schema(
            description = "逻辑度",
            example = "8"

    )
    private int logic;

    @Schema(
            description = "表达能力",
            example = "7"

    )
    private int expressionAbility;

    @Override
    public String toString() {
        return "ApiEvaluationResp{" +
                "comment='" + comment + '\'' +
                ", accuracy=" + accuracy +
                ", completeness=" + completeness +
                ", levelOfDetail=" + levelOfDetail +
                ", logic=" + logic +
                ", expressionAbility=" + expressionAbility +
                '}';
    }
}
