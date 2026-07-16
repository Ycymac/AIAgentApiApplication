package com.ycy.aiapplication.rag.core.retrieve;

import com.ycy.aiapplication.infrastructure.ai.rerank.RerankClient;
import com.ycy.aiapplication.rag.core.intent.common.SubQuestionIntent;
import com.ycy.aiapplication.rag.core.intent.common.enums.IntentKind;
import com.ycy.aiapplication.rag.core.retrieve.channel.SearchChannel;
import com.ycy.aiapplication.rag.core.retrieve.channel.SearchChannelResult;
import com.ycy.aiapplication.rag.core.retrieve.common.QueryEmbeddingContext;
import com.ycy.aiapplication.rag.core.retrieve.common.SearchContext;
import com.ycy.aiapplication.rag.core.retrieve.common.SearchTask;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RetrievalEngineTest {

    @Test
    void shouldPrepareEmbeddingsOnceForTasksFromAllEnabledChannels() {
        SearchChannel firstChannel = mock(SearchChannel.class);
        SearchChannel secondChannel = mock(SearchChannel.class);
        QueryEmbeddingBatcher batcher = mock(QueryEmbeddingBatcher.class);
        RerankClient rerankClient = mock(RerankClient.class);
        when(firstChannel.getPriority()).thenReturn(10);
        when(secondChannel.getPriority()).thenReturn(20);

        SearchContext context = SearchContext.builder()
                .originalQuestion("question")
                .intents(List.of(SubQuestionIntent.builder()
                        .subQuestion("question")
                        .routeKind(IntentKind.KB)
                        .nodeScores(List.of())
                        .build()))
                .build();
        SearchTask firstTask = new SearchTask(
                "question-1", List.of("collection-a"), 5, Map.of("collection-a", List.of("intent-a")));
        SearchTask secondTask = new SearchTask(
                "question-2", List.of("collection-b"), 5, Map.of("collection-b", List.of("intent-b")));
        when(firstChannel.isEnabled(context)).thenReturn(true);
        when(secondChannel.isEnabled(context)).thenReturn(true);
        when(firstChannel.plan(context)).thenReturn(List.of(firstTask));
        when(secondChannel.plan(context)).thenReturn(List.of(secondTask));

        QueryEmbeddingContext embeddingContext = QueryEmbeddingContext.empty();
        when(batcher.prepare(anyList())).thenReturn(embeddingContext);
        SearchChannelResult emptyResult = SearchChannelResult.builder().chunks(List.of()).build();
        when(firstChannel.search(context, List.of(firstTask), embeddingContext)).thenReturn(emptyResult);
        when(secondChannel.search(context, List.of(secondTask), embeddingContext)).thenReturn(emptyResult);

        RetrievalEngine engine = new RetrievalEngine(
                List.of(secondChannel, firstChannel), rerankClient, batcher);
        engine.retrieve(context);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SearchTask>> tasksCaptor = ArgumentCaptor.forClass(List.class);
        verify(batcher, times(1)).prepare(tasksCaptor.capture());
        assertEquals(List.of(firstTask, secondTask), tasksCaptor.getValue());
        verify(firstChannel, times(1)).search(context, List.of(firstTask), embeddingContext);
        verify(secondChannel, times(1)).search(context, List.of(secondTask), embeddingContext);
    }
}
