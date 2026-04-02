package com.ycy.aiapplication.user.service.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ycy.aiapplication.framework.exception.ClientException;
import com.ycy.aiapplication.user.service.common.constant.UserServiceRedisConstant;
import com.ycy.aiapplication.user.service.common.context.UserContext;
import com.ycy.aiapplication.user.service.common.pojo.EducationExperience;
import com.ycy.aiapplication.user.service.common.pojo.ProjectExperience;
import com.ycy.aiapplication.user.service.common.pojo.WorkExperience;
import com.ycy.aiapplication.user.service.dao.entity.IntervieweeFormDO;
import com.ycy.aiapplication.user.service.dao.mapper.IntervieweeFormDOMapper;
import com.ycy.aiapplication.user.service.dto.req.CreateIntervieweeFormReqDTO;
import com.ycy.aiapplication.user.service.dto.req.FuzzySearchIntervieweeFormReqDTO;
import com.ycy.aiapplication.user.service.dto.req.UpdateIntervieweeFormReqDTO;
import com.ycy.aiapplication.user.service.dto.resp.IntervieweeFormNameRespDTO;
import com.ycy.aiapplication.user.service.dto.resp.IntervieweeFormRespDTO;
import com.ycy.aiapplication.user.service.service.IntervieweeFormService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class IntervieweeFormServiceImpl implements IntervieweeFormService {

    private final IntervieweeFormDOMapper intervieweeFormDOMapper;

    private final StringRedisTemplate stringRedisTemplate;

    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public IntervieweeFormRespDTO addIntervieweeForm(CreateIntervieweeFormReqDTO requestParam) {
        validFormParam(
                requestParam.getFormName(),
                requestParam.getCandidateName(),
                requestParam.getJobIntention(),
                requestParam.getProfessionalSkills(),
                requestParam.getEducationExperiences(),
                requestParam.getWorkExperiences(),
                requestParam.getProjectExperiences());
        Long userId = getCurrentUserId();
        IntervieweeFormDO intervieweeFormDO = IntervieweeFormDO.builder()
                .formName(requestParam.getFormName())
                .userId(userId)
                .candidateName(requestParam.getCandidateName())
                .jobIntention(requestParam.getJobIntention())
                .professionalSkills(writeJson(requestParam.getProfessionalSkills()))
                .educationExperiences(writeJson(requestParam.getEducationExperiences()))
                .workExperiences(writeJson(defaultIfNull(requestParam.getWorkExperiences())))
                .projectExperiences(writeJson(requestParam.getProjectExperiences()))
                .build();
        intervieweeFormDOMapper.insert(intervieweeFormDO);
        addOrRefreshFormNameCache(intervieweeFormDO.getId(), requestParam.getFormName(), userId, intervieweeFormDO.getCreateTime());
        log.info("新增简历成功，userId:{}, formId:{}", userId, intervieweeFormDO.getId());
        return toRespDTO(intervieweeFormDO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateIntervieweeForm(UpdateIntervieweeFormReqDTO requestParam) {
        Long formId = parseFormId(requestParam.getId());
        validFormParam(
                requestParam.getFormName(),
                requestParam.getCandidateName(),
                requestParam.getJobIntention(),
                requestParam.getProfessionalSkills(),
                requestParam.getEducationExperiences(),
                requestParam.getWorkExperiences(),
                requestParam.getProjectExperiences());
        Long userId = getCurrentUserId();
        IntervieweeFormDO oldIntervieweeFormDO = getIntervieweeFormByIdAndUserId(formId, userId);
        IntervieweeFormDO updateIntervieweeFormDO = IntervieweeFormDO.builder()
                .id(formId)
                .formName(requestParam.getFormName())
                .candidateName(requestParam.getCandidateName())
                .jobIntention(requestParam.getJobIntention())
                .professionalSkills(writeJson(requestParam.getProfessionalSkills()))
                .educationExperiences(writeJson(requestParam.getEducationExperiences()))
                .workExperiences(writeJson(defaultIfNull(requestParam.getWorkExperiences())))
                .projectExperiences(writeJson(requestParam.getProjectExperiences()))
                .build();
        intervieweeFormDOMapper.updateById(updateIntervieweeFormDO);
        removeFormNameCache(oldIntervieweeFormDO.getId(), oldIntervieweeFormDO.getFormName(), userId);
        addOrRefreshFormNameCache(oldIntervieweeFormDO.getId(), requestParam.getFormName(), userId, oldIntervieweeFormDO.getCreateTime());
        log.info("修改简历成功，userId:{}, formId:{}", userId, formId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteIntervieweeForm(String id) {
        Long formId = parseFormId(id);
        Long userId = getCurrentUserId();
        IntervieweeFormDO intervieweeFormDO = getIntervieweeFormByIdAndUserId(formId, userId);
        intervieweeFormDOMapper.deleteById(formId);
        removeFormNameCache(intervieweeFormDO.getId(), intervieweeFormDO.getFormName(), userId);
        log.info("删除简历成功，userId:{}, formId:{}", userId, formId);
    }

    @Override
    public IntervieweeFormRespDTO searchIntervieweeFormById(String id) {
        Long formId = parseFormId(id);
        Long userId = getCurrentUserId();
        IntervieweeFormDO intervieweeFormDO = getIntervieweeFormByIdAndUserId(formId, userId);
        return toRespDTO(intervieweeFormDO);
    }

    @Override
    public List<IntervieweeFormRespDTO> fuzzySearchIntervieweeForm(FuzzySearchIntervieweeFormReqDTO requestParam) {
        if (StrUtil.isBlank(requestParam.getFormName())) {
            throw new ClientException("查询关键字不能为空，请检查！");
        }
        Long userId = getCurrentUserId();
        LambdaQueryWrapper<IntervieweeFormDO> queryWrapper = new LambdaQueryWrapper<IntervieweeFormDO>()
                .eq(IntervieweeFormDO::getUserId, userId)
                .like(IntervieweeFormDO::getFormName, requestParam.getFormName())
                .orderByDesc(IntervieweeFormDO::getCreateTime);
        List<IntervieweeFormDO> intervieweeFormDOS = intervieweeFormDOMapper.selectList(queryWrapper);
        if (CollectionUtil.isEmpty(intervieweeFormDOS)) {
            log.info("当前用户{}未查询到关键字为{}的简历", userId, requestParam.getFormName());
            return Collections.emptyList();
        }
        return intervieweeFormDOS.stream()
                .map(this::toRespDTO)
                .toList();
    }

    @Override
    public List<IntervieweeFormNameRespDTO> searchIntervieweeFormNameList() {
        Long userId = getCurrentUserId();
        String cacheKey = String.format(UserServiceRedisConstant.INTERVIEWEE_FORM_NAME_CACHE_KEY, userId);
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeWithScores(cacheKey, 0, 99);
        if (CollectionUtil.isEmpty(typedTuples)) {
            log.info("当前用户{}不存在简历缓存记录", userId);
            return Collections.emptyList();
        }
        return typedTuples.stream()
                .map(each -> {
                    String value = each.getValue();
                    if (StrUtil.isBlank(value)) {
                        return null;
                    }
                    int splitIndex = value.indexOf("|");
                    if (splitIndex == -1) {
                        return null;
                    }
                    Double score = each.getScore();
                    if (ObjectUtil.isNull(score)) {
                        return null;
                    }
                    return IntervieweeFormNameRespDTO.builder()
                            .id(value.substring(0, splitIndex))
                            .formName(value.substring(splitIndex + 1))
                            .createTime(new Date(score.longValue()))
                            .build();
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private void validFormParam(
            String formName,
            String candidateName,
            String jobIntention,
            List<String> professionalSkills,
            List<EducationExperience> educationExperiences,
            List<WorkExperience> workExperiences,
            List<ProjectExperience> projectExperiences) {
        if (StrUtil.isBlank(formName) || StrUtil.isBlank(candidateName) || StrUtil.isBlank(jobIntention)) {
            throw new ClientException("简历信息不完整，请检查！");
        }
        if (CollectionUtil.isEmpty(professionalSkills)
                || CollectionUtil.isEmpty(educationExperiences)
                || CollectionUtil.isEmpty(projectExperiences)) {
            throw new ClientException("简历信息不完整，请检查！");
        }
        boolean illegalSkill = professionalSkills.stream().anyMatch(StrUtil::isBlank);
        if (illegalSkill) {
            throw new ClientException("专业技能信息不完整，请检查！");
        }
        for (EducationExperience each : educationExperiences) {
            if (ObjectUtil.isNull(each)
                    || StrUtil.isBlank(each.getSchoolName())
                    || StrUtil.isBlank(each.getStartDate())
                    || StrUtil.isBlank(each.getEndDate())
                    || StrUtil.isBlank(each.getMajor())
                    || StrUtil.isBlank(each.getDegree())) {
                throw new ClientException("教育经历信息不完整，请检查！");
            }
        }
        if (CollectionUtil.isNotEmpty(workExperiences)) {
            for (WorkExperience each : workExperiences) {
                if (ObjectUtil.isNull(each)
                        || StrUtil.isBlank(each.getCompanyName())
                        || StrUtil.isBlank(each.getStartDate())
                        || StrUtil.isBlank(each.getEndDate())
                        || StrUtil.isBlank(each.getPosition())
                        || StrUtil.isBlank(each.getWorkContent())) {
                    throw new ClientException("工作经历信息不完整，请检查！");
                }
            }
        }
        for (ProjectExperience each : projectExperiences) {
            if (ObjectUtil.isNull(each)
                    || StrUtil.isBlank(each.getProjectName())
                    || StrUtil.isBlank(each.getProjectRole())
                    || StrUtil.isBlank(each.getProjectDescription())
                    || StrUtil.isBlank(each.getResponsibility())
                    || StrUtil.isBlank(each.getAchievement())) {
                throw new ClientException("项目经历信息不完整，请检查！");
            }
        }
    }

    private Long getCurrentUserId() {
        Long userId = UserContext.getId();
        if (ObjectUtil.isNull(userId)) {
            throw new ClientException("当前用户未登录或登录已失效");
        }
        return userId;
    }

    private Long parseFormId(String id) {
        if (StrUtil.isBlank(id)) {
            throw new ClientException("简历id不能为空，请检查！");
        }
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException ex) {
            throw new ClientException("简历id格式错误，请检查！");
        }
    }

    private IntervieweeFormDO getIntervieweeFormByIdAndUserId(Long id, Long userId) {
        LambdaQueryWrapper<IntervieweeFormDO> queryWrapper = new LambdaQueryWrapper<IntervieweeFormDO>()
                .eq(IntervieweeFormDO::getId, id)
                .eq(IntervieweeFormDO::getUserId, userId);
        IntervieweeFormDO intervieweeFormDO = intervieweeFormDOMapper.selectOne(queryWrapper);
        if (ObjectUtil.isNull(intervieweeFormDO)) {
            throw new ClientException("简历不存在或无访问权限！");
        }
        return intervieweeFormDO;
    }

    private void addOrRefreshFormNameCache(Long id, String formName, Long userId, Date createTime) {
        String cacheKey = String.format(UserServiceRedisConstant.INTERVIEWEE_FORM_NAME_CACHE_KEY, userId);
        String cacheValue = id + "|" + formName;
        long timeStamp = ObjectUtil.isNull(createTime) ? System.currentTimeMillis() : createTime.getTime();
        stringRedisTemplate.opsForZSet().add(cacheKey, cacheValue, timeStamp);
    }

    private void removeFormNameCache(Long id, String formName, Long userId) {
        String cacheKey = String.format(UserServiceRedisConstant.INTERVIEWEE_FORM_NAME_CACHE_KEY, userId);
        String cacheValue = id + "|" + formName;
        stringRedisTemplate.opsForZSet().remove(cacheKey, cacheValue);
    }

    private IntervieweeFormRespDTO toRespDTO(IntervieweeFormDO intervieweeFormDO) {
        return IntervieweeFormRespDTO.builder()
                .id(String.valueOf(intervieweeFormDO.getId()))
                .formName(intervieweeFormDO.getFormName())
                .userId(String.valueOf(intervieweeFormDO.getUserId()))
                .candidateName(intervieweeFormDO.getCandidateName())
                .jobIntention(intervieweeFormDO.getJobIntention())
                .professionalSkills(readStringList(intervieweeFormDO.getProfessionalSkills()))
                .educationExperiences(readEducationExperiences(intervieweeFormDO.getEducationExperiences()))
                .workExperiences(readWorkExperiences(intervieweeFormDO.getWorkExperiences()))
                .projectExperiences(readProjectExperiences(intervieweeFormDO.getProjectExperiences()))
                .createTime(intervieweeFormDO.getCreateTime())
                .build();
    }

    private List<WorkExperience> defaultIfNull(List<WorkExperience> workExperiences) {
        return CollectionUtil.isEmpty(workExperiences) ? Collections.emptyList() : workExperiences;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new ClientException("简历信息序列化失败，请检查！");
        }
    }

    private List<String> readStringList(String json) {
        return readList(json, new TypeReference<>() {
        });
    }

    private List<EducationExperience> readEducationExperiences(String json) {
        return readList(json, new TypeReference<>() {
        });
    }

    private List<WorkExperience> readWorkExperiences(String json) {
        return readList(json, new TypeReference<>() {
        });
    }

    private List<ProjectExperience> readProjectExperiences(String json) {
        return readList(json, new TypeReference<>() {
        });
    }

    private <T> List<T> readList(String json, TypeReference<List<T>> typeReference) {
        if (StrUtil.isBlank(json)) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, typeReference);
        } catch (JsonProcessingException ex) {
            throw new ClientException("简历信息解析失败，请检查！");
        }
    }
}
