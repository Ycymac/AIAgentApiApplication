package com.ycy.aiapplication.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 报告生成返回类
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AgentInterviewReportDTO {
    /**
     * 面试对应的总分数，0-100分
     */
    private int interviewPoint;
    /**
     * 总结报告，对面试人的整体情况进行评价
     */
    private String summaryReport;
    /**
     * 建议报告，对面试人的不足进行建议
     */
    private String adviceReport;
}
