
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

    @Override
    public InterviewQuestionAskRespDTO giveInterviewQuestions(InterviewQuestionAskReqDTO requestParam) {
        if (ObjectUtil.isNull(requestParam)) {
            throw new RemoteException("用户参数部分未填写，请检查");
        }

        IntervieweeForm form = getIntervieweeFormById(requestParam.getFormId());
        if (StrUtil.isEmpty(form.getCandidateName())
                || StrUtil.isEmpty(form.getJobIntention())
                || CollectionUtil.isEmpty(form.getProfessionalSkills())
                || CollectionUtil.isEmpty(form.getEducationExperiences())
                || CollectionUtil.isEmpty(form.getProjectExperiences())) {
            throw new RemoteException("用户参数部分未填写，请检查");
        }

        String description = buildFormDescription(form);
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

        try {
            GenerationResult result = generation.call(param);
            String json = result.getOutput().getChoices().get(0).getMessage().getContent();
            log.info("消息内容: {}", json);
            List<InterviewQuestion> questions = JSON.parseArray(json, InterviewQuestion.class);
            if (questions.size() != 15) {
                throw new RuntimeException("题目数量不正确");
            }
            for (InterviewQuestion each : questions) {
                if (each.getLevel() < 0 || each.getLevel() > 2) {
                    throw new RuntimeException("非法 level 值");
                }
            }
            return InterviewQuestionAskRespDTO.builder()
                    .questions(questions)
                    .build();
        } catch (Exception ex) {
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

    @Override
    public AnswerEvaluationRespDTO singleQuestionAnswerEvaluation(QuestionWithAnswer requestParam) {
        return evaluateSingleQuestionWithStructuredFlow(requestParam);
    }

    @Override
    public List<AnswerEvaluationRespDTO> answersEvaluationByAsync(List<QuestionWithAnswer> requestParams) {
        return evaluateAnswersWithInvokeAll(requestParams);
    }

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
     * 批量评分
     * 修改点： 批量评分改为 invokeAll：
     * 1. 提交与收集语义更稳定；
     * 2. 不再出现 CompletableFuture 嵌套异常包装；
     * 3. 单题失败时直接落到兜底，不会导致整批 join 失败。
     */
    private List<AnswerEvaluationRespDTO> evaluateAnswersWithInvokeAll(List<QuestionWithAnswer> requestParams) {
        if (requestParams == null) {
            throw new ClientException("评估参数不足，请检查");
        }
        if (CollectionUtil.isEmpty(requestParams)) {
            return Collections.emptyList();
        }

        long startTime = System.currentTimeMillis();
        log.info("开始批量评估 total={} parallelism={} queueCapacity={}",
                requestParams.size(), AI_TASK_PARALLELISM, AI_TASK_QUEUE_CAPACITY);

        List<Callable<AnswerEvaluationRespDTO>> tasks = requestParams.stream()
                .map(each -> (Callable<AnswerEvaluationRespDTO>) () -> evaluateQuestionSafely(each))
                .toList();

        List<Future<AnswerEvaluationRespDTO>> futures;
        try {
            futures = aiTaskExecutor.invokeAll(tasks);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.error("批量评估任务提交被中断 total={}", requestParams.size(), ex);
            return buildInterruptedBatchFallback(requestParams);
        }

        List<AnswerEvaluationRespDTO> resultList = new ArrayList<>(requestParams.size());
        for (int i = 0; i < futures.size(); i++) {
            QuestionWithAnswer requestParam = requestParams.get(i);
            try {
                resultList.add(futures.get(i).get());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                log.error("批量评估结果获取被中断 questionNum={}", requestParam.getQuestion().getNum(), ex);
                resultList.add(buildModelFailureFallback(requestParam, "评估任务被中断，已按保守策略完成评分。"));
            } catch (ExecutionException ex) {
                log.error("批量评估结果获取异常 questionNum={}", requestParam.getQuestion().getNum(), ex);
                resultList.add(buildModelFailureFallback(requestParam, "评估任务执行异常，已按保守策略完成评分。"));
            }
        }

        resultList.sort(Comparator.comparingInt(each -> each.getQuestion().getNum()));
        long fallbackCount = resultList.stream().filter(this::isFallbackEvaluation).count();
        log.info("批量评估完成 total={} fallbackCount={} costMs={}",
                resultList.size(), fallbackCount, System.currentTimeMillis() - startTime);
        return resultList;
    }

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
     * 判断当前用户回答是否过短、且意图为“不会”等类似词句
     *
     * @param answer 用户回答
     */
    private boolean shouldUseRuleBasedFallback(String answer) {
        String normalized = answer.replaceAll("\\s+", "");
        if (normalized.length() < MIN_ANSWER_LENGTH_FOR_AI) {
            return true;
        }
        return Arrays.asList("不知道", "不会", "不清楚", "无", "略", "跳过", "未作答", "没有")
                .contains(normalized);
    }

    /**
     * 将题目信息统一收敛为 JSON 文本，确保两个模型使用完全一致的输入语义。
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
     * @param questionNum  问题编号
     * @param evaluationInput  评估输入
     * @return 大模型评语
     */
    private String requestEvaluationComment(int questionNum, String evaluationInput) {
        String model = AIModelEnum.EVALUATION_COMMENT_AI_MODEL.getModel();
        for (int attempt = 1; attempt <= MODEL_MAX_ATTEMPTS; attempt++) {
            try {
                String rawContent = callModelForMessage(model,
                        evaluationInput + AIPromptConstant.ANSWER_COMMENT_GIVE,
                        questionNum, "evaluation-comment", attempt);
                String comment = parseCommentPayload(rawContent);
                log.info("评语生成成功 questionNum={} attempt={} comment={}",
                        questionNum, attempt, truncateForLog(comment));
                return comment;
            } catch (Exception ex) {
                log.warn("评语生成失败 questionNum={} model={} attempt={}",
                        questionNum, model, attempt, ex);
                if (isNonRetryableModelException(ex)) {
                    break;
                }
            }
        }
        return FALLBACK_COMMENT_PREFIX + "评语模型输出异常，建议结合原回答人工复核。";
    }

    /**
     * 带有简单重试机制的分数计算
     * @param questionNum 问题比那好
     * @param evaluationInput  评估输入
     * @param answer 问题回答
     * @return 评估分数
     */
    private EvaluationScorePayload requestEvaluationScore(int questionNum, String evaluationInput, String answer) {
        String model = AIModelEnum.EVALUATION_SCORE_AI_MODEL.getModel();
        for (int attempt = 1; attempt <= MODEL_MAX_ATTEMPTS; attempt++) {
            try {
                String rawContent = callModelForMessage(model,
                        evaluationInput + AIPromptConstant.ANSWER_SCORE_GIVE,
                        questionNum, "evaluation-score", attempt);
                EvaluationScorePayload payload = parseScorePayload(rawContent);
                log.info("打分生成成功 questionNum={} attempt={} accuracy={} completeness={} detail={} logic={} expression={}",
                        questionNum, attempt, payload.getAccuracy(), payload.getCompleteness(),
                        payload.getLevelOfDetail(), payload.getLogic(), payload.getExpressionAbility());
                return payload;
            } catch (Exception ex) {
                log.warn("打分生成失败 questionNum={} model={} attempt={}",
                        questionNum, model, attempt, ex);
                if (isNonRetryableModelException(ex)) {
                    break;
                }
            }
        }

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
     * @param model 模型类型
     * @param userContent 用户消息内容
     * @param questionNum 问题编号
     * @param stage  当前阶段
     * @param attempt 尝试次数
     * @return 模型调用结果

     */
    private String callModelForMessage(String model, String userContent, int questionNum, String stage, int attempt)
            throws NoApiKeyException, ApiException, InputRequiredException {
        long startTime = System.currentTimeMillis();
        Generation generation = new Generation();
        Message systemMsg = Message.builder()
                .role(Role.SYSTEM.getValue())
                .content(AIPromptConstant.SYSTEM_ROLE_CONTENT)
                .build();
        Message userMsg = Message.builder()
                .role(Role.USER.getValue())
                .content(userContent)
                .build();
        GenerationParam param = GenerationParam.builder()
                .apiKey(apiKey)
                .model(model)
                .messages(Arrays.asList(systemMsg, userMsg))
                .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                .build();

        log.info("模型调用开始 questionNum={} stage={} model={} attempt={}",
                questionNum, stage, model, attempt);
        GenerationResult result = generation.call(param);
        String content = result.getOutput().getChoices().get(0).getMessage().getContent();
        log.info("模型调用完成 questionNum={} stage={} model={} attempt={} costMs={} raw={}",
                questionNum, stage, model, attempt, System.currentTimeMillis() - startTime, truncateForLog(content));
        return content;
    }


    /**
     *JSON字符串解析comment
     * @param rawContent comment对应的json字符串
     * @return  转换为字符串的评价
     */
    private String parseCommentPayload(String rawContent) {
        JSONObject jsonObject = JSONObject.parseObject(extractFirstJsonObject(rawContent));
        String comment = jsonObject.getString("comment");
        if (StrUtil.isBlank(comment)) {
            throw new IllegalArgumentException("comment 字段缺失");
        }
        return comment.trim();
    }

    /**
     * JSON字符串解析为五大分数
     * @param rawContent  分数对应的json字符串
     * @return  结构化分数
     */
    private EvaluationScorePayload parseScorePayload(String rawContent) {
        JSONObject jsonObject = JSONObject.parseObject(extractFirstJsonObject(rawContent));
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
     * @param jsonObject json对象
     * @param fieldName 对应字段名
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
     * 使用嵌套深度追踪，解析json对象
     * @param rawContent 原始内容
     * @return 提取后内容
     */
    private String extractFirstJsonObject(String rawContent) {
        //去除md标记&空白字符
        String normalized = normalizeModelContent(rawContent);
        //定位起始符
        int start = normalized.indexOf('{');
        if (start < 0) {
            throw new IllegalArgumentException("未找到 JSON 对象起始符");
        }
        //嵌套深度
        int depth = 0;
        //标记但却按是否在字符串内部
        boolean inQuotes = false;
        //标记前一个字符是否为转义字符
        boolean escaped = false;
        //从json的可能开始位置遍历到字符串末尾
        for (int i = start; i < normalized.length(); i++) {
            char current = normalized.charAt(i);
            //前一个字符是转义字符，跳过当前字符判断
            //避免将  \“ 判断为字符串结束
            if (escaped) {
                escaped = false;
                continue;
            }
            //当前为转义字符 '\'
            //是的话设置判断标识
            if (current == '\\') {
                escaped = true;
                continue;
            }
            //判断当前是否在字符串当中
            //用于区分json字符串外部括号&字符串内部字符
            if (current == '"') {
                inQuotes = !inQuotes;
                continue;
            }
            //当前在字符串内部，跳过字符判断，避免字符串内普通字符影响判断json结构
            if (inQuotes) {
                continue;
            }
            //当前为前括号，说明嵌套深度+1
            if (current == '{') {
                depth++;
                //遇到后括号，不一定是json字符串结尾，只能说明嵌套深度-1
            } else if (current == '}') {
                depth--;
                //嵌套深度为0，说明为json结尾
                if (depth == 0) {
                    return normalized.substring(start, i + 1);
                }
            }
        }
        throw new IllegalArgumentException("JSON 对象不完整");
    }

    /**
     * 模型输出归一化
     * 功能：预防模型使用md输出导致输出不合规
     * @param rawContent 输出内容
     * @return 归一化输出
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
     * @param ex 异常
     */
    private boolean isNonRetryableModelException(Exception ex) {
        //没用api 调用密钥/缺失输入异常，无法重试
        return ex instanceof NoApiKeyException || ex instanceof InputRequiredException;
    }

    /**
     * 当分数模型持续异常时，使用回答长度与技术关键词命中率生成保守分数。
     * 该兜底只用于“模型不可用”场景，因此 accuracy 被严格限制在较低区间，避免误判为高分。
     */
    private EvaluationScorePayload buildHeuristicScorePayload(String answer) {
        //字符串归一化
        String normalized = answer.replaceAll("\\s+", "");
        int length = normalized.length();
        //小于规定长度，分数为0
        if (length < MIN_ANSWER_LENGTH_FOR_AI) {
            return new EvaluationScorePayload(0, 0, 0, 0, 0);
        }
        //关键字
        int keywordHits = countTechnicalKeywordHits(normalized.toLowerCase());
        //结构化
        boolean structured = containsAny(normalized, "首先", "其次", "最后", "因为", "所以", "方案", "实现", "步骤", "1.", "2.", "3.");

        int completeness = Math.min(8, scoreByLength(length, 2, 4, 5, 6, 7) + Math.min(1, keywordHits / 3));
        int levelOfDetail = Math.min(8, scoreByLength(length, 1, 3, 4, 5, 6) + (structured ? 1 : 0));
        int accuracy = Math.min(4, scoreByLength(length, 0, 1, 2, 3, 3) + Math.min(1, keywordHits / 4));
        int logic = Math.min(7, scoreByLength(length, 1, 2, 3, 4, 5) + (structured ? 2 : 0));
        int expressionAbility = Math.min(7, scoreByLength(length, 1, 2, 3, 4, 5) + (structured ? 1 : 0));
        return new EvaluationScorePayload(completeness, levelOfDetail, accuracy, logic, expressionAbility);
    }

    /**
     * 简单计算术语命中次数
     * 用于兜底判断
     * @param answer 回答问题
     * @return  术语命中次数
     */
    private int countTechnicalKeywordHits(String answer) {
        List<String> keywords = Arrays.asList(
                "redis", "mysql", "rocketmq", "rabbitmq", "spring", "java",
                "分布式", "缓存", "数据库", "事务", "锁", "一致性", "幂等", "消息队列"
        );
        int hits = 0;
        for (String keyword : keywords) {
            if (answer.contains(keyword)) {
                hits++;
            }
        }
        return hits;
    }

    /**
     * 判断关键字包含
     * 用于兜底判断
     * @param text 文本
     * @param fragments 关键字
     * @return 是否包含
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
     * @param requestParam 面试问题&回答输入类
     * @return  面试评价
     */
    private AnswerEvaluationRespDTO buildShortAnswerFallback(QuestionWithAnswer requestParam) {
        return buildFallbackEvaluation(requestParam,
                FALLBACK_COMMENT_PREFIX + "回答内容过短或未有效作答，系统按低分处理。",
                new EvaluationScorePayload(0, 0, 0, 0, 0));
    }

    /**
     * 模型调用失败降级回答构建
     * @param requestParam  请求参数
     * @param reason 原因
     * @return 评估返回类
     */
    private AnswerEvaluationRespDTO buildModelFailureFallback(QuestionWithAnswer requestParam, String reason) {
        if (requestParam == null) {
            throw new ClientException("评估参数不足，请检查");
        }
        EvaluationScorePayload heuristicScore = buildHeuristicScorePayload(StrUtil.blankToDefault(requestParam.getAnswer(), ""));
        return buildFallbackEvaluation(requestParam, FALLBACK_COMMENT_PREFIX + reason, heuristicScore);
    }

    /**
     * 构建降级评估返回对象
     * @param requestParam  问题&面试对象回答
     * @param comment 大模型评价
     * @param scorePayload 所有分数
     * @return 评估返回类对象
     */
    private AnswerEvaluationRespDTO buildFallbackEvaluation(QuestionWithAnswer requestParam, String comment,
                                                            EvaluationScorePayload scorePayload) {
        ApiEvaluationResp apiEvaluationResp = ApiEvaluationResp.builder()
                .comment(comment)
                .completeness(scorePayload.getCompleteness())
                .levelOfDetail(scorePayload.getLevelOfDetail())
                .accuracy(scorePayload.getAccuracy())
                .logic(scorePayload.getLogic())
                .expressionAbility(scorePayload.getExpressionAbility())
                .build();
        normalizeEvaluationResp(apiEvaluationResp);
        return new AnswerEvaluationRespDTO(requestParam, apiEvaluationResp);
    }

    /**
     * 批量评估终端，构建兜底保守策略评分
     * @param requestParams 问题&回答
     * @return 兜底评估
     */
    private List<AnswerEvaluationRespDTO> buildInterruptedBatchFallback(List<QuestionWithAnswer> requestParams) {
        return requestParams.stream()
                .map(each -> buildModelFailureFallback(each, "批量评估被中断，已按保守策略完成评分。"))
                .sorted(Comparator.comparingInt(each -> each.getQuestion().getNum()))
                .collect(Collectors.toList());
    }

    /**
     * 判断是否是兜底评估
     */
    private boolean isFallbackEvaluation(AnswerEvaluationRespDTO dto) {
        return dto != null
                && dto.getApiResp() != null
                && StrUtil.startWith(dto.getApiResp().getComment(), FALLBACK_COMMENT_PREFIX);
    }

    private String truncateForLog(String content) {
        String normalized = StrUtil.blankToDefault(content, "").replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 300) {
            return normalized;
        }
        return normalized.substring(0, 300) + "...";
    }

    /**
     * 通过面试人简历构建对应描述文本
     */
    private String buildFormDescription(IntervieweeForm form) {
        StringBuilder builder = new StringBuilder();
        builder.append("候选人姓名：").append(form.getCandidateName())
                .append("；求职意向：").append(form.getJobIntention());
        if (CollectionUtil.isNotEmpty(form.getProfessionalSkills())) {
            builder.append("；专业技能：")
                    .append(String.join("、", form.getProfessionalSkills()));
        }
        if (CollectionUtil.isNotEmpty(form.getEducationExperiences())) {
            builder.append("；教育经历：")
                    .append(form.getEducationExperiences().stream()
                            .map(each -> each.getSchoolName() + " " + each.getMajor() + " " + each.getDegree())
                            .collect(Collectors.joining("；")));
        }
        if (CollectionUtil.isNotEmpty(form.getWorkExperiences())) {
            builder.append("；工作经历：")
                    .append(buildWorkDescription(form.getWorkExperiences()));
        }
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
     */
    private String buildWorkDescription(List<WorkExperience> workExperiences) {
        return workExperiences.stream()
                .map(each -> each.getCompanyName() + " " + each.getPosition() + " " + each.getWorkContent())
                .collect(Collectors.joining("；"));
    }

    /**
     * 项目经历构建
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
     */
    private InterviewDimensionScoreDTO calculateDimensionScore(List<AnswerEvaluationRespDTO> list) {
        double totalAccuracyScore = 0;
        double totalCompletenessScore = 0;
        double totalLevelOfDetailScore = 0;
        double totalLogicScore = 0;
        double totalExpressionAbilityScore = 0;
        double totalWeight = 0;

        for (AnswerEvaluationRespDTO dto : list) {
            ApiEvaluationResp apiResp = dto.getApiResp();
            InterviewQuestion question = dto.getQuestion();

            double weight = switch (question.getLevel()) {
                case 0 -> 1.0;
                case 1 -> 1.2;
                case 2 -> 1.5;
                default -> 1.0;
            };

            totalAccuracyScore += apiResp.getAccuracy() * weight;
            totalCompletenessScore += apiResp.getCompleteness() * weight;
            totalLevelOfDetailScore += apiResp.getLevelOfDetail() * weight;
            totalLogicScore += apiResp.getLogic() * weight;
            totalExpressionAbilityScore += apiResp.getExpressionAbility() * weight;
            totalWeight += weight;
        }

        int accuracyScore = normalizeTotalScore(totalAccuracyScore / totalWeight);
        int completenessScore = normalizeTotalScore(totalCompletenessScore / totalWeight);
        int levelOfDetailScore = normalizeTotalScore(totalLevelOfDetailScore / totalWeight);
        int logicScore = normalizeTotalScore(totalLogicScore / totalWeight);
        int expressionAbilityScore = normalizeTotalScore(totalExpressionAbilityScore / totalWeight);

        int interviewPoint = (int) Math.round((
                accuracyScore * 0.35 +
                        completenessScore * 0.20 +
                        levelOfDetailScore * 0.15 +
                        logicScore * 0.15 +
                        expressionAbilityScore * 0.15
        ) * 10);

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
