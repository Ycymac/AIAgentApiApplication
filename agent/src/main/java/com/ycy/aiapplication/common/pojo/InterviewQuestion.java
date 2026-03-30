package com.ycy.aiapplication.common.pojo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class InterviewQuestion {
    /**
     * 问题编号
     */
    @Schema(
            description = "问题编号",
            example = "1"

    )
    private int num;
    /**
     * 问题对应的等级
     */
    @Schema(
            description = "面试问题等级",
            example = "0"

    )
    private int level;
    /**
     * 问题对应的问题描述
     */
    @Schema(
            description = "面试问题",
            example = "String是基本数据类型吗？Java当中有哪些基本数据类型？"
    )
    private String questionDescription;

}