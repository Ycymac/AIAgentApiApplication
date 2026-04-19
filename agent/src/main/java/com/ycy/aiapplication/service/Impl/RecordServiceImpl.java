package com.ycy.aiapplication.service.Impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ycy.aiapplication.common.constant.AgentRedisConstant;
import com.ycy.aiapplication.dao.entity.InterviewRecordDO;
import com.ycy.aiapplication.dao.mapper.InterviewRecordDOMapper;
import com.ycy.aiapplication.dto.req.FuzzySearchInterviewNameReqDTO;
import com.ycy.aiapplication.dto.resp.FuzzySearchInterviewRecordRespDTO;
import com.ycy.aiapplication.dto.resp.InterviewRecordRespDTO;
import com.ycy.aiapplication.dto.resp.SearchInterviewNameAndIdRespDTO;
import com.ycy.aiapplication.framework.context.UserContext;
import com.ycy.aiapplication.framework.exception.ClientException;
import com.ycy.aiapplication.service.RecordService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecordServiceImpl implements RecordService {

    private final InterviewRecordDOMapper interviewRecordDOMapper;

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public List<FuzzySearchInterviewRecordRespDTO> fuzzySearchInterviewRecordName(FuzzySearchInterviewNameReqDTO requestParam) {
        String searchInput = requestParam.getName();
        if (StrUtil.isBlank(searchInput)) {
            throw new ClientException("鏌ヨ杈撳叆涓嶈兘涓虹┖锛?");
        }
        String keyWord = searchInput.toLowerCase();
        String zSetKey = String.format(AgentRedisConstant.INTERVIEW_RECORD_ID_WITH_NAME_CACHE_KEY, UserContext.getId());
        Set<String> stringSet = stringRedisTemplate.opsForZSet().reverseRange(zSetKey, 0, 99);
        List<FuzzySearchInterviewRecordRespDTO> respList = new ArrayList<>();
        if (CollectionUtil.isEmpty(stringSet)) {
            log.info("褰撳墠鐢ㄦ埛娌℃湁{}瀵瑰簲璁板綍", searchInput);
            return Collections.emptyList();
        }
        for (String cacheValue : stringSet) {
            RecordCacheInfo recordCacheInfo = parseRecordCache(cacheValue, null);
            if (Objects.isNull(recordCacheInfo)) {
                continue;
            }
            String name = recordCacheInfo.getName();
            if (StrUtil.isNotEmpty(name) && name.toLowerCase().contains(keyWord)) {
                respList.add(FuzzySearchInterviewRecordRespDTO.builder()
                        .id(String.valueOf(recordCacheInfo.getId()))
                        .recordName(name)
                        .build());
            }
        }
        log.info("璁板綍杩斿洖鎴愬姛");
        return respList;
    }

    @Override
    public List<SearchInterviewNameAndIdRespDTO> searchInterviewNameAndId() {
        Long userId = UserContext.getId();
        String cacheKey = String.format(AgentRedisConstant.INTERVIEW_RECORD_ID_WITH_NAME_CACHE_KEY, userId);
        Set<ZSetOperations.TypedTuple<String>> records = stringRedisTemplate.opsForZSet()
                .reverseRangeWithScores(cacheKey, 0, 99);

        if (CollectionUtil.isEmpty(records)) {
            log.info("褰撳墠鐢ㄦ埛{}璁板綍涓虹┖", userId);
            return Collections.emptyList();
        }
        log.info("鏌ヨ璁板綍鎴愬姛锛岀珛鍗宠繑鍥炵粨鏋?");
        return records.stream()
                .map(record -> {
                    RecordCacheInfo recordCacheInfo = parseRecordCache(record.getValue(), record.getScore());
                    if (Objects.isNull(recordCacheInfo)) {
                        return null;
                    }
                    return SearchInterviewNameAndIdRespDTO.builder()
                            .id(String.valueOf(recordCacheInfo.getId()))
                            .name(recordCacheInfo.getName())
                            .interviewPoint(recordCacheInfo.getInterviewPoint())
                            .createTime(recordCacheInfo.getCreateTime())
                            .build();
                })
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public InterviewRecordRespDTO searchInterviewRecordById(String id) {
        Long recordId = parseRecordId(id);
        Long userId = getCurrentUserId();
        InterviewRecordDO interviewRecordDO = findInterviewRecordByIdAndUserId(recordId, userId);
        if (ObjectUtil.isNull(interviewRecordDO)) {
            removeInterviewRecordCache(recordId, userId);
            throw new ClientException("当前记录不存在");
        }
        log.info("当前记录检索成功，名称：{}，对应id：{}", interviewRecordDO.getRecordName(), interviewRecordDO.getId());
        return toInterviewRecordRespDTO(interviewRecordDO);
    }

    @Override
    public void deleteInterviewRecord(String id) {
        Long recordId = parseRecordId(id);
        Long userId = getCurrentUserId();
        InterviewRecordDO interviewRecordDO = findInterviewRecordByIdAndUserId(recordId, userId);
        if (ObjectUtil.isNull(interviewRecordDO)) {
            removeInterviewRecordCache(recordId, userId);
            throw new ClientException("当前记录不存在");
        }
        interviewRecordDOMapper.deleteById(recordId);
        removeInterviewRecordCache(recordId, userId);
        log.info("记录删除成功，userId:{}, recordId:{}", userId, recordId);
    }

    private InterviewRecordRespDTO toInterviewRecordRespDTO(InterviewRecordDO interviewRecordDO) {
        return InterviewRecordRespDTO.builder()
                .id(String.valueOf(interviewRecordDO.getId()))
                .userId(String.valueOf(interviewRecordDO.getUserId()))
                .recordName(interviewRecordDO.getRecordName())
                .interviewProcessRecord(interviewRecordDO.getInterviewProcessRecord())
                .interviewKeywords(interviewRecordDO.getInterviewKeywords())
                .adviceReportRecord(interviewRecordDO.getAdviceReportRecord())
                .summaryReportRecord(interviewRecordDO.getSummaryReportRecord())
                .interviewPoint(interviewRecordDO.getInterviewPoint())
                .accuracyScore(interviewRecordDO.getAccuracyScore())
                .completenessScore(interviewRecordDO.getCompletenessScore())
                .levelOfDetailScore(interviewRecordDO.getLevelOfDetailScore())
                .logicScore(interviewRecordDO.getLogicScore())
                .expressionAbilityScore(interviewRecordDO.getExpressionAbilityScore())
                .createTime(interviewRecordDO.getCreateTime())
                .deleted(interviewRecordDO.getDeleted())
                .build();
    }

    private Long parseRecordId(String id) {
        if (StrUtil.isBlank(id)) {
            throw new ClientException("id不能为空");
        }
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException ex) {
            throw new ClientException("id格式存在问题");
        }
    }

    private Long getCurrentUserId() {
        Long userId = UserContext.getId();
        if (ObjectUtil.isNull(userId)) {
            throw new ClientException("对应用户id为空，用户不存在");
        }
        return userId;
    }

    private InterviewRecordDO findInterviewRecordByIdAndUserId(Long recordId, Long userId) {
        LambdaQueryWrapper<InterviewRecordDO> lambdaQueryWrapper = new LambdaQueryWrapper<InterviewRecordDO>()
                .eq(InterviewRecordDO::getId, recordId)
                .eq(InterviewRecordDO::getUserId, userId);
        return interviewRecordDOMapper.selectOne(lambdaQueryWrapper);
    }

    private void removeInterviewRecordCache(Long recordId, Long userId) {
        String cacheKey = String.format(AgentRedisConstant.INTERVIEW_RECORD_ID_WITH_NAME_CACHE_KEY, userId);
        Set<String> cacheValues = stringRedisTemplate.opsForZSet().range(cacheKey, 0, -1);
        if (CollectionUtil.isEmpty(cacheValues)) {
            return;
        }
        String cachePrefix = recordId + "|";
        List<String> staleCacheValues = cacheValues.stream()
                .filter(each -> StrUtil.isNotBlank(each) && each.startsWith(cachePrefix))
                .toList();
        if (CollectionUtil.isEmpty(staleCacheValues)) {
            return;
        }
        stringRedisTemplate.opsForZSet().remove(cacheKey, staleCacheValues.toArray(new String[0]));
    }

    private RecordCacheInfo parseRecordCache(String cacheValue, Double score) {
        if (StrUtil.isBlank(cacheValue)) {
            return null;
        }
        String[] cacheInfoArray = cacheValue.split("\\|");
        if (cacheInfoArray.length < 2) {
            return null;
        }
        try {
            Long id = Long.parseLong(cacheInfoArray[0]);
            String name = cacheInfoArray[1];
            Integer interviewPoint = cacheInfoArray.length >= 3 ? Integer.parseInt(cacheInfoArray[2]) : null;
            Date createTime;
            if (cacheInfoArray.length >= 4) {
                createTime = new Date(Long.parseLong(cacheInfoArray[3]));
            } else if (Objects.nonNull(score)) {
                createTime = new Date(score.longValue());
            } else {
                createTime = null;
            }
            return RecordCacheInfo.builder()
                    .id(id)
                    .name(name)
                    .interviewPoint(interviewPoint)
                    .createTime(createTime)
                    .build();
        } catch (NumberFormatException ex) {
            log.warn("redis格式问题cacheValue:{}", cacheValue);
            return null;
        }
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    private static class RecordCacheInfo {
        private Long id;

        private String name;

        private Integer interviewPoint;

        private Date createTime;
    }
}
