package com.ycy.aiapplication.agent.Service.Impl;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ycy.aiapplication.agent.Service.AgentAsk;
import com.ycy.aiapplication.agent.common.constant.AIPromptConstant;
import com.ycy.aiapplication.agent.common.enums.AIModelEnum;
import com.ycy.aiapplication.agent.common.pojo.ApiEvaluationResp;
import com.ycy.aiapplication.agent.common.pojo.InterviewQuestion;
import com.ycy.aiapplication.agent.dto.req.AnswerEvaluationReqDTO;
import com.ycy.aiapplication.agent.dto.req.InterviewQuestionAskReqDTO;
import com.ycy.aiapplication.agent.dto.req.ReportGenerationReqDTO;
import com.ycy.aiapplication.agent.dto.resp.AnswerEvaluationRespDTO;
import com.ycy.aiapplication.agent.dto.resp.InterviewQuestionAskRespDTO;
import com.ycy.aiapplication.agent.dto.resp.ReportGenerationRespDTO;
import com.ycy.aiapplication.framework.exception.ClientException;
import com.ycy.aiapplication.framework.exception.RemoteException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;


import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;


/**
 * 这里是ai的api调用模块，无需管理记录的存储
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentAskImpl implements AgentAsk {
    //api调用密钥
    @Value("${ai-application.agent.api.key:}")
    private String apiKey;

    private final ExecutorService executorService = new ThreadPoolExecutor(
            Runtime.getRuntime().availableProcessors(),
            Runtime.getRuntime().availableProcessors()*2,
            60L,
            TimeUnit.SECONDS,
            new SynchronousQueue<>(),
            new ThreadPoolExecutor.CallerRunsPolicy()
    );


    @Override
    public InterviewQuestionAskRespDTO giveInterviewQuestions(InterviewQuestionAskReqDTO requestParam) {

        if(StrUtil.isEmpty(requestParam.getForm().getGrade())||StrUtil.isEmpty(requestParam.getForm().getMajor())||StrUtil.isEmpty(requestParam.getForm().getLearningDirection())|| StrUtil.isEmpty(requestParam.getForm().getLearningProgress())){

           throw new RemoteException("用户参数部分未填写，请检查！");
        }
        String description= requestParam.getFormDescription();
        Generation generation = new Generation();
        Message systemMsg = Message.builder()
                .role(Role.ASSISTANT.getValue())
                .content(AIPromptConstant.SYSTEM_ROLE_CONTENT)
                .build();
        Message userMsg = Message.builder()
                .role(Role.USER.getValue())
                .content(description + AIPromptConstant.QUESTION_ASK)
                .build();
        GenerationParam param = GenerationParam.builder()
                .apiKey(apiKey)
                .model(AIModelEnum.QUESTION_AI_MODEL.getModel())
                .messages(Arrays.asList(systemMsg, userMsg))
                .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                .build();

        InterviewQuestionAskRespDTO resp;
        try{
            GenerationResult result = generation.call(param);
            String json = result.getOutput().getChoices().get(0).getMessage().getContent();
            log.info("消息内容：{}",json);
            List<InterviewQuestion> questions = JSON.parseArray(json, InterviewQuestion.class);
            if(questions.size()!=15){
                throw new RuntimeException("题目数量不正确");
            }
            for(InterviewQuestion q:questions){
                if(q.getLevel()<0||q.getLevel()>2){
                    throw new RuntimeException("非法level值");
                }
            }
             resp = InterviewQuestionAskRespDTO.builder()
                    .questions(questions)
                    .build();


        }catch (Exception e){

           if(e instanceof NoApiKeyException)
               log.error("没有apikey，信息：{}",e.getMessage());
           else if (e instanceof ApiException)
               log.error("api出现错误，信息：{}",e.getMessage());
           else if (e instanceof InputRequiredException)
               log.error("需要输入，信息：{}",e.getMessage());
           else
               log.error("其他错误，信息{}",e.getMessage());

           throw new ClientException("系统异常，请稍后再试");
        }

        return resp;
    }

    /**
     * 单个问题评价
     * @param requestParam 问题参数
     * @return 评价面试对象单个问题回答
     * 为了方便测试这里使用public
     */
    @Override
    public AnswerEvaluationRespDTO singleQuestionAnswerEvaluation(AnswerEvaluationReqDTO requestParam) {
       //检验参数
        InterviewQuestion question = requestParam.getQuestion();
        String answer = requestParam.getAnswer();
        if (ObjectUtil.isNull(question)) {
            throw new ClientException("问题为空！请检查");
        }
        if (question.getLevel()<0||question.getLevel()>2|| StrUtil.isEmpty(question.getQuestionDescription())) {
            throw new ClientException("问题描述/评级为空！请检查");
        }
        if(StrUtil.isEmpty(answer)){
            throw new ClientException("回答为空！请检查");
        }
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("题目难度", question.getLevel());
        jsonObject.put("题目描述", question.getQuestionDescription());
        jsonObject.put("面试对象回答", answer);
        String json = jsonObject.toJSONString();

        Generation generation = new Generation();
        Message systemMsg = Message.builder()
                .role(Role.SYSTEM.getValue())
                .content(AIPromptConstant.SYSTEM_ROLE_CONTENT)
                .build();
        Message userMsg = Message.builder()
                .role(Role.USER.getValue())
                .content(json + AIPromptConstant.ANSWER_POINT_GIVE)
                .build();
        GenerationParam param = GenerationParam.builder()
                .apiKey(apiKey)
                .model(AIModelEnum.EVALUATION_AI_MODEL.getModel())
                .messages(Arrays.asList(systemMsg, userMsg))
                .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                .build();
        ApiEvaluationResp apiEvaluationResp;

        try{
            GenerationResult result = generation.call(param);
            String answerJson=result.getOutput().getChoices().get(0).getMessage().getContent();
            log.info("消息内容：{}",answerJson);
            apiEvaluationResp = JSONObject.parseObject(answerJson, ApiEvaluationResp.class);

        }catch (Exception e){

            if(e instanceof NoApiKeyException)
                log.error("没有apikey，信息：{}",e.getMessage());
            else if (e instanceof ApiException)
                log.error("api出现错误，信息：{}",e.getMessage());
            else if (e instanceof InputRequiredException)
                log.error("需要输入，信息：{}",e.getMessage());
            else
                log.error("其他错误，信息{}",e.getMessage());

            throw new ClientException("系统异常，请稍后再试");
        }
        return new AnswerEvaluationRespDTO(requestParam,apiEvaluationResp);
    }

    @Override
    public List<AnswerEvaluationRespDTO> answersEvaluationByAsync(List<AnswerEvaluationReqDTO> requestParams) {
        //边界检查
        if(requestParams == null || requestParams.size() != 15)
            throw new ClientException("评估用参数不足！请检查");
       //为了防止出现线程安全问题，每个问题独立返回结果，最后进行合并
        List<CompletableFuture<AnswerEvaluationRespDTO>> evaluationTasks = requestParams.stream()
                .map(each -> {
                    //每个问题创建执行任务
                    int index =each.getQuestion().getNum();
                    return CompletableFuture
                            .supplyAsync(() -> singleQuestionAnswerEvaluation(each), executorService)
                            .exceptionally(ex -> {
                                log.error("第 {} 个问题评估失败：{}", index, ex.getMessage());
                                throw new CompletionException(ex);
                            });
                })
                //合并为List
                .toList();
        return CompletableFuture.allOf(evaluationTasks.toArray(CompletableFuture[]::new))//整合所有任务
                .thenApply(allTasks->evaluationTasks.stream()
                        .map(CompletableFuture::join)//阻塞等待单个执行结果，拿到单个结果并收集
                        .collect(Collectors.toList()))
                .thenApply(list->{
                    list.sort(Comparator.comparingInt(e -> e.getQuestion().getNum()));
                    return list;
                })//执行排序
                .join();//等待全部完成，返回结果

    }

    /**
     * 计算整体回答分数
     * @param list 所有回答返回的评测
     * @return 整体分数
     */
    private  int calculateScore(List<AnswerEvaluationRespDTO>list){
        double totalScore = 0;
        double totalWeight = 0;

        for (AnswerEvaluationRespDTO dto : list) {
            ApiEvaluationResp r = dto.getApiResp();
            InterviewQuestion q = dto.getQuestion();

            double baseScore =
                    r.getAccuracy() * 0.5 +
                            r.getCompleteness() * 0.25 +
                            r.getLevelOfDetail() * 0.25;

            double weight = switch (q.getLevel()) {
                case 0 -> 1.0;
                case 1 -> 1.2;
                case 2 -> 1.5;
                default -> 1.0;
            };

            totalScore += baseScore * weight;
            totalWeight += weight;
        }

       return  (int) Math.round((totalScore / totalWeight) * 10);
    }

    /**
     * 调用api生成报告
     * @param requestParam 参数
     * @return 报告返回类
     */
    @Override
    public ReportGenerationRespDTO generateInterviewReport(ReportGenerationReqDTO requestParam) {
        if(ObjectUtil.isEmpty(requestParam))throw new ClientException("参数为空");

        //将面试人信息进行提取，总结为json形式，投递给AI生成对应的报告
        int finalScore =calculateScore(requestParam.getAnswerEvaluationRespS());
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("form",requestParam.getForm());
        jsonObject.put("evaluations",requestParam.getAnswerEvaluationRespS());
        jsonObject.put("interviewPoint",finalScore);


        Generation generation = new Generation();

        Message systemMsg = Message.builder()
                .role(Role.SYSTEM.getValue())
                .content(AIPromptConstant.SYSTEM_ROLE_CONTENT)
                .build();
        Message userMsg = Message.builder()
                .role(Role.USER.getValue())
                .content(AIPromptConstant.SUMMARY_ASK + jsonObject.toJSONString())
                .build();

        GenerationParam param = GenerationParam.builder()
                .apiKey(apiKey)
                .model(AIModelEnum.SUMMARY_GENERATE_AI_MODEL.getModel())
                .messages(Arrays.asList(systemMsg, userMsg))
                .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                .build();

        ReportGenerationRespDTO apiInterviewReport;
        try{
            GenerationResult result = generation.call(param);
            String answerJson=result.getOutput().getChoices().get(0).getMessage().getContent();
            log.info("消息内容：{}",answerJson);
             apiInterviewReport= JSONObject.parseObject(answerJson, ReportGenerationRespDTO.class);
             apiInterviewReport.setInterviewPoint(finalScore);

        }catch (Exception e){

            if(e instanceof NoApiKeyException)
                log.error("没有apikey，信息：{}",e.getMessage());
            else if (e instanceof ApiException)
                log.error("api出现错误，信息：{}",e.getMessage());
            else if (e instanceof InputRequiredException)
                log.error("需要输入，信息：{}",e.getMessage());
            else
                log.error("其他错误，信息{}",e.getMessage());

            throw new ClientException("系统异常，请稍后再试");
        }

        return apiInterviewReport;
    }
}

