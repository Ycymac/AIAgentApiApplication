
package com.ycy.aiapplication.service.Impl;

import cn.hutool.core.collection.CollectionUtil;
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
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ycy.aiapplication.common.constant.AIPromptConstant;
import com.ycy.aiapplication.common.constant.AgentRedisConstant;
import com.ycy.aiapplication.common.enums.AIModelEnum;

import com.ycy.aiapplication.common.pojo.ApiEvaluationResp;
import com.ycy.aiapplication.common.pojo.InterviewQuestion;
import com.ycy.aiapplication.common.pojo.IntervieweeForm;

import com.ycy.aiapplication.common.pojo.QuestionWithAnswer;
import com.ycy.aiapplication.dao.entity.InterviewRecordDO;
import com.ycy.aiapplication.dao.mapper.InterviewRecordDOMapper;
import com.ycy.aiapplication.dto.AgentInterviewReportDTO;
import com.ycy.aiapplication.dto.InterviewDimensionScoreDTO;
import com.ycy.aiapplication.dto.req.InterviewQuestionAskReqDTO;
import com.ycy.aiapplication.dto.req.ReportGenerationReqDTO;
import com.ycy.aiapplication.dto.resp.AnswerEvaluationRespDTO;
import com.ycy.aiapplication.dto.resp.InterviewQuestionAskRespDTO;

