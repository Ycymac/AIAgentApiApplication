package com.ycy.aiapplication.user.service.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ycy.aiapplication.framework.context.UserContext;
import com.ycy.aiapplication.framework.context.UserInfoDTO;
import com.ycy.aiapplication.framework.exception.ClientException;
import com.ycy.aiapplication.user.service.common.constant.UserServiceRedisConstant;
import com.ycy.aiapplication.user.service.dao.entity.IntervieweeFormDO;
import com.ycy.aiapplication.user.service.dao.mapper.IntervieweeFormDOMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IntervieweeFormServiceImplTest {

    private IntervieweeFormDOMapper intervieweeFormDOMapper;

    private StringRedisTemplate stringRedisTemplate;

    private ZSetOperations<String, String> zSetOperations;

    private IntervieweeFormServiceImpl intervieweeFormService;

    @BeforeEach
    void setUp() {
        intervieweeFormDOMapper = mock(IntervieweeFormDOMapper.class);
        stringRedisTemplate = mock(StringRedisTemplate.class);
        zSetOperations = mock(ZSetOperations.class);
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        intervieweeFormService = new IntervieweeFormServiceImpl(intervieweeFormDOMapper, stringRedisTemplate, new ObjectMapper());
        UserContext.setUser(UserInfoDTO.builder().id(1001L).build());
    }

    @AfterEach
    void tearDown() {
        UserContext.removeUser();
    }

    @Test
    void deleteIntervieweeFormShouldRemoveAllMatchingCacheEntriesById() {
        IntervieweeFormDO intervieweeFormDO = IntervieweeFormDO.builder()
                .id(10L)
                .userId(1001L)
                .formName("Java绠€鍘?")
                .build();
        when(intervieweeFormDOMapper.selectOne(any())).thenReturn(intervieweeFormDO);
        String cacheKey = String.format(UserServiceRedisConstant.INTERVIEWEE_FORM_NAME_CACHE_KEY, 1001L);
        Set<String> cacheValues = new LinkedHashSet<>();
        cacheValues.add("10|Java绠€鍘?");
        cacheValues.add("10|Java鍚庣绠€鍘?");
        cacheValues.add("11|鍏朵粬绠€鍘?");
        when(zSetOperations.range(cacheKey, 0, -1)).thenReturn(cacheValues);

        intervieweeFormService.deleteIntervieweeForm("10");

        verify(intervieweeFormDOMapper).deleteById(10L);
        verify(zSetOperations).remove(eq(cacheKey), eq("10|Java绠€鍘?"), eq("10|Java鍚庣绠€鍘?"));
    }

    @Test
    void searchIntervieweeFormByIdShouldEvictStaleCacheWhenRecordMissing() {
        when(intervieweeFormDOMapper.selectOne(any())).thenReturn(null);
        String cacheKey = String.format(UserServiceRedisConstant.INTERVIEWEE_FORM_NAME_CACHE_KEY, 1001L);
        when(zSetOperations.range(cacheKey, 0, -1)).thenReturn(new LinkedHashSet<>(Set.of("10|宸叉伓鍖栫紦瀛?", "12|鍏朵粬")));

        assertThrows(ClientException.class, () -> intervieweeFormService.searchIntervieweeFormById("10"));

        verify(zSetOperations).remove(eq(cacheKey), eq("10|宸叉伓鍖栫紦瀛?"));
    }
}
