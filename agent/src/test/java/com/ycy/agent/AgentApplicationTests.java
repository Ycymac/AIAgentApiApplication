package com.ycy.agent;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.ycy.agent.common.constant.ApiConstant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;

@SpringBootTest
class AgentApplicationTests {

    @Value("${ai-application.agent.api.key}")
    private String apiKey;

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
        } catch (NoApiKeyException e) {
            throw new RuntimeException(e);
        } catch (InputRequiredException e) {
            throw new RuntimeException(e);
        }

    }

}
