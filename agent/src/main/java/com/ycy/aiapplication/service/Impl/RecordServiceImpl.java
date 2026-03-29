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
import com.ycy.aiapplication.service.RecordService;
import com.ycy.aiapplication.framework.exception.ClientException;
import com.ycy.aiapplication.user.service.common.context.UserContext;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.*;


@Slf4j
@Service
@RequiredArgsConstructor
public class RecordServiceImpl implements RecordService {

    private final InterviewRecordDOMapper interviewRecordDOMapper;

    private final StringRedisTemplate stringRedisTemplate;



    /**
     * 模糊查询
     * @param requestParam 搜索框当中的输入
     * @return 所有符合名称的记录的id和名字，之后点击对应的记录我们再去查询，提升性能,减轻前端压力
     * 这里查询缓存，性能更好
     */
    @Override
    public List<FuzzySearchInterviewRecordRespDTO> fuzzySearchInterviewRecordName( FuzzySearchInterviewNameReqDTO requestParam) {
        String searchInput=requestParam.getName();
        if (StrUtil.isBlank(searchInput)) {
            throw new ClientException("查询输入不能为空！");
        }
        String keyWord = searchInput.toLowerCase();
        //查询对应的ZSet
        String zSetKey = String.format(AgentRedisConstant.INTERVIEW_RECORD_ID_WITH_NAME_CACHE_KEY, UserContext.getId());
        Set<String> stringSet = stringRedisTemplate.opsForZSet().reverseRange(zSetKey, 0, 99);
        ArrayList<FuzzySearchInterviewRecordRespDTO> respList = new ArrayList<>();
        if(CollectionUtil.isEmpty(stringSet)){
            log.info("当前用户没有{}对应记录",searchInput);
            return Collections.emptyList();
        }
        for(String cacheValue:stringSet){
            RecordCacheInfo recordCacheInfo = parseRecordCache(cacheValue, null);
            if(Objects.isNull(recordCacheInfo)){
                continue;
            }
            String name = recordCacheInfo.getName();
            if(StrUtil.isNotEmpty(name)&&name.toLowerCase().contains(keyWord)){
                respList.add(FuzzySearchInterviewRecordRespDTO.builder()
                                .id(String.valueOf(recordCacheInfo.getId()))
                                .recordName(name)
                                .build());


            }

        }
        log.info("记录返回成功！");
        return respList;
    }


    /**
     * 左侧显示用户前100条记录在左侧已经很多了，后续改造为分页查询
     */
    @Override
    public List<SearchInterviewNameAndIdRespDTO> searchInterviewNameAndId() {
        Long userId= UserContext.getId();
        String cacheKey = String.format(AgentRedisConstant.INTERVIEW_RECORD_ID_WITH_NAME_CACHE_KEY, userId);
        Set<ZSetOperations.TypedTuple<String>> records = stringRedisTemplate
                .opsForZSet()
                .reverseRangeWithScores(cacheKey, 0, 99);

        if (CollectionUtil.isEmpty(records)) {
            log.info("当前用户{}记录为空",userId);
            return Collections.emptyList();
        }
        log.info("查询记录成功，立即返回结果");
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
                .filter(Objects::nonNull)//防止数据污染
                .toList();

    }

    @Override
    public InterviewRecordRespDTO searchInterviewRecordById(String id) {
        if(StrUtil.isBlank(id)){
            throw new ClientException("查询id不能为空！请检查");
        }
        long recordId ;
        try{
            recordId=Long.parseLong(id);
        }catch (NumberFormatException e){
            throw new ClientException("记录id格式错误,必须为数字id");
        }
        LambdaQueryWrapper<InterviewRecordDO> lambdaQueryWrapper = new LambdaQueryWrapper<InterviewRecordDO>()
                .eq(InterviewRecordDO::getId, recordId)
                .eq(InterviewRecordDO::getUserId, UserContext.getId());//防止查询到不属于当前用户的记录导致数据泄露

        InterviewRecordDO interviewRecordDO = interviewRecordDOMapper.selectOne(lambdaQueryWrapper);
        if(ObjectUtil.isNull(interviewRecordDO)){
            throw new ClientException("记录已经不存在/无访问权限");
        }
        log.info("查询到对应记录，记录名称：{}，记录id：{}",interviewRecordDO.getRecordName(),interviewRecordDO.getId());
        return toInterviewRecordRespDTO(interviewRecordDO);

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
            log.warn("redis缓存记录解析失败，cacheValue:{}", cacheValue);
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

