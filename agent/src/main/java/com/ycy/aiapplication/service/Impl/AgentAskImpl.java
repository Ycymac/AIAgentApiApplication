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
import com.ycy.aiapplication.common.pojo.EducationExperience;
import com.ycy.aiapplication.common.pojo.InterviewQuestion;
import com.ycy.aiapplication.common.pojo.IntervieweeForm;
import com.ycy.aiapplication.common.pojo.ProjectExperience;
import com.ycy.aiapplication.common.pojo.QuestionWithAnswer;
import com.ycy.aiapplication.common.pojo.WorkExperience;
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
import com.ycy.aiapplication.user.service.common.context.UserContext;
import com.ycy.aiapplication.user.service.dao.entity.IntervieweeFormDO;
import com.ycy.aiapplication.user.service.dao.mapper.IntervieweeFormDOMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentAskImpl implements AgentAsk {

    @Value("${ai-application.agent.api.key:}")
    private String apiKey;

    private final ExecutorService executorService = new ThreadPoolExecutor(
            Runtime.getRuntime().availableProcessors(),
            Runtime.getRuntime().availableProcessors() * 2,
            60L,
            TimeUnit.SECONDS,
            new SynchronousQueue<>(),
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
        InterviewQuestion question = requestParam.getQuestion();
        String answer = requestParam.getAnswer();
        if (ObjectUtil.isNull(question)) {
            throw new ClientException("问题不能为空，请检查");
        }
        if (question.getLevel() < 0 || question.getLevel() > 2 || StrUtil.isEmpty(question.getQuestionDescription())) {
            throw new ClientException("问题描述或等级不能为空，请检查");
        }
        if (StrUtil.isEmpty(answer)) {
            throw new ClientException("回答不能为空，请检查");
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
                .content(json + AIPromptConstant.ANSWER_POINT_GIVE_V2)
                .build();
        GenerationParam param = GenerationParam.builder()
                .apiKey(apiKey)
                .model(AIModelEnum.EVALUATION_AI_MODEL.getModel())
                .messages(Arrays.asList(systemMsg, userMsg))
                .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                .build();

        try {
            GenerationResult result = generation.call(param);
            String answerJson = result.getOutput().getChoices().get(0).getMessage().getContent();
            log.info("消息内容: {}", answerJson);
            ApiEvaluationResp apiEvaluationResp = JSONObject.parseObject(answerJson, ApiEvaluationResp.class);
            normalizeEvaluationResp(apiEvaluationResp);
            return new AnswerEvaluationRespDTO(requestParam, apiEvaluationResp);
        } catch (Exception ex) {
            if (ex instanceof NoApiKeyException) {
                log.error("缺少 apiKey: {}", ex.getMessage());
            } else if (ex instanceof ApiException) {
                log.error("调用 AI 接口失败: {}", ex.getMessage());
            } else if (ex instanceof InputRequiredException) {
                log.error("请求参数缺失: {}", ex.getMessage());
            } else {
                log.error("回答评估异常: {}", ex.getMessage(), ex);
            }
            throw new ClientException("系统异常，请稍后重试");
        }
    }

    @Override
    public List<AnswerEvaluationRespDTO> answersEvaluationByAsync(List<QuestionWithAnswer> requestParams) {
        if (requestParams == null) {
            throw new ClientException("评估参数不足，请检查");
        }

        List<CompletableFuture<AnswerEvaluationRespDTO>> evaluationTasks = requestParams.stream()
                .map(each -> {
                    int index = each.getQuestion().getNum();
                    return CompletableFuture
                            .supplyAsync(() -> singleQuestionAnswerEvaluation(each), executorService)
                            .exceptionally(ex -> {
                                log.error("第 {} 题评估失败: {}", index, ex.getMessage());
                                throw new CompletionException(ex);
                            });
                })
                .toList();

        return CompletableFuture.allOf(evaluationTasks.toArray(CompletableFuture[]::new))
                .thenApply(allTasks -> evaluationTasks.stream()
                        .map(CompletableFuture::join)
                        .collect(Collectors.toList()))
                .thenApply(list -> {
                    list.sort(Comparator.comparingInt(each -> each.getQuestion().getNum()));
                    return list;
                })
                .join();
    }

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

    private String buildWorkDescription(List<WorkExperience> workExperiences) {
        return workExperiences.stream()
                .map(each -> each.getCompanyName() + " " + each.getPosition() + " " + each.getWorkContent())
                .collect(Collectors.joining("；"));
    }

    private String buildProjectDescription(ProjectExperience projectExperience) {
        return projectExperience.getProjectName()
                + "（角色：" + projectExperience.getProjectRole()
                + "，项目描述：" + projectExperience.getProjectDescription()
                + "，职责：" + projectExperience.getResponsibility()
                + "，成果：" + projectExperience.getAchievement() + "）";
    }

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

    @Override
    public ReportGenerationRespDTO generateInterviewReportAndRecordName(ReportGenerationReqDTO requestParam) {
        if (ObjectUtil.isEmpty(requestParam)) {
            throw new ClientException("参数不能为空");
        }
        if (CollectionUtil.isEmpty(requestParam.getAnswerEvaluationRespS())) {
            throw new ClientException("回答评估结果不能为空");
        }

        Long userId = getCurrentUserId();
        IntervieweeForm form = getIntervieweeFormById(requestParam.getFormId());
        InterviewDimensionScoreDTO dimensionScoreDTO = calculateDimensionScore(requestParam.getAnswerEvaluationRespS());
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("form", form);
        jsonObject.put("evaluations", requestParam.getAnswerEvaluationRespS());
        jsonObject.put("interviewPoint", dimensionScoreDTO.getInterviewPoint());
        jsonObject.put("accuracyScore", dimensionScoreDTO.getAccuracyScore());
        jsonObject.put("completenessScore", dimensionScoreDTO.getCompletenessScore());
        jsonObject.put("levelOfDetailScore", dimensionScoreDTO.getLevelOfDetailScore());
        jsonObject.put("logicScore", dimensionScoreDTO.getLogicScore());
        jsonObject.put("expressionAbilityScore", dimensionScoreDTO.getExpressionAbilityScore());

        CompletableFuture<AgentInterviewReportDTO> reportFuture = CompletableFuture
                .supplyAsync(() -> generateInterviewReport(jsonObject, dimensionScoreDTO), executorService);
        CompletableFuture<String> recordNameFuture = CompletableFuture
                .supplyAsync(() -> generateRecordName(form), executorService);

        return reportFuture.thenCombine(recordNameFuture, (apiInterviewReport, recordName) -> {
            String interviewProcessRecord = JSON.toJSONString(requestParam.getAnswerEvaluationRespS());
            String interviewKeywords = JSON.toJSONString(form.getProfessionalSkills());
            InterviewRecordDO recordDO = InterviewRecordDO.builder()
                    .recordName(recordName)
                    .userId(userId)
                    .interviewProcessRecord(interviewProcessRecord)
                    .interviewKeywords(interviewKeywords)
                    .summaryReportRecord(JSON.toJSONString(apiInterviewReport.getSummaryReport()))
                    .adviceReportRecord(JSON.toJSONString(apiInterviewReport.getAdviceReport()))
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

            return ReportGenerationRespDTO.builder()
                    .date(now)
                    .recordName(recordName)
                    .reportDTO(apiInterviewReport)
                    .build();
        }).join();
    }

    private AgentInterviewReportDTO generateInterviewReport(JSONObject jsonObject, InterviewDimensionScoreDTO dimensionScoreDTO) {
        Generation generation = new Generation();
        Message systemMsg = Message.builder()
                .role(Role.SYSTEM.getValue())
                .content(AIPromptConstant.SYSTEM_ROLE_CONTENT)
                .build();
        Message userMsg = Message.builder()
                .role(Role.USER.getValue())
                .content(AIPromptConstant.SUMMARY_ASK_V2 + jsonObject.toJSONString())
                .build();
        GenerationParam param = GenerationParam.builder()
                .apiKey(apiKey)
                .model(AIModelEnum.SUMMARY_GENERATE_AI_MODEL.getModel())
                .messages(Arrays.asList(systemMsg, userMsg))
                .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                .build();

        try {
            GenerationResult result = generation.call(param);
            String answerJson = result.getOutput().getChoices().get(0).getMessage().getContent();
            log.info("消息内容: {}", answerJson);
            AgentInterviewReportDTO agentInterviewReport = JSONObject.parseObject(answerJson, AgentInterviewReportDTO.class);
            agentInterviewReport.setInterviewPoint(dimensionScoreDTO.getInterviewPoint());
            agentInterviewReport.setAccuracyScore(dimensionScoreDTO.getAccuracyScore());
            agentInterviewReport.setCompletenessScore(dimensionScoreDTO.getCompletenessScore());
            agentInterviewReport.setLevelOfDetailScore(dimensionScoreDTO.getLevelOfDetailScore());
            agentInterviewReport.setLogicScore(dimensionScoreDTO.getLogicScore());
            agentInterviewReport.setExpressionAbilityScore(dimensionScoreDTO.getExpressionAbilityScore());
            return agentInterviewReport;
        } catch (Exception ex) {
            if (ex instanceof NoApiKeyException) {
                log.error("缺少 apiKey: {}", ex.getMessage());
            } else if (ex instanceof ApiException) {
                log.error("调用 AI 接口失败: {}", ex.getMessage());
            } else if (ex instanceof InputRequiredException) {
                log.error("请求参数缺失: {}", ex.getMessage());
            } else {
                log.error("生成面试报告异常: {}", ex.getMessage(), ex);
            }
            throw new ClientException("系统异常，请稍后重试");
        }
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

    private String generateRecordName(IntervieweeForm form) {
        String json = JSON.toJSONString(form);
        Generation generation = new Generation();
        Message systemMsg = Message.builder()
                .role(Role.SYSTEM.getValue())
                .content(AIPromptConstant.SYSTEM_ROLE_CONTENT)
                .build();
        Message userMsg = Message.builder()
                .role(Role.USER.getValue())
                .content(AIPromptConstant.RECORD_NAME_GENERATE + json)
                .build();
        GenerationParam param = GenerationParam.builder()
                .apiKey(apiKey)
                .model(AIModelEnum.NAME_GENERATE_AI_MODEL.getModel())
                .messages(Arrays.asList(systemMsg, userMsg))
                .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                .build();

        try {
            GenerationResult result = generation.call(param);
            String answerString = result.getOutput().getChoices().get(0).getMessage().getContent();
            log.info("消息内容: {}", answerString);
            return answerString;
        } catch (Exception ex) {
            if (ex instanceof NoApiKeyException) {
                log.error("缺少 apiKey: {}", ex.getMessage());
            } else if (ex instanceof ApiException) {
                log.error("调用 AI 接口失败: {}", ex.getMessage());
            } else if (ex instanceof InputRequiredException) {
                log.error("请求参数缺失: {}", ex.getMessage());
            } else {
                log.error("生成记录名异常: {}", ex.getMessage(), ex);
            }
            throw new ClientException("系统异常，请稍后重试");
        }
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
}
