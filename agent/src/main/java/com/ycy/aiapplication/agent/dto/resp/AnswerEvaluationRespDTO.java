package com.ycy.aiapplication.agent.dto.resp;

import com.ycy.aiapplication.agent.common.pojo.ApiEvaluationResp;
import com.ycy.aiapplication.agent.common.pojo.InterviewQuestion;
import com.ycy.aiapplication.agent.common.pojo.QuestionWithAnswer;
import io.swagger.v3.oas.annotations.media.Schema;
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
    @Schema(
            description = "面试问题"
    )
    private InterviewQuestion question;
    /**
     * 面试对象回答
     */
    @Schema(
            description = "面试对象回答",
            example = "不是，String是Java当中的引用类型。java当中的基本数据类型有 byte、short、int、long、double、float、char、boolean，所有的基本数据类型都有对应的包装类"

    )
    private String answer;
    /**
     * api反馈
     */
    @Schema(
            description = "api反馈"
    )
    private ApiEvaluationResp apiResp;

    public AnswerEvaluationRespDTO(QuestionWithAnswer requestParam, ApiEvaluationResp apiResp) {
        this.answer=requestParam.getAnswer();
        this.apiResp = apiResp;
        this.question = requestParam.getQuestion();
    }
}
