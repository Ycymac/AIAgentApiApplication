package com.ycy.aiapplication.agent.Service;

import com.ycy.aiapplication.agent.dto.req.AnswerEvaluationReqDTO;
import com.ycy.aiapplication.agent.dto.req.InterviewQuestionAskReqDTO;
import com.ycy.aiapplication.agent.dto.req.ReportGenerationReqDTO;
import com.ycy.aiapplication.agent.dto.resp.AnswerEvaluationRespDTO;
import com.ycy.aiapplication.agent.dto.resp.InterviewQuestionAskRespDTO;
import com.ycy.aiapplication.agent.dto.resp.ReportGenerationRespDTO;

import java.util.List;

/**
 * api调用服务层
 * 三个方法：
 * 1，分析提供的简历，给出对应的15个问题
 * 2.对用户的15回答一一进行评分
 * 3.通过所有的回答+评分生成最终报告
 *
 */
public interface AgentAsk {

    InterviewQuestionAskRespDTO giveInterviewQuestions(InterviewQuestionAskReqDTO requestParam);

    AnswerEvaluationRespDTO singleQuestionAnswerEvaluation(AnswerEvaluationReqDTO requestParam);

    List<AnswerEvaluationRespDTO> answersEvaluationByAsync(List<AnswerEvaluationReqDTO>requestParams);

    ReportGenerationRespDTO generateInterviewReport(ReportGenerationReqDTO requestParam);



}
