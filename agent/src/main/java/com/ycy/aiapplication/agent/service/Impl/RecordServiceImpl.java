package com.ycy.aiapplication.agent.service.Impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ycy.aiapplication.agent.common.constant.AgentRedisConstant;
import com.ycy.aiapplication.agent.common.context.UserContext;
import com.ycy.aiapplication.agent.dao.entity.InterviewRecordDO;
import com.ycy.aiapplication.agent.dao.mapper.InterviewRecordDOMapper;
import com.ycy.aiapplication.agent.dto.req.FuzzySearchInterviewNameReqDTO;
import com.ycy.aiapplication.agent.dto.resp.FuzzySearchInterviewRecordRespDTO;
import com.ycy.aiapplication.agent.dto.resp.SearchInterviewNameAndIdRespDTO;
import com.ycy.aiapplication.agent.service.RecordService;
import com.ycy.aiapplication.framework.exception.ClientException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
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
        for(String nameWithId:stringSet){
           int index = nameWithId.indexOf("|");
            String id=nameWithId.substring(0,index);
            String name=nameWithId.substring(index+1);
            if(StrUtil.isNotEmpty(name)&&name.toLowerCase().contains(keyWord)){
                respList.add(FuzzySearchInterviewRecordRespDTO.builder()
                                .id(Long.parseLong(id))
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
        Set<String> records = stringRedisTemplate
                .opsForZSet()
                .reverseRange(cacheKey, 0, 99);

        if (CollectionUtil.isEmpty(records)) {
            log.info("当前用户{}记录为空",userId);
            return Collections.emptyList();
        }
        log.info("查询记录成功，立即返回结果");
        return records.stream()
                .map(record -> {

                    int index = record.indexOf("|");
                    if (index == -1) {
                        return null;
                    }

                    Long id = Long.valueOf(record.substring(0, index));
                    String name = record.substring(index + 1);

                    return SearchInterviewNameAndIdRespDTO.builder()
                            .id(id)
                            .name(name)
                            .build();

                })
                .filter(Objects::nonNull)//防止数据污染
                .toList();

    }

    @Override
    public InterviewRecordDO searchInterviewRecordById(String id) {
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
        return interviewRecordDO;

    }
}