import com.ycy.aiapplication.dto.resp.ReportGenerationRespDTO;
import com.ycy.aiapplication.framework.exception.ClientException;
import com.ycy.aiapplication.framework.exception.RemoteException;
import com.ycy.aiapplication.service.AgentAsk;
import com.ycy.aiapplication.framework.context.UserContext;
import com.ycy.aiapplication.user.service.common.pojo.EducationExperience;
import com.ycy.aiapplication.user.service.common.pojo.ProjectExperience;
import com.ycy.aiapplication.user.service.common.pojo.WorkExperience;
import com.ycy.aiapplication.user.service.dao.entity.IntervieweeFormDO;
import com.ycy.aiapplication.user.service.dao.mapper.IntervieweeFormDOMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentAskImpl implements AgentAsk {

    private static final int AI_TASK_PARALLELISM = Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors()));

    private static final int AI_TASK_QUEUE_CAPACITY = 64;

    private static final int MODEL_MAX_ATTEMPTS = 2;

    private static final int MIN_ANSWER_LENGTH_FOR_AI = 12;

    private static final String FALLBACK_COMMENT_PREFIX = "【系统兜底】";

    @Value("${ai-application.agent.api.key:}")
    private String apiKey;

    /**
     * AI 调用以网络 I/O 为主。
     * 使用固定并发度 + 有界队列，避免旧方案在高峰期出现请求线程回退执行、
     * CompletionException 级联放大以及并发度失控的问题。
     */
    private final ExecutorService aiTaskExecutor = new ThreadPoolExecutor(
            AI_TASK_PARALLELISM,
            AI_TASK_PARALLELISM,
            60L,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(AI_TASK_QUEUE_CAPACITY),
            new NamedThreadFactory("agent-ai-"),
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    private final InterviewRecordDOMapper interviewRecordDOMapper;

    private final IntervieweeFormDOMapper intervieweeFormDOMapper;

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 生成面试题
     * 基于候选人简历信息，调用大模型生成15道结构化面试题目
     *
     * @param requestParam 包含简历ID的请求参数
     * @return 包含15道面试题的响应对象
     */
    @Override
    public InterviewQuestionAskRespDTO giveInterviewQuestions(InterviewQuestionAskReqDTO requestParam) {
        // 步骤1：校验请求参数非空
        if (ObjectUtil.isNull(requestParam)) {
            throw new RemoteException("用户参数部分未填写，请检查");
        }

        // 步骤2：根据简历ID查询候选人完整信息
        IntervieweeForm form = getIntervieweeFormById(requestParam.getFormId());
        
        // 步骤3：校验简历关键信息完整性（姓名、求职意向、技能、教育经历、项目经历）
        if (StrUtil.isEmpty(form.getCandidateName())
                || StrUtil.isEmpty(form.getJobIntention())
                || CollectionUtil.isEmpty(form.getProfessionalSkills())
                || CollectionUtil.isEmpty(form.getEducationExperiences())
                || CollectionUtil.isEmpty(form.getProjectExperiences())) {
            throw new RemoteException("用户参数部分未填写，请检查");
        }

        // 步骤4：构建简历描述文本，作为大模型的输入上下文
        String description = buildFormDescription(form);
        
        // 步骤5：初始化大模型客户端并构建消息
        Generation generation = new Generation();
        Message systemMsg = Message.builder()
                .role(Role.ASSISTANT.getValue())
                .content(AIPromptConstant.SYSTEM_ROLE_CONTENT)
                .build();
        Message userMsg = Message.builder()
                .role(Role.USER.getValue())
                .content(description + AIPromptConstant.QUESTION_ASK)
                .build();
        
        // 步骤6：配置大模型调用参数（指定模型、消息列表、输出格式）
        GenerationParam param = GenerationParam.builder()
                .apiKey(apiKey)
                .model(AIModelEnum.QUESTION_AI_MODEL.getModel())
                .messages(Arrays.asList(systemMsg, userMsg))
                .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                .build();

        try {
            // 步骤7：调用大模型生成面试题
            GenerationResult result = generation.call(param);
            String json = result.getOutput().getChoices().get(0).getMessage().getContent();
            log.info("消息内容: {}", json);
            
            // 步骤8：解析JSON字符串为面试题列表
            List<InterviewQuestion> questions = JSON.parseArray(json, InterviewQuestion.class);
            
            // 步骤9：校验题目数量必须为15道
            if (questions.size() != 15) {
                throw new RuntimeException("题目数量不正确");
            }
            
            // 步骤10：校验每道题的难度等级必须在0-2范围内
            for (InterviewQuestion each : questions) {
                if (each.getLevel() < 0 || each.getLevel() > 2) {
                    throw new RuntimeException("非法 level 值");
                }
            }
            
            // 步骤11：返回生成的面试题列表
            return InterviewQuestionAskRespDTO.builder()
                    .questions(questions)
                    .build();
        } catch (Exception ex) {
            // 步骤12：异常处理与日志记录
            if (ex instanceof NoApiKeyException) {
                log.error("缺少 apiKey: {}", ex.getMessage());
            } else if (ex instanceof ApiException) {
                log.error("调用 AI 接口失败: {}", ex.getMessage());
            } else if (ex instanceof InputRequiredException) {
                log.error("请求参数缺失: {}", ex.getMessage());
            } else {
                log.error("生成面试题异常: {}", ex.getMessage(), ex);
            }
            throw new ClientException("系统异常，请稍后重试");
        }
    }

    /**
     * 单题回答评估入口
     * 对单个问题的回答进行评分和评语生成
     *
     * @param requestParam 包含问题和回答的请求对象
     * @return 评估结果（包含分数和评语）
     */
    @Override
    public AnswerEvaluationRespDTO singleQuestionAnswerEvaluation(QuestionWithAnswer requestParam) {
        return evaluateSingleQuestionWithStructuredFlow(requestParam);
    }

    /**
     * 批量异步回答评估入口
     * 并行评估多个问题的回答，提升整体处理效率
     *
     * @param requestParams 包含多个问题与回答的列表
     * @return 评估结果列表（按题目编号排序）
     */
    @Override
    public List<AnswerEvaluationRespDTO> answersEvaluationByAsync(List<QuestionWithAnswer> requestParams) {
        return evaluateAnswersWithInvokeAll(requestParams);
    }

    /**
     * 生成面试报告并记录名称
     * 基于所有题目的评估结果，生成综合面试报告和记录名称，并持久化到数据库
     *
     * @param requestParam 包含评估结果列表和简历ID的请求对象
     * @return 包含报告、记录名称和生成时间的响应对象
     */
    @Override
    public ReportGenerationRespDTO generateInterviewReportAndRecordName(ReportGenerationReqDTO requestParam) {
        return generateInterviewReportWithFutureTasks(requestParam);
    }

    /**
     * 新评分链路的核心入口。
     * 设计目标：
     * 1. 评语与分数分离建模，分别使用不同模型；
     * 2. 任一子阶段异常时只影响当前题目，不影响整批结果；
     * 3. 所有异常都带上题号、阶段、模型与原始输出片段，便于定位。
     */
    private AnswerEvaluationRespDTO evaluateSingleQuestionWithStructuredFlow(QuestionWithAnswer requestParam) {
        validateQuestionWithAnswer(requestParam);

        InterviewQuestion question = requestParam.getQuestion();
        String answer = requestParam.getAnswer().trim();
        int questionNum = question.getNum();
        long startTime = System.currentTimeMillis();

        log.info("开始单题评估 questionNum={} level={} answerLength={}",
                questionNum, question.getLevel(), answer.length());

        if (shouldUseRuleBasedFallback(answer)) {
            log.warn("单题评估触发短回答兜底 questionNum={} answerLength={}", questionNum, answer.length());
            return buildShortAnswerFallback(requestParam);
        }

        try {
            //构建评估输入
            String evaluationInput = buildEvaluationInput(question, answer);
            //评语模型生成情景化评语
            String comment = requestEvaluationComment(questionNum, evaluationInput);
            //评分模型严格按照5维度评分
            EvaluationScorePayload scorePayload = requestEvaluationScore(questionNum, evaluationInput, answer);
            //构建评估整体回答
            ApiEvaluationResp apiEvaluationResp = ApiEvaluationResp.builder()
                    .comment(comment)
                    .completeness(scorePayload.getCompleteness())
                    .levelOfDetail(scorePayload.getLevelOfDetail())
                    .accuracy(scorePayload.getAccuracy())
                    .logic(scorePayload.getLogic())
                    .expressionAbility(scorePayload.getExpressionAbility())
                    .build();
            normalizeEvaluationResp(apiEvaluationResp);

            log.info("单题评估完成 questionNum={} accuracy={} completeness={} detail={} logic={} expression={} costMs={} \n",
                    questionNum,
                    apiEvaluationResp.getAccuracy(),
                    apiEvaluationResp.getCompleteness(),
                    apiEvaluationResp.getLevelOfDetail(),
                    apiEvaluationResp.getLogic(),
                    apiEvaluationResp.getExpressionAbility(),
                    System.currentTimeMillis() - startTime);
            return new AnswerEvaluationRespDTO(requestParam, apiEvaluationResp);
        } catch (Exception ex) {
            log.error("单题评估主流程异常 questionNum={} costMs={}",
                    questionNum, System.currentTimeMillis() - startTime, ex);
            return buildModelFailureFallback(requestParam, "模型评估异常，已按保守策略完成评分。");
        }
    }

    /**
     * 批量评估
     * 使用 invokeAll 提交所有评估任务，确保提交与收集语义稳定
     * 修改点：
     * 1. 提交与收集语义更稳定；
     * 2. 不再出现 CompletableFuture 嵌套异常包装；
     * 3. 单题失败时直接落到兜底，不会导致整批 join 失败。
     *
     * @param requestParams 问题与回答列表
     * @return 评估结果列表（按题目编号排序）
     */
    private List<AnswerEvaluationRespDTO> evaluateAnswersWithInvokeAll(List<QuestionWithAnswer> requestParams) {
        // 步骤1：校验请求参数
        if (requestParams == null) {
            throw new ClientException("评估参数不足，请检查");
        }
        if (CollectionUtil.isEmpty(requestParams)) {
            return Collections.emptyList();
        }

        long startTime = System.currentTimeMillis();
        log.info("开始批量评估 total={} parallelism={} queueCapacity={}",
                requestParams.size(), AI_TASK_PARALLELISM, AI_TASK_QUEUE_CAPACITY);

        // 步骤2：将每个问题的评估任务封装为 Callable 对象
        List<Callable<AnswerEvaluationRespDTO>> tasks = requestParams.stream()
                .map(each -> (Callable<AnswerEvaluationRespDTO>) () -> evaluateQuestionSafely(each))
                .toList();

        // 步骤3：使用 invokeAll 批量提交任务并等待所有任务完成
        List<Future<AnswerEvaluationRespDTO>> futures;
        try {
            futures = aiTaskExecutor.invokeAll(tasks);
        } catch (InterruptedException ex) {
            // 步骤4：处理任务提交被中断的异常情况
            Thread.currentThread().interrupt();
            log.error("批量评估任务提交被中断 total={}", requestParams.size(), ex);
            return buildInterruptedBatchFallback(requestParams);
        }

        // 步骤5：逐个获取任务执行结果
        List<AnswerEvaluationRespDTO> resultList = new ArrayList<>(requestParams.size());
        for (int i = 0; i < futures.size(); i++) {
            QuestionWithAnswer requestParam = requestParams.get(i);
            try {
                resultList.add(futures.get(i).get());
            } catch (InterruptedException ex) {
                // 步骤6：处理结果获取被中断的情况，使用兜底评分
                Thread.currentThread().interrupt();
                log.error("批量评估结果获取被中断 questionNum={}", requestParam.getQuestion().getNum(), ex);
                resultList.add(buildModelFailureFallback(requestParam, "评估任务被中断，已按保守策略完成评分。"));
            } catch (ExecutionException ex) {
                // 步骤7：处理任务执行异常的情况，使用兜底评分
                log.error("批量评估结果获取异常 questionNum={}", requestParam.getQuestion().getNum(), ex);
                resultList.add(buildModelFailureFallback(requestParam, "评估任务执行异常，已按保守策略完成评分。"));
            }
        }

        // 步骤8：按题目编号排序结果
        resultList.sort(Comparator.comparingInt(each -> each.getQuestion().getNum()));
        
        // 步骤9：统计兜底评分数量并记录日志
        long fallbackCount = resultList.stream().filter(this::isFallbackEvaluation).count();
        log.info("批量评估完成 整体数量：{} 降级数量：{} 花费时间（毫秒）：{}",
                resultList.size(), fallbackCount, System.currentTimeMillis() - startTime);
        return resultList;
    }

    /**
     * 安全地评估单个问题
     * 捕获所有未预期异常，确保单题失败不影响整体流程
     *
     * @param requestParam 问题与回答
     * @return 评估结果（失败时返回兜底评分）
     */
    private AnswerEvaluationRespDTO evaluateQuestionSafely(QuestionWithAnswer requestParam) {
        try {
            return evaluateSingleQuestionWithStructuredFlow(requestParam);
        } catch (Exception ex) {
            int questionNum = requestParam != null && requestParam.getQuestion() != null
                    ? requestParam.getQuestion().getNum() : -1;
            log.error("单题评估任务未捕获异常 questionNum={}", questionNum, ex);
            return buildModelFailureFallback(requestParam, "评估流程异常终止，已按保守策略完成评分。");
        }
    }

    /**
     * 检查参数是否合规
     * 不合规直接报错
     * @param requestParam 请求参数
     */
    private void validateQuestionWithAnswer(QuestionWithAnswer requestParam) {
        if (ObjectUtil.isNull(requestParam) || ObjectUtil.isNull(requestParam.getQuestion())) {
            throw new ClientException("问题不能为空，请检查");
        }
        InterviewQuestion question = requestParam.getQuestion();
        if (question.getLevel() < 0 || question.getLevel() > 2 || StrUtil.isBlank(question.getQuestionDescription())) {
            throw new ClientException("问题描述或等级不能为空，请检查");
        }
        if (StrUtil.isBlank(requestParam.getAnswer())) {
            throw new ClientException("回答不能为空，请检查");
        }
    }

    /**
     * 判断当前用户回答是否过短、且意图为"不会"等类似词句
     * 用于快速识别无效回答，触发规则兜底策略，避免调用大模型造成资源浪费
     *
     * @param answer 用户回答文本
     * @return true-需要触发规则兜底（回答过短或明确表示不会）；false-可以正常调用AI评估
     */
    private boolean shouldUseRuleBasedFallback(String answer) {
        // 步骤1：去除所有空白字符（空格、换行、制表符等），得到纯文本内容
        String normalized = answer.replaceAll("\\s+", "");
            
        // 步骤2：检查回答长度是否低于最小阈值（MIN_ANSWER_LENGTH_FOR_AI=12）
        // 如果回答过短，直接判定为无效回答，触发兜底策略
        if (normalized.length() < MIN_ANSWER_LENGTH_FOR_AI) {
            return true;
        }
            
        // 步骤3：检查回答是否完全匹配预定义的"无效回答关键词列表"
        // 注意：这里是完全匹配，不是部分包含
        // 例如："不知道" → true；"我不知道" → false（因为整个字符串不在列表中）
        return Arrays.asList("不知道", "不会", "不清楚", "无", "略", "跳过", "未作答", "没有")
                .contains(normalized);
    }

    /**
     * 将题目信息统一收敛为 JSON 文本，确保两个模型使用完全一致的输入语义
     *
     * @param question 面试问题对象
     * @param answer 候选人回答文本
     * @return JSON格式的评估输入字符串
     */
    private String buildEvaluationInput(InterviewQuestion question, String answer) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("题目难度", question.getLevel());
        jsonObject.put("题目描述", question.getQuestionDescription());
        jsonObject.put("面试对象回答", answer);
        return jsonObject.toJSONString();
    }

    /**
     * 构建评估评价
     * 调用评语模型生成情景化评语，支持重试机制
     *
     * @param questionNum  问题编号
     * @param evaluationInput  评估输入（JSON格式）
     * @return 大模型生成的评语，失败时返回兜底评语
     */
    private String requestEvaluationComment(int questionNum, String evaluationInput) {
        String model = AIModelEnum.EVALUATION_COMMENT_AI_MODEL.getModel();
        
        // 步骤1：最多尝试 MODEL_MAX_ATTEMPTS 次调用评语模型
        for (int attempt = 1; attempt <= MODEL_MAX_ATTEMPTS; attempt++) {
            try {
                // 步骤2：调用模型生成评语
                String rawContent = callModelForMessage(model,
                        evaluationInput + AIPromptConstant.ANSWER_COMMENT_GIVE,
                        questionNum, "evaluation-comment", attempt);
                
                // 步骤3：解析模型输出的JSON，提取评语文本
                String comment = parseCommentPayload(rawContent);
                log.info("评语生成成功 questionNum={} attempt={} comment={}",
                        questionNum, attempt, truncateForLog(comment));
                return comment;
            } catch (Exception ex) {
                // 步骤4：记录失败日志，判断是否为不可重试异常
                log.warn("评语生成失败 questionNum={} model={} attempt={}",
                        questionNum, model, attempt, ex);
                if (isNonRetryableModelException(ex)) {
                    break;
                }
            }
        }
        
        // 步骤5：所有尝试均失败，返回兜底评语
        return FALLBACK_COMMENT_PREFIX + "评语模型输出异常，建议结合原回答人工复核。";
    }

    /**
     * 带有简单重试机制的分数计算
     * 调用评分模型严格按照5维度评分，失败时使用启发式兜底策略
     *
     * @param questionNum 问题编号
     * @param evaluationInput  评估输入（JSON格式）
     * @param answer 问题回答文本
     * @return 包含5个维度分数的结构化对象
     */
    private EvaluationScorePayload requestEvaluationScore(int questionNum, String evaluationInput, String answer) {
        String model = AIModelEnum.EVALUATION_SCORE_AI_MODEL.getModel();
        
        // 步骤1：最多尝试 MODEL_MAX_ATTEMPTS 次调用评分模型
        for (int attempt = 1; attempt <= MODEL_MAX_ATTEMPTS; attempt++) {
            try {
                // 步骤2：调用模型生成分数
                String rawContent = callModelForMessage(model,
                        evaluationInput + AIPromptConstant.ANSWER_SCORE_GIVE,
                        questionNum, "evaluation-score", attempt);
                
                // 步骤3：解析模型输出的JSON，提取5个维度分数
                EvaluationScorePayload payload = parseScorePayload(rawContent);
                log.info("打分生成成功 questionNum={} attempt={} accuracy={} completeness={} detail={} logic={} expression={}",
                        questionNum, attempt, payload.getAccuracy(), payload.getCompleteness(),
                        payload.getLevelOfDetail(), payload.getLogic(), payload.getExpressionAbility());
                return payload;
            } catch (Exception ex) {
                // 步骤4：记录失败日志，判断是否为不可重试异常
                log.warn("打分生成失败 questionNum={} model={} attempt={}",
                        questionNum, model, attempt, ex);
                if (isNonRetryableModelException(ex)) {
                    break;
                }
            }
        }

        // 步骤5：所有尝试均失败，启用启发式兜底策略（基于回答长度和关键词命中）
        EvaluationScorePayload heuristicScore = buildHeuristicScorePayload(answer);
        log.warn("打分模型多次失败，启用启发式兜底 questionNum={} accuracy={} completeness={} detail={} logic={} expression={}",
                questionNum,
                heuristicScore.getAccuracy(),
                heuristicScore.getCompleteness(),
                heuristicScore.getLevelOfDetail(),
                heuristicScore.getLogic(),
                heuristicScore.getExpressionAbility());
        return heuristicScore;
    }

    /**
     * 模型调用通用方法
     * 封装大模型调用的标准流程，包括消息构建、参数配置、调用执行和日志记录
     *
     * @param model 模型类型（如评语模型、评分模型等）
     * @param userContent 用户消息内容
     * @param questionNum 问题编号（用于日志追踪，-1表示非单题评估场景）
     * @param stage  当前阶段标识（如 "evaluation-comment"、"evaluation-score"）
     * @param attempt 尝试次数（用于重试日志记录）
     * @return 模型返回的原始文本内容
     * @throws NoApiKeyException API密钥缺失异常
     * @throws ApiException API调用失败异常
     * @throws InputRequiredException 输入参数缺失异常
     */
    private String callModelForMessage(String model, String userContent, int questionNum, String stage, int attempt)
            throws NoApiKeyException, ApiException, InputRequiredException {
        long startTime = System.currentTimeMillis();
        
        // 步骤1：初始化大模型客户端
        Generation generation = new Generation();
        
        // 步骤2：构建系统消息（设定角色和行为准则）
        Message systemMsg = Message.builder()
                .role(Role.SYSTEM.getValue())
                .content(AIPromptConstant.SYSTEM_ROLE_CONTENT)
                .build();
        
        // 步骤3：构建用户消息（包含具体的评估输入）
        Message userMsg = Message.builder()
                .role(Role.USER.getValue())
                .content(userContent)
                .build();
        
        // 步骤4：配置调用参数（API密钥、模型类型、消息列表、输出格式）
        GenerationParam param = GenerationParam.builder()
                .apiKey(apiKey)
                .model(model)
                .messages(Arrays.asList(systemMsg, userMsg))
                .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                .build();

        log.info("模型调用开始 questionNum={} stage={} model={} attempt={}",
                questionNum, stage, model, attempt);
        
        // 步骤5：执行模型调用
        GenerationResult result = generation.call(param);
        
        // 步骤6：提取模型返回的文本内容
        String content = result.getOutput().getChoices().get(0).getMessage().getContent();
        log.info("模型调用完成 questionNum={} stage={} model={} attempt={} costMs={} raw={}",
                questionNum, stage, model, attempt, System.currentTimeMillis() - startTime, truncateForLog(content));
        return content;
    }


    /**
     * JSON字符串解析comment
     * 从模型输出的原始文本中提取并解析评语文本
     *
     * @param rawContent comment对应的json字符串（可能包含Markdown标记）
     * @return  转换为字符串的评价文本
     * @throws IllegalArgumentException 当comment字段缺失时抛出
     */
    private String parseCommentPayload(String rawContent) {
        // 步骤1：从原始文本中提取第一个完整的JSON对象
        JSONObject jsonObject = JSONObject.parseObject(extractFirstJsonObject(rawContent));
        
        // 步骤2：提取comment字段
        String comment = jsonObject.getString("comment");
        if (StrUtil.isBlank(comment)) {
            throw new IllegalArgumentException("comment 字段缺失");
        }
        
        // 步骤3：去除首尾空白字符后返回
        return comment.trim();
    }

    /**
     * JSON字符串解析为五大分数
     * 从模型输出的原始文本中提取并解析5个维度的分数
     *
     * @param rawContent  分数对应的json字符串（可能包含Markdown标记）
     * @return  包含5个维度分数的结构化对象
     * @throws IllegalArgumentException 当任一必填字段缺失时抛出
     */
    private EvaluationScorePayload parseScorePayload(String rawContent) {
        // 步骤1：从原始文本中提取第一个完整的JSON对象
        JSONObject jsonObject = JSONObject.parseObject(extractFirstJsonObject(rawContent));
        
        // 步骤2：依次读取5个必填分数字段，并进行归一化处理
        return new EvaluationScorePayload(
                readRequiredScore(jsonObject, "completeness"),
                readRequiredScore(jsonObject, "levelOfDetail"),
                readRequiredScore(jsonObject, "accuracy"),
                readRequiredScore(jsonObject, "logic"),
                readRequiredScore(jsonObject, "expressionAbility")
        );
    }

    /**
     * 校验JSONObject是否存在对应字段
     * 读取必填分数字段并进行归一化处理（限制在0-10范围内）
     *
     * @param jsonObject json对象
     * @param fieldName 对应字段名
     * @return 归一化后的分数值（0-10）
     * @throws IllegalArgumentException 当字段缺失时抛出
     */
    private int readRequiredScore(JSONObject jsonObject, String fieldName) {
        Integer score = jsonObject.getInteger(fieldName);
        if (score == null) {
            throw new IllegalArgumentException("缺少字段: " + fieldName);
        }
        return normalizeSingleScore(score);
    }

    /**
     * 从大模型原始输出中提取第一个完整的JSON对象
     * 使用嵌套深度追踪算法，精确识别JSON对象的起始和结束位置
     * 支持处理包含Markdown标记、转义字符和嵌套结构的复杂场景
     *
     * @param rawContent 原始内容（可能包含Markdown标记或其他非JSON文本）
     * @return 提取后的完整JSON字符串
     * @throws IllegalArgumentException 当未找到JSON起始符或JSON结构不完整时抛出
     */
    private String extractFirstJsonObject(String rawContent) {
        // 步骤1：去除Markdown标记和首尾空白字符
        String normalized = normalizeModelContent(rawContent);
            
        // 步骤2：定位JSON对象的起始符 '{'
        int start = normalized.indexOf('{');
        if (start < 0) {
            throw new IllegalArgumentException("未找到 JSON 对象起始符");
        }
            
        // 步骤3：初始化状态变量
        int depth = 0;              // 嵌套深度计数器
        boolean inQuotes = false;   // 标记当前是否在字符串内部
        boolean escaped = false;    // 标记前一个字符是否为转义字符
            
        // 步骤4：从JSON的可能开始位置遍历到字符串末尾
        for (int i = start; i < normalized.length(); i++) {
            char current = normalized.charAt(i);
                
            // 步骤5：如果前一个字符是转义字符，跳过当前字符判断
            // 避免将 \" 误判为字符串结束
            if (escaped) {
                escaped = false;
                continue;
            }
                
            // 步骤6：检测转义字符 '\'
            if (current == '\\') {
                escaped = true;
                continue;
            }
                
            // 步骤7：检测双引号，切换字符串内外状态
            // 用于区分JSON结构括号和字符串内部的普通字符
            if (current == '"') {
                inQuotes = !inQuotes;
                continue;
            }
                
            // 步骤8：如果在字符串内部，跳过字符判断
            // 避免字符串内的普通字符影响JSON结构解析
            if (inQuotes) {
                continue;
            }
                
            // 步骤9：检测左括号，嵌套深度+1
            if (current == '{') {
                depth++;
            // 步骤10：检测右括号，嵌套深度-1
            } else if (current == '}') {
                depth--;
                // 步骤11：当嵌套深度归零时，说明找到了完整的JSON对象
                if (depth == 0) {
                    return normalized.substring(start, i + 1);
                }
            }
        }
            
        // 步骤12：遍历完成但未找到匹配的结束符，JSON结构不完整
        throw new IllegalArgumentException("JSON 对象不完整");
    }

    /**
     * 模型输出归一化
     * 功能：预防模型使用Markdown格式输出导致JSON解析失败
     * 移除常见的Markdown代码块标记（```json、```JSON、```）
     *
     * @param rawContent 原始输出内容
     * @return 归一化后的纯文本内容
     */
    private String normalizeModelContent(String rawContent) {
        String normalized = StrUtil.blankToDefault(rawContent, "").trim();
        normalized = normalized.replace("```json", "");
        normalized = normalized.replace("```JSON", "");
        normalized = normalized.replace("```", "");
        return normalized.trim();
    }

    /**
     * 判断当前异常是否为不可重试异常
     * 对于API密钥缺失或输入参数缺失等配置错误，重试无法解决问题，应立即终止重试
     *
     * @param ex 异常对象
     * @return true-不可重试异常；false-可重试异常
     */
    private boolean isNonRetryableModelException(Exception ex) {
        // 没有API调用密钥或缺失输入异常，无法通过重试解决
        return ex instanceof NoApiKeyException || ex instanceof InputRequiredException;
    }

    /**
     * 当分数模型持续异常时，使用回答长度与技术关键词命中率生成保守分数
     * 该兜底只用于“模型不可用”场景，因此 accuracy 被严格限制在较低区间，避免误判为高分
     *
     * @param answer 候选人回答文本
     * @return 基于启发式规则计算的5维度分数
     */
    private EvaluationScorePayload buildHeuristicScorePayload(String answer) {
        // 步骤1：字符串归一化（去除所有空白字符）
        String normalized = answer.replaceAll("\\s+", "");
        int length = normalized.length();
        
        // 步骤2：如果回答长度低于最小阈值，所有维度分数均为0
        if (length < MIN_ANSWER_LENGTH_FOR_AI) {
            return new EvaluationScorePayload(0, 0, 0, 0, 0);
        }
        
        // 步骤3：统计技术关键词命中次数（用于评估专业度）
        int keywordHits = countTechnicalKeywordHits(normalized.toLowerCase());
        
        // 步骤4：检测回答是否具有结构化特征（如“首先、其次、最后”等逻辑词）
        boolean structured = containsAny(normalized, "首先", "其次", "最后", "因为", "所以", "方案", "实现", "步骤", "1.", "2.", "3.");

        // 步骤5：基于回答长度、关键词命中数和结构化程度计算各维度分数
        // completeness（完整度）：基础分 + 关键词加分，上限8分
        int completeness = Math.min(8, scoreByLength(length, 2, 4, 5, 6, 7) + Math.min(1, keywordHits / 3));
        
        // levelOfDetail（细节度）：基础分 + 结构化加分，上限8分
        int levelOfDetail = Math.min(8, scoreByLength(length, 1, 3, 4, 5, 6) + (structured ? 1 : 0));
        
        // accuracy（准确度）：严格限制在低分区间，上限4分，避免误判
        int accuracy = Math.min(4, scoreByLength(length, 0, 1, 2, 3, 3) + Math.min(1, keywordHits / 4));
        
        // logic（逻辑度）：基础分 + 结构化加分（结构化回答逻辑性更强），上限7分
        int logic = Math.min(7, scoreByLength(length, 1, 2, 3, 4, 5) + (structured ? 2 : 0));
        
        // expressionAbility（表达能力）：基础分 + 结构化加分，上限7分
        int expressionAbility = Math.min(7, scoreByLength(length, 1, 2, 3, 4, 5) + (structured ? 1 : 0));
        
        return new EvaluationScorePayload(completeness, levelOfDetail, accuracy, logic, expressionAbility);
    }

    /**
     * 简单计算术语命中次数
     * 用于兜底评分时评估候选人的技术专业度
     *
     * @param answer 回答文本（已转换为小写）
     * @return  术语命中次数
     */
    private int countTechnicalKeywordHits(String answer) {
        // 定义常见技术关键词列表
        List<String> keywords = Arrays.asList(
                "redis", "mysql", "rocketmq", "rabbitmq", "spring", "java",
                "分布式", "缓存", "数据库", "事务", "锁", "一致性", "幂等", "消息队列"
        );
        
        // 遍历关键词列表，统计命中次数
        int hits = 0;
        for (String keyword : keywords) {
            if (answer.contains(keyword)) {
                hits++;
            }
        }
        return hits;
    }

    /**
     * 判断文本是否包含任意一个关键字片段
     * 用于兜底评分时检测回答的结构化特征
     *
     * @param text 待检测文本
     * @param fragments 关键字片段列表
     * @return true-包含至少一个关键字；false-不包含任何关键字
     */
    private boolean containsAny(String text, String... fragments) {
        for (String fragment : fragments) {
            if (text.contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 根据回答长度，选择对应分数，用于简单兜底评分
     * 采用分段评分策略，回答越长基础分数越高
     *
     * @param length 回答文本长度（去除空白字符后）
     * @param shortScore 短回答分数（长度 < 20）
     * @param mediumScore 中等回答分数（20 <= 长度 < 60）
     * @param longScore 长回答分数（60 <= 长度 < 120）
     * @param longerScore 更长回答分数（120 <= 长度 < 240）
     * @param richScore 丰富回答分数（长度 >= 240）
     * @return 对应长度区间的分数
     */
    private int scoreByLength(int length, int shortScore, int mediumScore, int longScore, int longerScore, int richScore) {
        if (length < 20) {
            return shortScore;
        }
        if (length < 60) {
            return mediumScore;
        }
        if (length < 120) {
            return longScore;
        }
        if (length < 240) {
            return longerScore;
        }
        return richScore;
    }

    /**
     * 构建降级兜底评价
     * 功能：识别到用户回答过短/意图为“不会”等，触发兜底评价，降低LLM压力
     *
     * @param requestParam 面试问题&回答输入类
     * @return  面试评价（所有维度分数为0）
     */
    private AnswerEvaluationRespDTO buildShortAnswerFallback(QuestionWithAnswer requestParam) {
        return buildFallbackEvaluation(requestParam,
                FALLBACK_COMMENT_PREFIX + "回答内容过短或未有效作答，系统按低分处理。",
                new EvaluationScorePayload(0, 0, 0, 0, 0));
    }

    /**
     * 模型调用失败降级回答构建
     * 当模型多次调用失败时，使用启发式评分作为兜底策略
     *
     * @param requestParam  请求参数
     * @param reason 失败原因描述
     * @return 评估返回类（包含兜底评语和启发式分数）
     */
    private AnswerEvaluationRespDTO buildModelFailureFallback(QuestionWithAnswer requestParam, String reason) {
        if (requestParam == null) {
            throw new ClientException("评估参数不足，请检查");
        }
        // 使用启发式规则计算保守分数
        EvaluationScorePayload heuristicScore = buildHeuristicScorePayload(StrUtil.blankToDefault(requestParam.getAnswer(), ""));
        return buildFallbackEvaluation(requestParam, FALLBACK_COMMENT_PREFIX + reason, heuristicScore);
    }

    /**
     * 构建降级评估返回对象
     * 统一封装兜底评价的构建逻辑，包括评语、分数和归一化处理
     *
     * @param requestParam  问题&面试对象回答
     * @param comment 大模型评价（或兜底评语）
     * @param scorePayload 所有维度分数
     * @return 评估返回类对象
     */
    private AnswerEvaluationRespDTO buildFallbackEvaluation(QuestionWithAnswer requestParam, String comment,
                                                            EvaluationScorePayload scorePayload) {
        // 步骤1：构建API评估响应对象
        ApiEvaluationResp apiEvaluationResp = ApiEvaluationResp.builder()
                .comment(comment)
                .completeness(scorePayload.getCompleteness())
                .levelOfDetail(scorePayload.getLevelOfDetail())
                .accuracy(scorePayload.getAccuracy())
                .logic(scorePayload.getLogic())
                .expressionAbility(scorePayload.getExpressionAbility())
                .build();
        
        // 步骤2：对分数进行归一化处理（限制在0-10范围内）
        normalizeEvaluationResp(apiEvaluationResp);
        
        // 步骤3：返回评估结果对象
        return new AnswerEvaluationRespDTO(requestParam, apiEvaluationResp);
    }

    /**
     * 批量评估中断时的兜底策略
     * 当批量评估任务被中断时，为所有题目生成保守评分
     *
     * @param requestParams 问题&回答列表
     * @return 兜底评估结果列表（按题目编号排序）
     */
    private List<AnswerEvaluationRespDTO> buildInterruptedBatchFallback(List<QuestionWithAnswer> requestParams) {
        return requestParams.stream()
                .map(each -> buildModelFailureFallback(each, "批量评估被中断，已按保守策略完成评分。"))
                .sorted(Comparator.comparingInt(each -> each.getQuestion().getNum()))
                .collect(Collectors.toList());
    }

    /**
     * 判断是否是兜底评估
     * 通过检查评语是否以兜底前缀开头来识别
     *
     * @param dto 评估结果对象
     * @return true-是兜底评估；false-是正常模型评估
     */
    private boolean isFallbackEvaluation(AnswerEvaluationRespDTO dto) {
        return dto != null
                && dto.getApiResp() != null
                && StrUtil.startWith(dto.getApiResp().getComment(), FALLBACK_COMMENT_PREFIX);
    }

    /**
     * 截断文本用于日志记录
     * 避免日志中输出过长的文本内容，限制最大长度为300字符
     *
     * @param content 原始文本内容
     * @return 截断后的文本（超过300字符时添加"..."）
     */
    private String truncateForLog(String content) {
        String normalized = StrUtil.blankToDefault(content, "").replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 300) {
            return normalized;
        }
        return normalized.substring(0, 300) + "...";
    }

    /**
     * 通过面试人简历构建对应描述文本
     * 将简历中的各个字段拼接成自然语言描述，作为大模型的输入上下文
     *
     * @param form 候选人简历信息
     * @return 格式化的简历描述文本
     */
    private String buildFormDescription(IntervieweeForm form) {
        StringBuilder builder = new StringBuilder();
        
        // 步骤1：添加基本信息（姓名、求职意向）
        builder.append("候选人姓名：").append(form.getCandidateName())
                .append("；求职意向：").append(form.getJobIntention());
        
        // 步骤2：添加专业技能列表
        if (CollectionUtil.isNotEmpty(form.getProfessionalSkills())) {
            builder.append("；专业技能：")
                    .append(String.join("、", form.getProfessionalSkills()));
        }
        
        // 步骤3：添加教育经历（学校、专业、学历）
        if (CollectionUtil.isNotEmpty(form.getEducationExperiences())) {
            builder.append("；教育经历：")
                    .append(form.getEducationExperiences().stream()
                            .map(each -> each.getSchoolName() + " " + each.getMajor() + " " + each.getDegree())
                            .collect(Collectors.joining("；")));
        }
        
        // 步骤4：添加工作经历
        if (CollectionUtil.isNotEmpty(form.getWorkExperiences())) {
            builder.append("；工作经历：")
                    .append(buildWorkDescription(form.getWorkExperiences()));
        }
        
        // 步骤5：添加项目经历
        if (CollectionUtil.isNotEmpty(form.getProjectExperiences())) {
            builder.append("；项目经历：")
                    .append(form.getProjectExperiences().stream()
                            .map(this::buildProjectDescription)
                            .collect(Collectors.joining("；")));
        }
        
        return builder.toString();
    }

    /**
     * 工作经历描述构建
     * 将工作经历列表转换为自然语言描述
     *
     * @param workExperiences 工作经历列表
     * @return 格式化的工作经历描述文本
     */
    private String buildWorkDescription(List<WorkExperience> workExperiences) {
        return workExperiences.stream()
                .map(each -> each.getCompanyName() + " " + each.getPosition() + " " + each.getWorkContent())
                .collect(Collectors.joining("；"));
    }

    /**
     * 项目经历构建
     * 将单个项目经历转换为结构化描述文本
     *
     * @param projectExperience 项目经历对象
     * @return 格式化的项目描述文本（包含角色、描述、职责、成果）
     */
    private String buildProjectDescription(ProjectExperience projectExperience) {
        return projectExperience.getProjectName()
                + "（角色：" + projectExperience.getProjectRole()
                + "，项目描述：" + projectExperience.getProjectDescription()
                + "，职责：" + projectExperience.getResponsibility()
                + "，成果：" + projectExperience.getAchievement() + "）";
    }

    /**
     * 整体分数评估（包含总分和各维度分数）
     * 基于所有题目的评估结果，计算加权平均分和综合面试分数
     * 不同难度的题目赋予不同权重：简单(1.0)、中等(1.2)、困难(1.5)
     *
     * @param list 所有题目的评估结果列表
     * @return 包含总分和各维度分数的DTO对象
     */
    private InterviewDimensionScoreDTO calculateDimensionScore(List<AnswerEvaluationRespDTO> list) {
        // 步骤1：初始化累加器
        double totalAccuracyScore = 0;
        double totalCompletenessScore = 0;
        double totalLevelOfDetailScore = 0;
        double totalLogicScore = 0;
        double totalExpressionAbilityScore = 0;
        double totalWeight = 0;

        // 步骤2：遍历所有题目，按难度加权累加各维度分数
        for (AnswerEvaluationRespDTO dto : list) {
            ApiEvaluationResp apiResp = dto.getApiResp();
            InterviewQuestion question = dto.getQuestion();

            // 根据题目难度确定权重
            double weight = switch (question.getLevel()) {
                case 0 -> 1.0;   // 简单题
                case 1 -> 1.2;   // 中等题
                case 2 -> 1.5;   // 困难题
                default -> 1.0;
            };

            // 累加加权分数
            totalAccuracyScore += apiResp.getAccuracy() * weight;
            totalCompletenessScore += apiResp.getCompleteness() * weight;
            totalLevelOfDetailScore += apiResp.getLevelOfDetail() * weight;
            totalLogicScore += apiResp.getLogic() * weight;
            totalExpressionAbilityScore += apiResp.getExpressionAbility() * weight;
            totalWeight += weight;
        }

        // 步骤3：计算各维度的加权平均分，并归一化到0-10范围
        int accuracyScore = normalizeTotalScore(totalAccuracyScore / totalWeight);
        int completenessScore = normalizeTotalScore(totalCompletenessScore / totalWeight);
        int levelOfDetailScore = normalizeTotalScore(totalLevelOfDetailScore / totalWeight);
        int logicScore = normalizeTotalScore(totalLogicScore / totalWeight);
        int expressionAbilityScore = normalizeTotalScore(totalExpressionAbilityScore / totalWeight);

        // 步骤4：计算综合面试分数（满分100分）
        // 权重分配：准确度35%、完整度20%、细节度15%、逻辑度15%、表达能力15%
        int interviewPoint = (int) Math.round((
                accuracyScore * 0.35 +
                        completenessScore * 0.20 +
                        levelOfDetailScore * 0.15 +
                        logicScore * 0.15 +
                        expressionAbilityScore * 0.15
        ) * 10);

        // 步骤5：返回包含总分和各维度分数的DTO对象
        return InterviewDimensionScoreDTO.builder()
                .interviewPoint(interviewPoint)
                .accuracyScore(accuracyScore)
                .completenessScore(completenessScore)
                .levelOfDetailScore(levelOfDetailScore)
                .logicScore(logicScore)
                .expressionAbilityScore(expressionAbilityScore)
                .build();
    }



    /**
     * 报告生成侧也改为 Future 协调：
     * 1. 仅保留必要并行任务；
     * 2. 每个任务内部自带重试和兜底；
     * 3. Future 获取失败时仍然返回可展示结果。
     */
    private ReportGenerationRespDTO generateInterviewReportWithFutureTasks(ReportGenerationReqDTO requestParam) {
        if (ObjectUtil.isEmpty(requestParam)) {
            throw new ClientException("参数不能为空");
        }
        List<AnswerEvaluationRespDTO> answerEvaluationRespList = requestParam.getAnswerEvaluationRespList();
        if (CollectionUtil.isEmpty(answerEvaluationRespList)) {
            throw new ClientException("回答评估结果不能为空");
        }
        sanitizeReportEvaluations(answerEvaluationRespList);

        long startTime = System.currentTimeMillis();
        Long userId = getCurrentUserId();
        //查询简历
        IntervieweeForm form = getIntervieweeFormById(requestParam.getFormId());
        //计算维度分数
        InterviewDimensionScoreDTO dimensionScoreDTO = calculateDimensionScore(answerEvaluationRespList);
        //报告生成输入构建
        JSONObject reportInput = buildReportInput(answerEvaluationRespList, form, dimensionScoreDTO);
        //构建异步执行任务
        Future<AgentInterviewReportDTO> reportFuture = aiTaskExecutor.submit(
                () -> generateInterviewReportSafely(reportInput, dimensionScoreDTO, form));
        Future<String> recordNameFuture = aiTaskExecutor.submit(
                () -> generateRecordNameSafely(form));
        //两个异步任务完成继续向下执行
        AgentInterviewReportDTO reportDTO = waitForReportFuture(reportFuture, form, dimensionScoreDTO);
        String recordName = waitForRecordNameFuture(recordNameFuture, form);
        //面试记录提取并json化
        String interviewProcessRecord = JSON.toJSONString(answerEvaluationRespList);
        //关键字提取，json化
        String interviewKeywords = JSON.toJSONString(form.getProfessionalSkills());
        InterviewRecordDO recordDO = InterviewRecordDO.builder()
                .recordName(recordName)
                .userId(userId)
                .interviewProcessRecord(interviewProcessRecord)
                .interviewKeywords(interviewKeywords)
                .summaryReportRecord(JSON.toJSONString(reportDTO.getSummaryReport()))
                .adviceReportRecord(JSON.toJSONString(reportDTO.getAdviceReport()))
                .build();

        recordDO.setInterviewPoint(dimensionScoreDTO.getInterviewPoint());
        recordDO.setAccuracyScore(dimensionScoreDTO.getAccuracyScore());
        recordDO.setCompletenessScore(dimensionScoreDTO.getCompletenessScore());
        recordDO.setLevelOfDetailScore(dimensionScoreDTO.getLevelOfDetailScore());
        recordDO.setLogicScore(dimensionScoreDTO.getLogicScore());
        recordDO.setExpressionAbilityScore(dimensionScoreDTO.getExpressionAbilityScore());
        interviewRecordDOMapper.insert(recordDO);

        String zSetKey = String.format(AgentRedisConstant.INTERVIEW_RECORD_ID_WITH_NAME_CACHE_KEY, userId);
        Long id = recordDO.getId();
        Date now = recordDO.getCreateTime();
        String idAndName = id + "|" + recordName + "|" + dimensionScoreDTO.getInterviewPoint() + "|" + now.getTime();
        stringRedisTemplate.opsForZSet().add(zSetKey, idAndName, now.getTime());

        log.info("报告生成完成 formId={} recordId={} recordName={} costMs={}",
                requestParam.getFormId(), id, recordName, System.currentTimeMillis() - startTime);
        return ReportGenerationRespDTO.builder()
                .date(now)
                .recordName(recordName)
                .reportDTO(reportDTO)
                .build();
    }

    private JSONObject buildReportInput(List<AnswerEvaluationRespDTO> answerEvaluationRespList, IntervieweeForm form,
                                        InterviewDimensionScoreDTO dimensionScoreDTO) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("form", form);
        jsonObject.put("evaluations", answerEvaluationRespList);
        jsonObject.put("interviewPoint", dimensionScoreDTO.getInterviewPoint());
        jsonObject.put("accuracyScore", dimensionScoreDTO.getAccuracyScore());
        jsonObject.put("completenessScore", dimensionScoreDTO.getCompletenessScore());
        jsonObject.put("levelOfDetailScore", dimensionScoreDTO.getLevelOfDetailScore());
        jsonObject.put("logicScore", dimensionScoreDTO.getLogicScore());
        jsonObject.put("expressionAbilityScore", dimensionScoreDTO.getExpressionAbilityScore());
        return jsonObject;
    }

    private void sanitizeReportEvaluations(List<AnswerEvaluationRespDTO> answerEvaluationRespList) {
        for (AnswerEvaluationRespDTO dto : answerEvaluationRespList) {
            if (dto == null || dto.getQuestion() == null) {
                throw new ClientException("回答评估结果缺少题目信息");
            }
            ApiEvaluationResp apiResp = dto.getApiResp();
            if (apiResp == null) {
                apiResp = ApiEvaluationResp.builder().build();
                dto.setApiResp(apiResp);
            }
            normalizeEvaluationResp(apiResp);
            if (StrUtil.isBlank(apiResp.getComment())) {
                int questionNum = dto.getQuestion().getNum();
                apiResp.setComment(FALLBACK_COMMENT_PREFIX + "评语缺失，报告生成阶段已自动补齐，请结合原回答人工复核。");
                log.warn("报告生成请求存在缺失评语，已使用兜底评语补齐 questionNum={}", questionNum);
            }
        }
    }

    /**
     * 等待报告生成完成
     * 执行失败带有兜底策略
     */
    private AgentInterviewReportDTO waitForReportFuture(Future<AgentInterviewReportDTO> future, IntervieweeForm form,
                                                        InterviewDimensionScoreDTO dimensionScoreDTO) {
        try {
            return future.get();
        } catch (InterruptedException ex) {
            //方法抛出 InterruptedException，中断标志被JVM自动清除
            //恢复中断标志
            Thread.currentThread().interrupt();
            log.error("等待面试报告结果被中断 formJobIntention={}", form.getJobIntention(), ex);
            return buildFallbackReport(form, dimensionScoreDTO, "报告任务被中断，系统已生成兜底报告。");
        } catch (ExecutionException ex) {
            log.error("等待面试报告结果异常 formJobIntention={}", form.getJobIntention(), ex);
            return buildFallbackReport(form, dimensionScoreDTO, "报告任务执行异常，系统已生成兜底报告。");
        }
    }

    private String waitForRecordNameFuture(Future<String> future, IntervieweeForm form) {
        try {
            return future.get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.error("等待记录名结果被中断 formJobIntention={}", form.getJobIntention(), ex);
            return buildFallbackRecordName(form);
        } catch (ExecutionException ex) {
            log.error("等待记录名结果异常 formJobIntention={}", form.getJobIntention(), ex);
            return buildFallbackRecordName(form);
        }
    }

    /**
     * 带有兜底策略&重试的报告构建
     */
    private AgentInterviewReportDTO generateInterviewReportSafely(JSONObject jsonObject,
                                                                  InterviewDimensionScoreDTO dimensionScoreDTO,
                                                                  IntervieweeForm form) {
        String model = AIModelEnum.SUMMARY_GENERATE_AI_MODEL.getModel();
        for (int attempt = 1; attempt <= MODEL_MAX_ATTEMPTS; attempt++) {
            try {
                String rawContent = callModelForMessage(
                        model,
                        AIPromptConstant.SUMMARY_ASK_V2 + jsonObject.toJSONString(),
                        -1,
                        "interview-report",
                        attempt
                );
                AgentInterviewReportDTO reportDTO = parseInterviewReport(rawContent, dimensionScoreDTO);
                log.info("面试报告生成成功 model={} attempt={} summary={} advice={}",
                        model, attempt, truncateForLog(reportDTO.getSummaryReport()), truncateForLog(reportDTO.getAdviceReport()));
                return reportDTO;
            } catch (Exception ex) {
                log.warn("面试报告生成失败 model={} attempt={} formJobIntention={}",
                        model, attempt, form.getJobIntention(), ex);
                if (isNonRetryableModelException(ex)) {
                    break;
                }
            }
        }
        return buildFallbackReport(form, dimensionScoreDTO, "模型报告生成异常，系统已生成兜底报告。");
    }

    /**
     * 将面试报告转换为返回类
     */
    private AgentInterviewReportDTO parseInterviewReport(String rawContent, InterviewDimensionScoreDTO dimensionScoreDTO) {
        JSONObject jsonObject = JSONObject.parseObject(extractFirstJsonObject(rawContent));
        String summaryReport = jsonObject.getString("summaryReport");
        String adviceReport = jsonObject.getString("adviceReport");
        if (StrUtil.isBlank(summaryReport) || StrUtil.isBlank(adviceReport)) {
            throw new IllegalArgumentException("报告字段缺失");
        }

        AgentInterviewReportDTO reportDTO = AgentInterviewReportDTO.builder()
                .summaryReport(summaryReport.trim())
                .adviceReport(adviceReport.trim())
                .build();
        reportDTO.setInterviewPoint(dimensionScoreDTO.getInterviewPoint());
        reportDTO.setAccuracyScore(dimensionScoreDTO.getAccuracyScore());
        reportDTO.setCompletenessScore(dimensionScoreDTO.getCompletenessScore());
        reportDTO.setLevelOfDetailScore(dimensionScoreDTO.getLevelOfDetailScore());
        reportDTO.setLogicScore(dimensionScoreDTO.getLogicScore());
        reportDTO.setExpressionAbilityScore(dimensionScoreDTO.getExpressionAbilityScore());
        return reportDTO;
    }

    /**
     * 构建兜底报告
     */
    private AgentInterviewReportDTO buildFallbackReport(IntervieweeForm form,
                                                        InterviewDimensionScoreDTO dimensionScoreDTO,
                                                        String reason) {
        String summaryReport = String.format(
                "%s候选人的本次面试总分为 %d 分，准确度 %d、完整度 %d、细节度 %d、逻辑度 %d、表达能力 %d。系统未能稳定生成完整自然语言报告，建议结合单题评分结果进行人工复核。",
                FALLBACK_COMMENT_PREFIX,
                dimensionScoreDTO.getInterviewPoint(),
                dimensionScoreDTO.getAccuracyScore(),
                dimensionScoreDTO.getCompletenessScore(),
                dimensionScoreDTO.getLevelOfDetailScore(),
                dimensionScoreDTO.getLogicScore(),
                dimensionScoreDTO.getExpressionAbilityScore()
        );
        String adviceReport = String.format(
                "%s建议优先围绕目标岗位“%s”补齐核心知识点，并结合项目经历补充更完整的技术方案、关键权衡与落地细节。若需要正式报告，建议在系统负载较低时重新生成。",
                FALLBACK_COMMENT_PREFIX,
                StrUtil.blankToDefault(form.getJobIntention(), "目标岗位")
        );

        AgentInterviewReportDTO reportDTO = AgentInterviewReportDTO.builder()
                .summaryReport(summaryReport)
                .adviceReport(adviceReport + " 原因：" + reason)
                .build();
        reportDTO.setInterviewPoint(dimensionScoreDTO.getInterviewPoint());
        reportDTO.setAccuracyScore(dimensionScoreDTO.getAccuracyScore());
        reportDTO.setCompletenessScore(dimensionScoreDTO.getCompletenessScore());
        reportDTO.setLevelOfDetailScore(dimensionScoreDTO.getLevelOfDetailScore());
        reportDTO.setLogicScore(dimensionScoreDTO.getLogicScore());
        reportDTO.setExpressionAbilityScore(dimensionScoreDTO.getExpressionAbilityScore());
        return reportDTO;
    }


    /**
     * 带有重试&兜底的报告名称生成
     */
    private String generateRecordNameSafely(IntervieweeForm form) {
        String model = AIModelEnum.NAME_GENERATE_AI_MODEL.getModel();
        for (int attempt = 1; attempt <= MODEL_MAX_ATTEMPTS; attempt++) {
            try {
                String rawContent = callModelForMessage(
                        model,
                        AIPromptConstant.RECORD_NAME_GENERATE + JSON.toJSONString(form),
                        -1,
                        "record-name",
                        attempt
                );
                String recordName = normalizeRecordName(rawContent);
                if (StrUtil.isBlank(recordName)) {
                    throw new IllegalArgumentException("记录名为空");
                }
                log.info("记录名生成成功 model={} attempt={} recordName={}", model, attempt, recordName);
                return recordName;
            } catch (Exception ex) {
                log.warn("记录名生成失败 model={} attempt={} formJobIntention={}",
                        model, attempt, form.getJobIntention(), ex);
                if (isNonRetryableModelException(ex)) {
                    break;
                }
            }
        }
        return buildFallbackRecordName(form);
    }

    private String normalizeRecordName(String rawContent) {
        return normalizeModelContent(rawContent)
                .replace("\"", "")
                .replace("'", "")
                .replaceAll("\\s+", "");
    }

    private String buildFallbackRecordName(IntervieweeForm form) {
        String jobIntention = StrUtil.blankToDefault(form.getJobIntention(), "面试");
        String normalized = jobIntention.replaceAll("[^\\p{IsHan}A-Za-z0-9]", "");
        if (normalized.length() > 8) {
            normalized = normalized.substring(0, 8);
        }
        if (!normalized.endsWith("面试")) {
            normalized = normalized + "面试";
        }
        return normalized;
    }


    private void normalizeEvaluationResp(ApiEvaluationResp apiEvaluationResp) {
        apiEvaluationResp.setAccuracy(normalizeSingleScore(apiEvaluationResp.getAccuracy()));
        apiEvaluationResp.setCompleteness(normalizeSingleScore(apiEvaluationResp.getCompleteness()));
        apiEvaluationResp.setLevelOfDetail(normalizeSingleScore(apiEvaluationResp.getLevelOfDetail()));
        apiEvaluationResp.setLogic(normalizeSingleScore(apiEvaluationResp.getLogic()));
        apiEvaluationResp.setExpressionAbility(normalizeSingleScore(apiEvaluationResp.getExpressionAbility()));
    }

    private int normalizeSingleScore(int score) {
        return Math.max(0, Math.min(score, 10));
    }

    private int normalizeTotalScore(double score) {
        return normalizeSingleScore((int) Math.round(score));
    }

    private IntervieweeForm getIntervieweeFormById(String formId) {
        Long formIdValue = parseFormId(formId);
        Long userId = getCurrentUserId();
        LambdaQueryWrapper<IntervieweeFormDO> queryWrapper = new LambdaQueryWrapper<IntervieweeFormDO>()
                .eq(IntervieweeFormDO::getId, formIdValue)
                .eq(IntervieweeFormDO::getUserId, userId);
        IntervieweeFormDO intervieweeFormDO = intervieweeFormDOMapper.selectOne(queryWrapper);
        if (ObjectUtil.isNull(intervieweeFormDO)) {
            throw new ClientException("简历不存在或无访问权限");
        }
        return IntervieweeForm.builder()
                .candidateName(intervieweeFormDO.getCandidateName())
                .jobIntention(intervieweeFormDO.getJobIntention())
                .professionalSkills(readStringList(intervieweeFormDO.getProfessionalSkills()))
                .educationExperiences(readEducationExperiences(intervieweeFormDO.getEducationExperiences()))
                .workExperiences(readWorkExperiences(intervieweeFormDO.getWorkExperiences()))
                .projectExperiences(readProjectExperiences(intervieweeFormDO.getProjectExperiences()))
                .build();
    }

    private Long parseFormId(String formId) {
        if (StrUtil.isBlank(formId)) {
            throw new ClientException("简历 id 不能为空，请检查");
        }
        try {
            return Long.parseLong(formId);
        } catch (NumberFormatException ex) {
            throw new ClientException("简历 id 格式错误，必须为数字字符串");
        }
    }

    private Long getCurrentUserId() {
        Long userId = UserContext.getId();
        if (ObjectUtil.isNull(userId)) {
            throw new ClientException("当前用户未登录或登录已失效");
        }
        return userId;
    }

    private List<String> readStringList(String json) {
        if (StrUtil.isBlank(json)) {
            return Collections.emptyList();
        }
        return JSON.parseArray(json, String.class);
    }

    private List<EducationExperience> readEducationExperiences(String json) {
        if (StrUtil.isBlank(json)) {
            return Collections.emptyList();
        }
        return JSON.parseArray(json, EducationExperience.class);
    }

    private List<WorkExperience> readWorkExperiences(String json) {
        if (StrUtil.isBlank(json)) {
            return Collections.emptyList();
        }
        return JSON.parseArray(json, WorkExperience.class);
    }

    private List<ProjectExperience> readProjectExperiences(String json) {
        if (StrUtil.isBlank(json)) {
            return Collections.emptyList();
        }
        return JSON.parseArray(json, ProjectExperience.class);
    }

    /**
     * 评分结果在服务内部以强类型对象流转，避免频繁用 JSONObject 传递字段名。
     */
    private static final class EvaluationScorePayload {
        private final int completeness;
        private final int levelOfDetail;
        private final int accuracy;
        private final int logic;
        private final int expressionAbility;

        private EvaluationScorePayload(int completeness, int levelOfDetail, int accuracy, int logic, int expressionAbility) {
            this.completeness = completeness;
            this.levelOfDetail = levelOfDetail;
            this.accuracy = accuracy;
            this.logic = logic;
            this.expressionAbility = expressionAbility;
        }

        private int getCompleteness() {
            return completeness;
        }

        private int getLevelOfDetail() {
            return levelOfDetail;
        }

        private int getAccuracy() {
            return accuracy;
        }

        private int getLogic() {
            return logic;
        }

        private int getExpressionAbility() {
            return expressionAbility;
        }
    }

    /**
     * 自定义线程名前缀，方便从日志直接区分批量评分与其他业务线程。
     */
    private static final class NamedThreadFactory implements ThreadFactory {
        private final AtomicInteger sequence = new AtomicInteger(1);
        private final String prefix;

        private NamedThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable);
            thread.setName(prefix + sequence.getAndIncrement());
            return thread;
        }
    }
}
