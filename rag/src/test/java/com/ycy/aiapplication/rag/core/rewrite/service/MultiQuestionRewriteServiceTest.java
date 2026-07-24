package com.ycy.aiapplication.rag.core.rewrite.service;

import com.ycy.aiapplication.framework.convention.ChatRequest;
import com.ycy.aiapplication.infrastructure.ai.chat.interfaces.LLMService;
import com.ycy.aiapplication.rag.config.RAGReWriteProperties;
import com.ycy.aiapplication.rag.core.prompt.PromptTemplateLoader;
import com.ycy.aiapplication.rag.core.rewrite.common.RewriteResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MultiQuestionRewriteServiceTest {

    @Test
    void shouldExtractCurrentConstraintsAndPersistentPreferencesInRewriteCall() {
        LLMService llm = mock(LLMService.class);
        QueryTermMappingService mapping = mock(QueryTermMappingService.class);
        PromptTemplateLoader prompts = mock(PromptTemplateLoader.class);
        RAGReWriteProperties properties = new RAGReWriteProperties();
        properties.setQueryRewriteEnabled(true);
        properties.setQueryRewriteMaxHistoryMessages(4);
        properties.setQueryRewriteMaxHistoryChars(500);
        when(mapping.normalize(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(prompts.load(anyString())).thenReturn("rewrite prompt");
        when(llm.chat(any(ChatRequest.class))).thenReturn("""
                {
                  "status": "SUCCESS",
                  "rewrite": "RAG记忆架构",
                  "should_split": false,
                  "sub_questions": ["RAG记忆架构"],
                  "current_constraints": ["本次分点回答"],
                  "persistent_preferences": ["后续技术问题说明底层原理"]
                }
                """);

        MultiQuestionRewriteService service = new MultiQuestionRewriteService(
                llm,
                properties,
                mapping,
                prompts
        );

        RewriteResult result = service.rewriteWithSplit(
                "本次分点回答，后续技术问题说明底层原理",
                List.of(),
                false
        );

        assertFalse(result.needsMoreContext());
        assertEquals(List.of("本次分点回答"), result.currentConstraints());
        assertEquals(List.of("后续技术问题说明底层原理"), result.persistentPreferences());
    }
}
