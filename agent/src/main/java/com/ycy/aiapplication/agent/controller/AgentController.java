package com.ycy.aiapplication.agent.controller;

import com.ycy.aiapplication.agent.Service.Impl.AgentAskImpl;
import com.ycy.aiapplication.agent.dto.req.AnswerEvaluationReqDTO;
import com.ycy.aiapplication.agent.dto.req.InterviewQuestionAskReqDTO;
import com.ycy.aiapplication.agent.dto.req.ReportGenerationReqDTO;
import com.ycy.aiapplication.agent.dto.resp.AnswerEvaluationRespDTO;
import com.ycy.aiapplication.agent.dto.resp.InterviewQuestionAskRespDTO;
import com.ycy.aiapplication.agent.dto.resp.ReportGenerationRespDTO;
import com.ycy.aiapplication.framework.web.Result;
import com.ycy.aiapplication.framework.web.Results;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/agent")
@RequiredArgsConstructor
public class AgentController {
    private final AgentAskImpl agentAsk;

    @PostMapping("/questions")
    public Result<InterviewQuestionAskRespDTO> generateInterviewQuestion(@RequestBody InterviewQuestionAskReqDTO requestParam){
        return Results.success(agentAsk.giveInterviewQuestions(requestParam));
    }

    @PostMapping("/evaluations")
    public Result<List<AnswerEvaluationRespDTO>> generateAnswerEvaluation(List<AnswerEvaluationReqDTO>requestParams){
        return Results.success(agentAsk.answersEvaluationByAsync(requestParams));
    }

    @PostMapping("/report")
    public Result<ReportGenerationRespDTO> generateReport(ReportGenerationReqDTO requestParam){
        return Results.success(agentAsk.generateInterviewReport(requestParam));
    }
}
