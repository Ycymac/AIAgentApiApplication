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

    //返回结果不需要提供面试人回答，后续生成报告，我们也无须提供回答，只需要通过每个问题的面评生成报告
    private ApiEvaluationResp apiResp;

    public AnswerEvaluationRespDTO(AnswerEvaluationReqDTO requestParam, ApiEvaluationResp apiResp) {

        this.apiResp = apiResp;
        this.question = requestParam.getQuestion();
    }
}
