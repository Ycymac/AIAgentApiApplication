package com.ycy.aiapplication.controller;


import com.ycy.aiapplication.dto.req.AnswerEvaluationReqDTO;
import com.ycy.aiapplication.dto.req.InterviewQuestionAskReqDTO;
import com.ycy.aiapplication.dto.req.ReportGenerationReqDTO;
import com.ycy.aiapplication.dto.resp.AnswerEvaluationRespDTO;
import com.ycy.aiapplication.dto.resp.InterviewQuestionAskRespDTO;

import com.ycy.aiapplication.dto.resp.ReportGenerationRespDTO;
import com.ycy.aiapplication.framework.web.Result;
import com.ycy.aiapplication.framework.web.Results;

import com.ycy.aiapplication.service.Impl.AgentAskImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "agent面试模块")
@RestController
@RequestMapping("api/agent")
@RequiredArgsConstructor
@SecurityRequirement(name = "Authorization")
public class AgentController {

    private final AgentAskImpl agentAsk;

    @Operation(summary = "生成问题")
    @PostMapping("/questions")
    public Result<InterviewQuestionAskRespDTO> generateInterviewQuestion(@RequestBody InterviewQuestionAskReqDTO requestParam) {
        return Results.success(agentAsk.giveInterviewQuestions(requestParam));
    }

    @Operation(summary = "评估问题")
    @PostMapping("/evaluations")
    public Result<List<AnswerEvaluationRespDTO>> generateAnswerEvaluation(@RequestBody AnswerEvaluationReqDTO requestParam) {
        List<AnswerEvaluationRespDTO> answerEvaluationRespDTOS =
                agentAsk.answersEvaluationByAsync(requestParam.getQuestionWithAnswers());
        return Results.success(answerEvaluationRespDTOS);
    }

    @Operation(summary = "生成报告")
    @PostMapping("/report")
    public Result<ReportGenerationRespDTO> generateReport(@RequestBody ReportGenerationReqDTO requestParam) {
        return Results.success(agentAsk.generateInterviewReportAndRecordName(requestParam));
    }
}
