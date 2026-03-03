package com.ycy.aiapplication.agent.common.pojo;

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
    private String comment;
    /**
     * 完整度 0-10
     */
    private int completeness;
    /**
     * 详细度 0-10
     */
    private int levelOfDetail;
    /**
     * 准确度 0-10
     */
    private int accuracy;

    @Override
    public String toString() {
        return "ApiEvaluationResp{" +
                "accuracy=" + accuracy +
                ", comment='" + comment + '\'' +
                ", completeness=" + completeness +
                ", levelOfDetail=" + levelOfDetail +
                '}';
    }
}
