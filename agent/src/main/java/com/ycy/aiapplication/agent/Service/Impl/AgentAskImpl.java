package com.ycy.aiapplication.agent.Service.Impl;

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
import com.ycy.aiapplication.agent.Service.AgentAsk;
import com.ycy.aiapplication.agent.common.constant.ApiConstant;
import com.ycy.aiapplication.agent.common.pojo.InterviewQuestion;
import com.ycy.aiapplication.agent.dto.req.InterviewQuestionAskReq;
import com.ycy.aiapplication.agent.dto.resp.InterviewQuestionAskResp;
import com.ycy.aiapplication.framework.exception.ClientException;
import com.ycy.aiapplication.framework.exception.RemoteException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;


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
    @Override
    public InterviewQuestionAskResp giveInterviewQuestions(InterviewQuestionAskReq requestParam) {

        if(StrUtil.isEmpty(requestParam.getGrade())||StrUtil.isEmpty(requestParam.getMajor())||StrUtil.isEmpty(requestParam.getLearningDirection())|| StrUtil.isEmpty(requestParam.getLearningProgress())){

           throw new RemoteException("用户参数部分未填写，请检查！");
        }
        String description= requestParam.getDescription();
        Generation generation = new Generation();
        Message systemMsg = Message.builder()
                .role(Role.SYSTEM.getValue())
                .content(ApiConstant.SYSTEM_ROLE_CONTENT)
                .build();
        Message userMsg = Message.builder()
                .role(Role.USER.getValue())
                .content(description + ApiConstant.QUESTION_ASK)
                .build();
        GenerationParam param = GenerationParam.builder()
                .apiKey(apiKey)
                .model(ApiConstant.AI_MODEL)
                .messages(Arrays.asList(systemMsg, userMsg))
                .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                .build();

        InterviewQuestionAskResp resp=null;
        try{
            GenerationResult result = generation.call(param);
            String json = result.getOutput().getChoices().get(0).getMessage().getContent();
            log.info("返回结果：{}\n消息内容：{}",result,json);
            List<InterviewQuestion> questions = JSON.parseArray(json, InterviewQuestion.class);
            if(questions.size()!=15){
                throw new RuntimeException("题目数量不正确");
            }
            for(InterviewQuestion q:questions){
                if(q.getLevel()<0||q.getLevel()>2){
                    throw new RuntimeException("非法level值");
                }
            }
             resp = InterviewQuestionAskResp.builder()
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
               log.error("未知错误，信息{}",e.getMessage());

           throw new ClientException("系统异常，请稍后再试");
        }
        return resp;
    }
}

