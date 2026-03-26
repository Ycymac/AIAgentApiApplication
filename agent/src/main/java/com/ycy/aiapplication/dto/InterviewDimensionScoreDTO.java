package com.ycy.aiapplication.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 面试五维总分与综合总分
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class InterviewDimensionScoreDTO {
    /**
     * 综合得分，0-100
     */
    private int interviewPoint;

    /**
     * 准确度总分，10分制
     */
    private int accuracyScore;

    /**
     * 完整度总分，10分制
     */
    private int completenessScore;

    /**
     * 详细度总分，10分制
     */
    private int levelOfDetailScore;

    /**
     * 逻辑度总分，10分制
     */
    private int logicScore;

    /**
     * 表达能力总分，10分制
     */
    private int expressionAbilityScore;
}
