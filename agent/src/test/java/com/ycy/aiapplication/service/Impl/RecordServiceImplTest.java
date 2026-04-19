package com.ycy.aiapplication.service.Impl;

import com.ycy.aiapplication.common.constant.AgentRedisConstant;
import com.ycy.aiapplication.dao.entity.InterviewRecordDO;
import com.ycy.aiapplication.dao.mapper.InterviewRecordDOMapper;
import com.ycy.aiapplication.framework.context.UserContext;
import com.ycy.aiapplication.framework.context.UserInfoDTO;
import com.ycy.aiapplication.framework.exception.ClientException;
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

class RecordServiceImplTest {

    private InterviewRecordDOMapper interviewRecordDOMapper;

    private StringRedisTemplate stringRedisTemplate;

    private ZSetOperations<String, String> zSetOperations;

    private RecordServiceImpl recordService;

    @BeforeEach
    void setUp() {
        interviewRecordDOMapper = mock(InterviewRecordDOMapper.class);
        stringRedisTemplate = mock(StringRedisTemplate.class);
        zSetOperations = mock(ZSetOperations.class);
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        recordService = new RecordServiceImpl(interviewRecordDOMapper, stringRedisTemplate);
        UserContext.setUser(UserInfoDTO.builder().id(2002L).build());
    }

    @AfterEach
    void tearDown() {
        UserContext.removeUser();
    }

    @Test
    void deleteInterviewRecordShouldRemoveAllMatchingCacheEntriesById() {
        InterviewRecordDO interviewRecordDO = InterviewRecordDO.builder()
                .id(20L)
                .userId(2002L)
                .recordName("Java闈㈣瘯")
                .build();
        when(interviewRecordDOMapper.selectOne(any())).thenReturn(interviewRecordDO);
        String cacheKey = String.format(AgentRedisConstant.INTERVIEW_RECORD_ID_WITH_NAME_CACHE_KEY, 2002L);
        Set<String> cacheValues = new LinkedHashSet<>();
        cacheValues.add("20|Java闈㈣瘯|88|1710000000000");
        cacheValues.add("20|Java闈㈣瘯-鏂?|90|1710000001000");
        cacheValues.add("21|鍏朵粬|60|1710000002000");
        when(zSetOperations.range(cacheKey, 0, -1)).thenReturn(cacheValues);

        recordService.deleteInterviewRecord("20");

        verify(interviewRecordDOMapper).deleteById(20L);
        verify(zSetOperations).remove(eq(cacheKey), eq("20|Java闈㈣瘯|88|1710000000000"), eq("20|Java闈㈣瘯-鏂?|90|1710000001000"));
    }

    @Test
    void searchInterviewRecordByIdShouldEvictStaleCacheWhenRecordMissing() {
        when(interviewRecordDOMapper.selectOne(any())).thenReturn(null);
        String cacheKey = String.format(AgentRedisConstant.INTERVIEW_RECORD_ID_WITH_NAME_CACHE_KEY, 2002L);
        when(zSetOperations.range(cacheKey, 0, -1)).thenReturn(new LinkedHashSet<>(Set.of("20|宸叉伓鍖栬褰?|88|1710000000000", "21|鍏朵粬|60|1710000002000")));

        assertThrows(ClientException.class, () -> recordService.searchInterviewRecordById("20"));

        verify(zSetOperations).remove(eq(cacheKey), eq("20|宸叉伓鍖栬褰?|88|1710000000000"));
    }
}
