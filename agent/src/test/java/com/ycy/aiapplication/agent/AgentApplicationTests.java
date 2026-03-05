package com.ycy.aiapplication.agent;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.ycy.aiapplication.agent.service.AgentAsk;
import com.ycy.aiapplication.agent.common.constant.AIPromptConstant;
import com.ycy.aiapplication.agent.common.enums.AIModelEnum;
import com.ycy.aiapplication.agent.common.pojo.ApiEvaluationResp;
import com.ycy.aiapplication.agent.common.pojo.InterviewQuestion;
import com.ycy.aiapplication.agent.common.pojo.IntervieweeForm;
import com.ycy.aiapplication.agent.dto.req.AnswerEvaluationReqDTO;
import com.ycy.aiapplication.agent.dto.req.InterviewQuestionAskReqDTO;
import com.ycy.aiapplication.agent.dto.req.ReportGenerationReqDTO;
import com.ycy.aiapplication.agent.dto.resp.AnswerEvaluationRespDTO;
import com.ycy.aiapplication.agent.dto.resp.InterviewQuestionAskRespDTO;
import com.ycy.aiapplication.agent.dto.AgentInterviewReportDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.List;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.autoconfigure.exclude=org.redisson.spring.starter.RedissonAutoConfigurationV2"
        })
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
                .content(AIPromptConstant.SYSTEM_ROLE_CONTENT)
                .build();
        Message userMsg = Message.builder()
                .role(Role.USER.getValue())
                .content("你好，我是一名大三学生，学习的方向是Java后端开发，学习过的知识点有：1.SpringBoot框架 2.JavaSE基础 3.redis基础，熟悉所有数据类型 3.MySQL使用、底层，尤其是执行引擎相关部分 4.JVM底层知识，例如堆、栈、栈帧、执行引擎、方法区（永久代和元空间）"+ AIPromptConstant.QUESTION_ASK)
                .build();
        GenerationParam param = GenerationParam.builder()
                .apiKey(apiKey)
                .model(AIModelEnum.QUESTION_AI_MODEL.getModel())
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
        IntervieweeForm form = IntervieweeForm
                .builder()
                .grade("大二")
                .major("软件工程")
                .learningDirection("java后端开发")
                .learningProgress("学习过的知识点有：1.SpringBoot框架 2.JavaSE基础 3.redis基础，熟悉所有数据类型 3.MySQL使用、底层，尤其是执行引擎相关部分 4.JVM底层知识，例如堆、栈、栈帧、执行引擎、方法区（永久代和元空间）").build();

        InterviewQuestionAskReqDTO requestParam = InterviewQuestionAskReqDTO.builder()
                .form(form)
                .build();

        InterviewQuestionAskRespDTO interviewQuestionAskResp = agentAsk.giveInterviewQuestions(requestParam);
        List<InterviewQuestion> questions = interviewQuestionAskResp.getQuestions();
        for(InterviewQuestion q:questions){
            System.out.println("num "+q.getNum()+" level "+q.getLevel()+" question: "+q.getQuestionDescription());
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

    @Test
    void agentAskImpl_generateInterviewReport_Test(){
        // 1. 准备面试者信息（与其他测试方法保持一致）
        IntervieweeForm form = IntervieweeForm
                .builder()
                .grade("大二")
                .major("软件工程")
                .learningDirection("java 后端开发")
                .learningProgress("学习过的知识点有：1.SpringBoot 框架 2.JavaSE 基础 3.redis 基础，熟悉所有数据类型 3.MySQL 使用、底层，尤其是执行引擎相关部分 4.JVM 底层知识，例如堆、栈、栈帧、执行引擎、方法区（永久代和元空间）")
                .build();

        // 2. 准备 15 道面试问题（按照用户提供的问题列表）
        List<InterviewQuestion> questions = Arrays.asList(
                InterviewQuestion.builder()
                        .num(1)
                        .level(0)
                        .questionDescription("请简述 SpringBoot 框架的主要优点及其核心特性。")
                        .build(),
                InterviewQuestion.builder()
                        .num(2)
                        .level(0)
                        .questionDescription("Java 中常见的数据类型有哪些？基本数据类型和引用类型的区别是什么？")
                        .build(),
                InterviewQuestion.builder()
                        .num(3)
                        .level(0)
                        .questionDescription("Redis 支持哪些数据类型？请列举并简要说明每种类型的使用场景。")
                        .build(),
                InterviewQuestion.builder()
                        .num(4)
                        .level(0)
                        .questionDescription("MySQL 中常用的存储引擎有哪些？它们各自的特点和适用场景是什么？")
                        .build(),
                InterviewQuestion.builder()
                        .num(5)
                        .level(0)
                        .questionDescription("JVM 的内存结构包括哪些部分？请简述堆、栈、方法区的作用。")
                        .build(),
                InterviewQuestion.builder()
                        .num(6)
                        .level(1)
                        .questionDescription("SpringBoot 中如何实现自动配置（Auto-Configuration）？请结合@ConditionalOnClass 等注解说明其原理。")
                        .build(),
                InterviewQuestion.builder()
                        .num(7)
                        .level(1)
                        .questionDescription("在 MySQL 中，索引底层是如何实现的？B+ 树相比 B 树有什么优势？")
                        .build(),
                InterviewQuestion.builder()
                        .num(8)
                        .level(1)
                        .questionDescription("Redis 的持久化机制有哪些？RDB 和 AOF 的区别是什么？")
                        .build(),
                InterviewQuestion.builder()
                        .num(9)
                        .level(1)
                        .questionDescription("JVM 中垃圾回收的常见算法有哪些？标记 - 清除、复制、标记 - 整理分别适用于什么场景？")
                        .build(),
                InterviewQuestion.builder()
                        .num(10)
                        .level(1)
                        .questionDescription("什么是 MySQL 的执行计划？如何通过 EXPLAIN 命令分析查询性能？")
                        .build(),
                InterviewQuestion.builder()
                        .num(11)
                        .level(2)
                        .questionDescription("在高并发场景下，Redis 如何保证数据一致性？请说明缓存穿透、缓存击穿、缓存雪崩的解决方案。")
                        .build(),
                InterviewQuestion.builder()
                        .num(12)
                        .level(2)
                        .questionDescription("SpringBoot 中如何实现自定义 Starter？请描述其核心机制与实现步骤。")
                        .build(),
                InterviewQuestion.builder()
                        .num(13)
                        .level(2)
                        .questionDescription("JVM 中的元空间（Metaspace）替代永久代（PermGen）的原因是什么？它与永久代的主要区别有哪些？")
                        .build(),
                InterviewQuestion.builder()
                        .num(14)
                        .level(2)
                        .questionDescription("MySQL 中事务的隔离级别有哪些？它们分别解决了哪些并发问题？请结合具体例子说明。")
                        .build(),
                InterviewQuestion.builder()
                        .num(15)
                        .level(2)
                        .questionDescription("假设你正在设计一个基于 SpringBoot 和 Redis 的高可用系统，请从架构层面说明如何解决分布式环境下的缓存一致性与数据一致性问题。")
                        .build()
        );

        // 3. 为每个问题准备回答和评价（模拟真实的面试回答）
        List<AnswerEvaluationRespDTO> evaluations = Arrays.asList(
                // 问题 1 的回答评价
                createMockEvaluation(questions.get(0),
                        8, 7, 8),

                // 问题 2 的回答评价
                createMockEvaluation(questions.get(1),
                        9, 8, 8),

                // 问题 3 的回答评价
                createMockEvaluation(questions.get(2),
                        9, 9, 9),

                // 问题 4 的回答评价
                createMockEvaluation(questions.get(3),
                        8, 8, 8),

                // 问题 5 的回答评价
                createMockEvaluation(questions.get(4),
                        9, 9, 9),

                // 问题 6 的回答评价
                createMockEvaluation(questions.get(5),
                        7, 7, 7),

                // 问题 7 的回答评价
                createMockEvaluation(questions.get(6),
                        8, 8, 8),

                // 问题 8 的回答评价
                createMockEvaluation(questions.get(7),
                        8, 8, 8),

                // 问题 9 的回答评价
                createMockEvaluation(questions.get(8),
                        8, 8, 8),

                // 问题 10 的回答评价
                createMockEvaluation(questions.get(9),
                        7, 7, 7),

                // 问题 11 的回答评价
                createMockEvaluation(questions.get(10),
                        8, 8, 8),

                // 问题 12 的回答评价
                createMockEvaluation(questions.get(11),
                        7, 7, 7),

                // 问题 13 的回答评价
                createMockEvaluation(questions.get(12),
                        8, 8, 8),

                // 问题 14 的回答评价
                createMockEvaluation(questions.get(13),
                        9, 9, 9),

                // 问题 15 的回答评价
                createMockEvaluation(questions.get(14),
                        8, 8, 8)
        );

        // 4. 构建请求参数
        ReportGenerationReqDTO requestParam = ReportGenerationReqDTO.builder()
                .form(form)
                .answerEvaluationRespS(evaluations)
                .build();

        // 5. 调用方法生成面试报告
        try {
            AgentInterviewReportDTO report = agentAsk.generateInterviewReportAndRecordName(requestParam).getReportDTO();

            // 6. 输出结果验证
            System.out.println("========== 面试报告生成成功 ==========");
            System.out.println("面试者：" + form.getGrade() + " " + form.getMajor() + " " + form.getLearningDirection());
            System.out.println("综合得分：" + report.getInterviewPoint());
            System.out.println("总体评价：" + report.getSummaryReport());
            System.out.println("改进建议：" + report.getAdviceReport());
            System.out.println("======================================");

        } catch (Exception e) {
            System.err.println("生成面试报告失败：" + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 创建模拟的评价响应（辅助方法）
     */
    private AnswerEvaluationRespDTO createMockEvaluation(InterviewQuestion question,
                                                         int accuracy,
                                                         int completeness,
                                                         int levelOfDetail) {
        ApiEvaluationResp apiResp = ApiEvaluationResp.builder()
                .comment("回答基本正确，对核心概念理解清晰，但较为深入的部分回答的较为模糊。")
                .accuracy(accuracy)
                .completeness(completeness)
                .levelOfDetail(levelOfDetail)
                .build();

        return AnswerEvaluationRespDTO.builder()
                .question(question)
                .apiResp(apiResp)
                .build();
    }


}
