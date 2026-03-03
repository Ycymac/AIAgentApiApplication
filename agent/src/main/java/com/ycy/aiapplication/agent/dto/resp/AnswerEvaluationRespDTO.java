package com.ycy.aiapplication.agent.dto.resp;

import com.ycy.aiapplication.agent.common.pojo.ApiEvaluationResp;
import com.ycy.aiapplication.agent.common.pojo.InterviewQuestion;
import com.ycy.aiapplication.agent.dto.req.AnswerEvaluationReqDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnswerEvaluationRespDTO {
    /**
     * 面试问题
     */
    private InterviewQuestion question;
    /**
     * 面试回答
     */
    private String answer;

    private ApiEvaluationResp apiResp;

    public AnswerEvaluationRespDTO(AnswerEvaluationReqDTO requestParam, ApiEvaluationResp apiResp) {
        this.answer = requestParam.getAnswer();
        this.apiResp = apiResp;
        this.question = requestParam.getQuestion();
    }
}
