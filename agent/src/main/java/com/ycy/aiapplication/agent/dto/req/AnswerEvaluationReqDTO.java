package com.ycy.aiapplication.agent.dto.req;

import com.ycy.aiapplication.agent.common.pojo.InterviewQuestion;
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
public class AnswerEvaluationReqDTO {
    /**
     * 面试问题
     */
    private InterviewQuestion question;
    /**
     * 面试回答
     */
    private String answer;
}
