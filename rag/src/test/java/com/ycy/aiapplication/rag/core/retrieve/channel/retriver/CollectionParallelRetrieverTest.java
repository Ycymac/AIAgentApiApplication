package com.ycy.aiapplication.rag.core.retrieve.channel.retriver;

import com.ycy.aiapplication.framework.convention.RetrievedChunk;
import com.ycy.aiapplication.rag.core.retrieve.common.RetrieveRequest;
import com.ycy.aiapplication.rag.core.retrieve.service.RetrieverService;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CollectionParallelRetrieverTest {

    @Test
    void shouldUsePrecomputedVectorsAndIsolateCollectionFailure() {
        RetrieverService retrieverService = mock(RetrieverService.class);
        when(retrieverService.retrieveByVector(any(float[].class), any(RetrieveRequest.class)))
                .thenAnswer(invocation -> {
                    RetrieveRequest request = invocation.getArgument(1);
                    if ("collection-b".equals(request.getCollectionName())) {
                        throw new IllegalStateException("collection-b unavailable");
                    }
                    return List.of(new RetrievedChunk("chunk-a", "content", 0.9F));
                });

        CollectionParallelRetriever retriever =
                new CollectionParallelRetriever(retrieverService, Runnable::run);
        Map<String, float[]> collectionVectors = new LinkedHashMap<>();
        collectionVectors.put("collection-a", new float[]{1F, 1F});
        collectionVectors.put("collection-b", new float[]{2F, 2F});

        CollectionParallelRetriever.ParallelRetrievalResult result =
                retriever.executeParallelRetrievalWithTargets("question", collectionVectors, 5);

        assertEquals(1, result.successCount());
        assertEquals(1, result.failureCount());
        assertEquals(1, result.allChunks().size());
        assertEquals(1, result.targetChunks().get("collection-a").size());
        assertTrue(result.targetChunks().get("collection-b").isEmpty());
        verify(retrieverService, times(2))
                .retrieveByVector(any(float[].class), any(RetrieveRequest.class));
        verify(retrieverService, never()).retrieve(any(RetrieveRequest.class));
    }
}
