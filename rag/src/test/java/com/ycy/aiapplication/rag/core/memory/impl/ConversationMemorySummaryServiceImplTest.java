package com.ycy.aiapplication.rag.core.memory.impl;

import com.ycy.aiapplication.framework.convention.ChatMessage;
import com.ycy.aiapplication.framework.convention.ChatRequest;
import com.ycy.aiapplication.infrastructure.ai.chat.interfaces.LLMService;
import com.ycy.aiapplication.rag.config.MemoryProperties;
import com.ycy.aiapplication.rag.core.memory.common.PreferenceItem;
import com.ycy.aiapplication.rag.core.memory.support.ConversationPreferenceCodec;
import com.ycy.aiapplication.rag.core.prompt.PromptTemplateLoader;
import com.ycy.aiapplication.rag.dao.entity.ConversationDO;
import com.ycy.aiapplication.rag.dao.entity.ConversationMessageDO;
import com.ycy.aiapplication.rag.service.ConversationComplexQueryService;
import com.ycy.aiapplication.rag.service.ConversationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationMemorySummaryServiceImplTest {

    @Test
    void shouldMergePreferenceAddedWhileSummaryModelWasRunning() throws Exception {
        ConversationComplexQueryService queries = mock(ConversationComplexQueryService.class);
        ConversationService conversations = mock(ConversationService.class);
        LLMService llm = mock(LLMService.class);
        PromptTemplateLoader prompts = mock(PromptTemplateLoader.class);
        RedissonClient redisson = mock(RedissonClient.class);
        RLock summaryLock = mock(RLock.class);
        RLock updateLock = mock(RLock.class);
        when(redisson.getLock(anyString())).thenReturn(summaryLock, updateLock);
        when(summaryLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(updateLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(summaryLock.isHeldByCurrentThread()).thenReturn(true);
        when(updateLock.isHeldByCurrentThread()).thenReturn(true);

        PreferenceItem original = new PreferenceItem("100-1", "100", "详细回答");
        PreferenceItem added = new PreferenceItem("200-1", "200", "使用中文");
        ConversationDO snapshot = ConversationDO.builder()
                .conversationId("conversation")
                .userId("user")
                .conversationPreferences(ConversationPreferenceCodec.toJson(List.of(original)))
                .preferenceVersion(0L)
                .build();
        ConversationDO latest = ConversationDO.builder()
                .conversationId("conversation")
                .userId("user")
                .conversationPreferences(ConversationPreferenceCodec.toJson(List.of(original, added)))
                .preferenceVersion(1L)
                .build();
        when(queries.findConversation("conversation", "user")).thenReturn(snapshot, latest);
        when(queries.listMessagesAfterThroughId("conversation", "user", null, "target"))
                .thenReturn(completedTurns(9));
        when(prompts.render(anyString(), anyMap())).thenReturn("summary prompt");
        when(llm.chat(any(ChatRequest.class))).thenReturn("""
                {
                  "summary": "用户正在讨论会话记忆改造。",
                  "preferences": [
                    {"id":"100-1","sourceMessageId":"100","content":"回答时提供必要细节"}
                  ]
                }
                """);
        when(conversations.publishMemory(
                anyString(), anyString(), any(), anyLong(), anyString(), anyString(), anyString()
        )).thenReturn(1);

        MemoryProperties properties = new MemoryProperties();
        properties.setSummaryStartTurns(9);
        Executor directExecutor = Runnable::run;
        ConversationMemorySummaryServiceImpl service = new ConversationMemorySummaryServiceImpl(
                queries,
                conversations,
                properties,
                llm,
                prompts,
                redisson,
                directExecutor
        );

        service.compressIfNeeded(
                "conversation",
                "user",
                ChatMessage.assistant("answer"),
                "target"
        );

        ArgumentCaptor<String> preferencesJson = ArgumentCaptor.forClass(String.class);
        verify(conversations).publishMemory(
                org.mockito.ArgumentMatchers.eq("conversation"),
                org.mockito.ArgumentMatchers.eq("user"),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq(1L),
                anyString(),
                preferencesJson.capture(),
                org.mockito.ArgumentMatchers.eq("target")
        );
        assertEquals(
                List.of(
                        new PreferenceItem("100-1", "100", "回答时提供必要细节"),
                        added
                ),
                ConversationPreferenceCodec.parse(preferencesJson.getValue())
        );
    }

    private List<ConversationMessageDO> completedTurns(int count) {
        List<ConversationMessageDO> messages = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            messages.add(ConversationMessageDO.builder()
                    .id(String.valueOf(index * 2 + 1))
                    .role("user")
                    .content("question " + index)
                    .build());
            messages.add(ConversationMessageDO.builder()
                    .id(String.valueOf(index * 2 + 2))
                    .role("assistant")
                    .content("answer " + index)
                    .build());
        }
        return messages;
    }
}
