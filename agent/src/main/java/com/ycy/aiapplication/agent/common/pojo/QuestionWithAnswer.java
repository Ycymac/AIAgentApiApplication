package com.ycy.aiapplication.agent.common.pojo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 问题评价输入类
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class QuestionWithAnswer {
    /**
     * 面试问题
     */
    @Schema(
            description = "面试问题"
    )
    private InterviewQuestion question;
    /**
     * 面试回答
     */
    @Schema(
            description = "面试回答",
            example = "不是，String是Java当中的引用类型。java当中的基本数据类型有 byte、short、int、long、double、float、char、boolean，所有的基本数据类型都有对应的包装类"
    )
    private String answer;
}
