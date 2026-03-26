package com.ycy.aiapplication.user.service.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ycy.aiapplication.framework.exception.ClientException;
import com.ycy.aiapplication.user.service.common.constant.UserServiceRedisConstant;
import com.ycy.aiapplication.user.service.common.context.UserContext;
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public IntervieweeFormRespDTO addIntervieweeForm(CreateIntervieweeFormReqDTO requestParam) {
        validFormParam(requestParam.getFormName(), requestParam.getGrade(), requestParam.getMajor(),
                requestParam.getLearningDirection(), requestParam.getLearningProgress());
        Long userId = getCurrentUserId();
        IntervieweeFormDO intervieweeFormDO = IntervieweeFormDO.builder()
                .formName(requestParam.getFormName())
                .userId(userId)
                .grade(requestParam.getGrade())
                .major(requestParam.getMajor())
                .learningDirection(requestParam.getLearningDirection())
                .learningProgress(requestParam.getLearningProgress())
                .build();
        intervieweeFormDOMapper.insert(intervieweeFormDO);
        addOrRefreshFormNameCache(intervieweeFormDO.getId(), requestParam.getFormName(), userId, intervieweeFormDO.getCreateTime());
        log.info("新增简历成功，userId:{}, formId:{}", userId, intervieweeFormDO.getId());
        return toRespDTO(intervieweeFormDO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateIntervieweeForm(UpdateIntervieweeFormReqDTO requestParam) {
        if (ObjectUtil.isNull(requestParam.getId())) {
            throw new ClientException("简历id不能为空，请检查！");
        }
        validFormParam(requestParam.getFormName(), requestParam.getGrade(), requestParam.getMajor(),
                requestParam.getLearningDirection(), requestParam.getLearningProgress());
        Long userId = getCurrentUserId();
        IntervieweeFormDO oldIntervieweeFormDO = getIntervieweeFormByIdAndUserId(requestParam.getId(), userId);
        IntervieweeFormDO updateIntervieweeFormDO = IntervieweeFormDO.builder()
                .id(requestParam.getId())
                .formName(requestParam.getFormName())
                .grade(requestParam.getGrade())
                .major(requestParam.getMajor())
                .learningDirection(requestParam.getLearningDirection())
                .learningProgress(requestParam.getLearningProgress())
                .build();
        intervieweeFormDOMapper.updateById(updateIntervieweeFormDO);
        removeFormNameCache(oldIntervieweeFormDO.getId(), oldIntervieweeFormDO.getFormName(), userId);
        addOrRefreshFormNameCache(oldIntervieweeFormDO.getId(), requestParam.getFormName(), userId, oldIntervieweeFormDO.getCreateTime());
        log.info("修改简历成功，userId:{}, formId:{}", userId, requestParam.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteIntervieweeForm(Long id) {
        if (ObjectUtil.isNull(id)) {
            throw new ClientException("简历id不能为空，请检查！");
        }
        Long userId = getCurrentUserId();
        IntervieweeFormDO intervieweeFormDO = getIntervieweeFormByIdAndUserId(id, userId);
        intervieweeFormDOMapper.deleteById(id);
        removeFormNameCache(intervieweeFormDO.getId(), intervieweeFormDO.getFormName(), userId);
        log.info("删除简历成功，userId:{}, formId:{}", userId, id);
    }

    @Override
    public IntervieweeFormRespDTO searchIntervieweeFormById(Long id) {
        if (ObjectUtil.isNull(id)) {
            throw new ClientException("简历id不能为空，请检查！");
        }
        Long userId = getCurrentUserId();
        IntervieweeFormDO intervieweeFormDO = getIntervieweeFormByIdAndUserId(id, userId);
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

    private void validFormParam(String formName, String grade, String major, String learningDirection, String learningProgress) {
        if (StrUtil.isBlank(formName) || StrUtil.isBlank(grade) || StrUtil.isBlank(major)
                || StrUtil.isBlank(learningDirection) || StrUtil.isBlank(learningProgress)) {
            throw new ClientException("简历信息不完整，请检查！");
        }
    }

    private Long getCurrentUserId() {
        Long userId = UserContext.getId();
        if (ObjectUtil.isNull(userId)) {
            throw new ClientException("当前用户未登录或登录已失效");
        }
        return userId;
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
                .grade(intervieweeFormDO.getGrade())
                .major(intervieweeFormDO.getMajor())
                .learningDirection(intervieweeFormDO.getLearningDirection())
                .learningProgress(intervieweeFormDO.getLearningProgress())
                .createTime(intervieweeFormDO.getCreateTime())
                .build();
    }
}
