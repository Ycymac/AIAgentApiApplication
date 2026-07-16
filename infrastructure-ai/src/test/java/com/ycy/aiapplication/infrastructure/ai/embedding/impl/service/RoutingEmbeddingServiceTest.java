package com.ycy.aiapplication.infrastructure.ai.embedding.impl.service;

import com.ycy.aiapplication.infrastructure.ai.config.AIModelProperties;
import com.ycy.aiapplication.infrastructure.ai.embedding.EmbeddingClient;
import com.ycy.aiapplication.infrastructure.ai.embedding.EmbeddingModelRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoutingEmbeddingServiceTest {

    private final EmbeddingClient primaryClient = mock(EmbeddingClient.class);
    private final EmbeddingClient fallbackClient = mock(EmbeddingClient.class);
    private RoutingEmbeddingService service;

    @BeforeEach
    void setUp() {
        AIModelProperties properties = new AIModelProperties();
        properties.getEmbedding().setDefaultProvider("primary");
        properties.getEmbedding().setFallbackEnabled(true);
        properties.getEmbedding().setFallbackOrder(List.of("fallback"));
        properties.getEmbedding().setBatchSize(16);
        properties.getEmbedding().setDimension(2);
        LinkedHashMap<String, String> models = new LinkedHashMap<>();
        models.put("primary", "model-primary");
        models.put("fallback", "model-fallback");
        properties.getEmbedding().setModels(models);

        EmbeddingModelRegistry registry = new EmbeddingModelRegistry(properties);
        when(primaryClient.provider()).thenReturn("primary");
        when(fallbackClient.provider()).thenReturn("fallback");
        when(primaryClient.supports("primary", "model-primary")).thenReturn(true);
        when(fallbackClient.supports("fallback", "model-fallback")).thenReturn(true);
        service = new RoutingEmbeddingService(List.of(primaryClient, fallbackClient), properties, registry);
    }

    @Test
    void explicitModelShouldNotUseFallback() {
        List<String> texts = List.of("question");
        when(primaryClient.embedBatch(eq(texts), eq("model-primary"), eq(2), anyInt()))
                .thenThrow(new IllegalStateException("primary unavailable"));

        assertThrows(IllegalStateException.class,
                () -> service.embedBatch(texts, "model-primary"));

        verify(fallbackClient, never())
                .embedBatch(eq(texts), eq("model-fallback"), eq(2), anyInt());
    }

    @Test
    void defaultRouteShouldKeepFallbackBehavior() {
        List<String> texts = List.of("question");
        List<List<Float>> fallbackResult = List.of(List.of(1F, 2F));
        when(primaryClient.embedBatch(eq(texts), eq("model-primary"), eq(2), anyInt()))
                .thenThrow(new IllegalStateException("primary unavailable"));
        when(fallbackClient.embedBatch(eq(texts), eq("model-fallback"), eq(2), anyInt()))
                .thenReturn(fallbackResult);

        assertEquals(fallbackResult, service.embedBatch(texts));
    }

    @Test
    void unregisteredExplicitModelShouldFailBeforeCallingClient() {
        assertThrows(IllegalArgumentException.class,
                () -> service.embedBatch(List.of("question"), "model-unknown"));

        verify(primaryClient, never())
                .embedBatch(eq(List.of("question")), eq("model-unknown"), eq(2), anyInt());
        verify(fallbackClient, never())
                .embedBatch(eq(List.of("question")), eq("model-unknown"), eq(2), anyInt());
    }
}
