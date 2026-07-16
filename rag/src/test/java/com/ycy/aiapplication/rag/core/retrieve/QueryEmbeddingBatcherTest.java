package com.ycy.aiapplication.rag.core.retrieve;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.ycy.aiapplication.infrastructure.ai.embedding.EmbeddingService;
import com.ycy.aiapplication.knowledge.dao.entity.KnowledgeBaseDO;
import com.ycy.aiapplication.knowledge.dao.mapper.KnowledgeBaseMapper;
import com.ycy.aiapplication.rag.core.retrieve.common.QueryEmbeddingContext;
import com.ycy.aiapplication.rag.core.retrieve.common.SearchTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueryEmbeddingBatcherTest {

    @Mock
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Mock
    private EmbeddingService embeddingService;

    private QueryEmbeddingBatcher batcher;

    @BeforeEach
    void setUp() {
        batcher = new QueryEmbeddingBatcher(knowledgeBaseMapper, embeddingService, Runnable::run);
    }

    @Test
    void shouldBatchDistinctQuestionsOncePerModelAcrossTasks() {
        when(knowledgeBaseMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                knowledgeBase("collection-a", "model-1"),
                knowledgeBase("collection-b", "model-1"),
                knowledgeBase("collection-c", "model-2")));
        when(embeddingService.embedBatch(List.of("question-1", "question-2"), "model-1"))
                .thenReturn(List.of(List.of(1F, 1F), List.of(1F, 2F)));
        when(embeddingService.embedBatch(List.of("question-2"), "model-2"))
                .thenReturn(List.of(List.of(2F, 2F)));

        QueryEmbeddingContext context = batcher.prepare(List.of(
                task("question-1", "collection-a", "collection-b"),
                task("question-2", "collection-b", "collection-c")));

        assertArrayEquals(new float[]{1F, 1F}, context.resolveVector("collection-a", "question-1"));
        assertArrayEquals(new float[]{1F, 1F}, context.resolveVector("collection-b", "question-1"));
        assertArrayEquals(new float[]{1F, 2F}, context.resolveVector("collection-b", "question-2"));
        assertArrayEquals(new float[]{2F, 2F}, context.resolveVector("collection-c", "question-2"));
        verify(knowledgeBaseMapper, times(1)).selectList(any(Wrapper.class));
        verify(embeddingService, times(1))
                .embedBatch(List.of("question-1", "question-2"), "model-1");
        verify(embeddingService, times(1)).embedBatch(List.of("question-2"), "model-2");
    }

    @Test
    void shouldIsolateFailedModelAndSkipCollectionWithoutMapping() {
        when(knowledgeBaseMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                knowledgeBase("collection-a", "model-1"),
                knowledgeBase("collection-b", "model-2")));
        when(embeddingService.embedBatch(List.of("question"), "model-1"))
                .thenThrow(new IllegalStateException("model-1 unavailable"));
        when(embeddingService.embedBatch(List.of("question"), "model-2"))
                .thenReturn(List.of(List.of(2F, 2F)));

        QueryEmbeddingContext context = batcher.prepare(List.of(
                task("question", "collection-a", "collection-b", "collection-missing")));

        assertNull(context.resolveVector("collection-a", "question"));
        assertArrayEquals(new float[]{2F, 2F}, context.resolveVector("collection-b", "question"));
        assertNull(context.resolveVector("collection-missing", "question"));
    }

    @Test
    void shouldOnlyCallModelRequiredByCurrentTasks() {
        when(knowledgeBaseMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                knowledgeBase("collection-a", "model-1")));
        when(embeddingService.embedBatch(List.of("question"), "model-1"))
                .thenReturn(List.of(List.of(1F, 1F)));

        QueryEmbeddingContext context = batcher.prepare(List.of(task("question", "collection-a")));

        assertArrayEquals(new float[]{1F, 1F}, context.resolveVector("collection-a", "question"));
        verify(embeddingService, times(1)).embedBatch(List.of("question"), "model-1");
        verify(embeddingService, never()).embedBatch(anyList(), eq("model-2"));
    }

    private SearchTask task(String question, String... collections) {
        Map<String, List<String>> intentKeys = new LinkedHashMap<>();
        for (String collection : collections) {
            intentKeys.put(collection, List.of("intent"));
        }
        return new SearchTask(question, List.of(collections), 5, intentKeys);
    }

    private KnowledgeBaseDO knowledgeBase(String collectionName, String modelId) {
        return KnowledgeBaseDO.builder()
                .collectionName(collectionName)
                .embeddingModel(modelId)
                .deleted(0)
                .build();
    }
}
