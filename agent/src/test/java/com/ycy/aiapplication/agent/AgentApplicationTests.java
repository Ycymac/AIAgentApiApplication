package com.ycy.aiapplication.agent;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.ycy.aiapplication.agent.Service.AgentAsk;
import com.ycy.aiapplication.agent.common.constant.ApiConstant;
import com.ycy.aiapplication.agent.common.pojo.InterviewQuestion;
import com.ycy.aiapplication.agent.dto.req.AnswerEvaluationReqDTO;
import com.ycy.aiapplication.agent.dto.req.InterviewQuestionAskReqDTO;
import com.ycy.aiapplication.agent.dto.resp.AnswerEvaluationRespDTO;
import com.ycy.aiapplication.agent.dto.resp.InterviewQuestionAskRespDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.List;

@SpringBootTest
class AgentApplicationTests {

    //因为这是对应的密钥，所以写在配置类当中
    @Value("${ai-application.agent.api.key:}")
    private String apiKey;

    @Autowired
    private AgentAsk agentAsk;


    //尝试使用api
    @Test
    void testApi() {
        //测试api使用
        Generation generation = new Generation();
        Message systemMsg = Message.builder()
                .role(Role.SYSTEM.getValue())
                //设定ai角色，行为模式
                .content(ApiConstant.SYSTEM_ROLE_CONTENT)
                .build();
        Message userMsg = Message.builder()
                .role(Role.USER.getValue())
                .content("你好，我是一名大三学生，学习的方向是Java后端开发，学习过的知识点有：1.SpringBoot框架 2.JavaSE基础 3.redis基础，熟悉所有数据类型 3.MySQL使用、底层，尤其是执行引擎相关部分 4.JVM底层知识，例如堆、栈、栈帧、执行引擎、方法区（永久代和元空间）"+ApiConstant.QUESTION_ASK)
                .build();
        GenerationParam param = GenerationParam.builder()
                .apiKey(apiKey)
                .model(ApiConstant.AI_MODEL)
                .messages(Arrays.asList(systemMsg, userMsg))
                .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                .build();
        try {
            GenerationResult result = generation.call(param);
            System.out.println(result);
            System.out.println(result.getMessage());
        } catch (NoApiKeyException e) {
            throw new RuntimeException(e);
        } catch (InputRequiredException e) {
            throw new RuntimeException(e);
        }

    }

    @Test
    void agentAskImpl_giveInterviewQuestions_Test(){
        InterviewQuestionAskReqDTO requestParam = InterviewQuestionAskReqDTO.builder()
                .grade("大二")
                .major("软件工程")
                .learningDirection("java后端开发")
                .learningProgress("学习过的知识点有：1.SpringBoot框架 2.JavaSE基础 3.redis基础，熟悉所有数据类型 3.MySQL使用、底层，尤其是执行引擎相关部分 4.JVM底层知识，例如堆、栈、栈帧、执行引擎、方法区（永久代和元空间）").build();

        InterviewQuestionAskRespDTO interviewQuestionAskResp = agentAsk.giveInterviewQuestions(requestParam);
        List<InterviewQuestion> questions = interviewQuestionAskResp.getQuestions();
        for(InterviewQuestion q:questions){
            System.out.println("level"+q.getLevel()+" question: "+q.getQuestionDescription());
        }


    }
    @Test
    void agentAskImpl_answerEvaluation_Test(){
        InterviewQuestion question = InterviewQuestion.builder()
                .level(0)
                .questionDescription("String是基本数据类型吗？Java当中有哪些基本数据类型？")
                .build();
        AnswerEvaluationReqDTO requestParam = AnswerEvaluationReqDTO.builder()
                .question(question)
                .answer("不是，String是Java当中的引用类型。java当中的基本数据类型有 byte、short、int、long、double、float、char、boolean，所有的基本数据类型都有对应的包装类")
                .build();
        AnswerEvaluationRespDTO answerEvaluationRespDTO = agentAsk.singleQuestionAnswerEvaluation(requestParam);
        System.out.println(answerEvaluationRespDTO.getApiResp().toString());

    }


}
