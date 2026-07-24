package com.ycy.aiapplication.rag.eval.controller;

import com.ycy.aiapplication.rag.core.retrieve.RetrievalEngine;
import com.ycy.aiapplication.rag.eval.intent.RagEvalIntentMode;
import com.ycy.aiapplication.rag.eval.service.RagEvalStreamService;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagEvalStreamControllerTest {

    private final RagEvalStreamService service = mock(RagEvalStreamService.class);
    private final RagEvalStreamController controller = new RagEvalStreamController(service);

    @Test
    void shouldRejectKeepRatioForPerChannelMode() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> controller.stream(
                "question", 10, false, "layered", "per_channel", 0.2D,
                null, null, null));

        assertEquals(400, exception.getStatusCode().value());
    }

    @Test
    void shouldPassUnifiedOptionsToEvalService() {
        SseEmitter emitter = new SseEmitter();
        when(service.stream(
                "question", 10, false, RagEvalIntentMode.LAYERED,
                RetrievalEngine.RerankMode.UNIFIED, 0.4D,
                "trace", "run", "query"))
                .thenReturn(emitter);

        SseEmitter result = controller.stream(
                "question", 10, false, "layered", "unified", 0.4D,
                "trace", "run", "query");

        assertSame(emitter, result);
        verify(service).stream(
                "question", 10, false, RagEvalIntentMode.LAYERED,
                RetrievalEngine.RerankMode.UNIFIED, 0.4D,
                "trace", "run", "query");
    }
}
