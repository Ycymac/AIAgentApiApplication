package com.ycy.aiapplication.rag.core.retrieve;

import com.ycy.aiapplication.infrastructure.ai.rerank.RerankClient;
import com.ycy.aiapplication.framework.convention.RetrievedChunk;
import com.ycy.aiapplication.rag.core.intent.common.SubQuestionIntent;
import com.ycy.aiapplication.rag.core.intent.common.enums.IntentKind;
import com.ycy.aiapplication.rag.core.retrieve.channel.SearchChannel;
import com.ycy.aiapplication.rag.core.retrieve.channel.SearchChannelResult;
import com.ycy.aiapplication.rag.core.retrieve.channel.SearchChannelType;
import com.ycy.aiapplication.rag.core.retrieve.channel.impls.AbstractVectorSearchChannel;
import com.ycy.aiapplication.rag.core.retrieve.common.QueryEmbeddingContext;
import com.ycy.aiapplication.rag.core.retrieve.common.RetrievalContext;
import com.ycy.aiapplication.rag.core.retrieve.common.SearchContext;
import com.ycy.aiapplication.rag.core.retrieve.common.SearchTask;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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

    @Test
    void shouldKeepDefaultPerChannelRerankBehavior() {
        RerankFixture fixture = rerankFixture();
        when(fixture.rerankClient().rerank(anyString(), anyList(), anyInt()))
                .thenAnswer(invocation -> invocation.getArgument(1));

        fixture.engine().retrieve(fixture.context());

        verify(fixture.rerankClient(), times(2)).rerank(anyString(), anyList(), anyInt());
    }

    @Test
    void shouldDeduplicateAndRerankAllChannelsOnce() {
        RerankFixture fixture = rerankFixture();
        when(fixture.rerankClient().rerank(anyString(), anyList(), anyInt()))
                .thenAnswer(invocation -> {
                    List<RetrievedChunk> candidates = invocation.getArgument(1);
                    int topN = invocation.getArgument(2);
                    return candidates.subList(0, topN);
                });

        RetrievalContext result = fixture.engine().retrieve(
                fixture.context(), RetrievalEngine.RerankMode.UNIFIED, 0.4D);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RetrievedChunk>> candidatesCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<Integer> topNCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(fixture.rerankClient(), times(1)).rerank(
                anyString(), candidatesCaptor.capture(), topNCaptor.capture());
        assertEquals(5, candidatesCaptor.getValue().size());
        assertEquals(2, topNCaptor.getValue());
        assertEquals(List.of("a", "b"), ids(result.getIntentChunks().get("intent-a")));
        assertEquals(List.of("b"), ids(result.getIntentChunks().get("intent-b")));
        assertEquals(List.of("a", "b"), ids(result.getChannelResults().get(0).getChunks()));
        assertEquals(List.of("b"), ids(result.getChannelResults().get(1).getChunks()));
    }

    @Test
    void shouldRoundUnifiedRatioUp() {
        SearchChannel channel = mock(SearchChannel.class);
        QueryEmbeddingBatcher batcher = mock(QueryEmbeddingBatcher.class);
        RerankClient rerankClient = mock(RerankClient.class);
        SearchContext context = context();
        SearchTask task = task("question", "collection", "intent");
        List<RetrievedChunk> chunks = java.util.stream.IntStream.range(0, 14)
                .mapToObj(index -> chunk("chunk-" + index, 100F - index))
                .toList();
        prepareChannel(channel, context, task, SearchChannelType.INTENT_DIRECTED, "intent", chunks, batcher);
        when(rerankClient.rerank(anyString(), anyList(), anyInt()))
                .thenAnswer(invocation -> ((List<?>) invocation.getArgument(1)).subList(0, invocation.getArgument(2)));

        new RetrievalEngine(List.of(channel), rerankClient, batcher)
                .retrieve(context, RetrievalEngine.RerankMode.UNIFIED, 0.2D);

        verify(rerankClient).rerank(anyString(), anyList(), org.mockito.ArgumentMatchers.eq(3));
    }

    @Test
    void shouldFallBackToVectorResultsWhenUnifiedRerankFails() {
        RerankFixture fixture = rerankFixture();
        when(fixture.rerankClient().rerank(anyString(), anyList(), anyInt()))
                .thenThrow(new IllegalStateException("rerank unavailable"));

        RetrievalContext result = fixture.engine().retrieve(
                fixture.context(), RetrievalEngine.RerankMode.UNIFIED, 0.2D);

        assertEquals(5, result.getIntentChunks().values().stream()
                .flatMap(List::stream)
                .map(RetrievedChunk::getId)
                .distinct()
                .count());
        verify(fixture.rerankClient(), times(1)).rerank(anyString(), anyList(), anyInt());
    }

    @Test
    void shouldSkipRerankWhenNoChannelReturnsChunks() {
        SearchChannel channel = mock(SearchChannel.class);
        QueryEmbeddingBatcher batcher = mock(QueryEmbeddingBatcher.class);
        RerankClient rerankClient = mock(RerankClient.class);
        SearchContext context = context();
        SearchTask task = task("question", "collection", "intent");
        prepareChannel(channel, context, task, SearchChannelType.INTENT_DIRECTED, "intent", List.of(), batcher);

        new RetrievalEngine(List.of(channel), rerankClient, batcher)
                .retrieve(context, RetrievalEngine.RerankMode.UNIFIED, 0.2D);

        verify(rerankClient, never()).rerank(anyString(), anyList(), anyInt());
    }

    private RerankFixture rerankFixture() {
        SearchChannel firstChannel = mock(SearchChannel.class);
        SearchChannel secondChannel = mock(SearchChannel.class);
        QueryEmbeddingBatcher batcher = mock(QueryEmbeddingBatcher.class);
        RerankClient rerankClient = mock(RerankClient.class);
        SearchContext context = context();
        prepareChannel(
                firstChannel,
                context,
                task("question-a", "collection-a", "intent-a"),
                SearchChannelType.INTENT_DIRECTED,
                "intent-a",
                List.of(chunk("a", 0.9F), chunk("b", 0.8F), chunk("c", 0.7F)),
                batcher);
        prepareChannel(
                secondChannel,
                context,
                task("question-b", "collection-b", "intent-b"),
                SearchChannelType.VECTOR_GLOBAL,
                "intent-b",
                List.of(chunk("b", 0.75F), chunk("d", 0.6F), chunk("e", 0.5F)),
                batcher);
        when(firstChannel.getPriority()).thenReturn(10);
        when(secondChannel.getPriority()).thenReturn(20);
        return new RerankFixture(
                new RetrievalEngine(List.of(firstChannel, secondChannel), rerankClient, batcher),
                rerankClient,
                context);
    }

    private void prepareChannel(SearchChannel channel,
                                SearchContext context,
                                SearchTask task,
                                SearchChannelType type,
                                String intentKey,
                                List<RetrievedChunk> chunks,
                                QueryEmbeddingBatcher batcher) {
        QueryEmbeddingContext embeddingContext = QueryEmbeddingContext.empty();
        when(channel.getPriority()).thenReturn(10);
        when(channel.isEnabled(context)).thenReturn(true);
        when(channel.plan(context)).thenReturn(List.of(task));
        when(batcher.prepare(anyList())).thenReturn(embeddingContext);
        when(channel.search(context, List.of(task), embeddingContext)).thenReturn(SearchChannelResult.builder()
                .channelType(type)
                .channelName(type.name())
                .chunks(chunks)
                .metadata(Map.of(AbstractVectorSearchChannel.METADATA_INTENT_CHUNKS, Map.of(intentKey, chunks)))
                .build());
    }

    private SearchContext context() {
        return SearchContext.builder()
                .originalQuestion("original question")
                .rewrittenQuestion("rewritten question")
                .intents(List.of(SubQuestionIntent.builder()
                        .subQuestion("question")
                        .routeKind(IntentKind.KB)
                        .nodeScores(List.of())
                        .build()))
                .build();
    }

    private SearchTask task(String question, String collection, String intentKey) {
        return new SearchTask(question, List.of(collection), 10, Map.of(collection, List.of(intentKey)));
    }

    private RetrievedChunk chunk(String id, float score) {
        return RetrievedChunk.builder().id(id).text("text-" + id).score(score).build();
    }

    private List<String> ids(List<RetrievedChunk> chunks) {
        return chunks.stream().map(RetrievedChunk::getId).toList();
    }

    private record RerankFixture(
            RetrievalEngine engine,
            RerankClient rerankClient,
            SearchContext context) {
    }
}
