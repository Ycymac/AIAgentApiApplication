package com.ycy.aiapplication.rag.eval.stream;

import cn.hutool.core.util.StrUtil;
import com.ycy.aiapplication.framework.web.SseEmitterSender;
import com.ycy.aiapplication.infrastructure.ai.chat.interfaces.StreamCallback;
import com.ycy.aiapplication.rag.eval.dto.RagEvalStreamEvents;
import com.ycy.aiapplication.rag.eval.trace.RagEvalTraceContext;
import com.ycy.aiapplication.rag.eval.trace.RagEvalTraceWriter;
import com.ycy.aiapplication.rag.stream.common.MessageDelta;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;

public class RagEvalStreamCallback implements StreamCallback {

    private final SseEmitterSender sender;
    private final RagEvalTraceWriter traceWriter;
    private final String traceId;
    private final String runId;
    private final String queryId;
    private final Map<String, Long> timings;
    private final LongSupplier elapsedMsSupplier;
    private final StringBuilder answer = new StringBuilder();

    public RagEvalStreamCallback(
            SseEmitterSender sender,
            RagEvalTraceWriter traceWriter,
            String traceId,
            String runId,
            String queryId,
            Map<String, Long> timings,
            LongSupplier elapsedMsSupplier) {
        this.sender = sender;
        this.traceWriter = traceWriter;
        this.traceId = traceId;
        this.runId = runId;
        this.queryId = queryId;
        this.timings = timings;
        this.elapsedMsSupplier = elapsedMsSupplier;
    }

    @Override
    public void onContent(String content) {
        if (StrUtil.isBlank(content)) {
            return;
        }
        answer.append(content);
        sender.sendEvent("message", new MessageDelta("response", content));
    }

    @Override
    public void onThinking(String content) {
        if (StrUtil.isBlank(content)) {
            return;
        }
        sender.sendEvent("message", new MessageDelta("think", content));
    }

    @Override
    public void onComplete() {
        finish("success", null);
    }

    @Override
    public void onError(Throwable error) {
        finish("error", error == null ? null : error.toString());
    }

    private void finish(String status, String error) {
        Map<String, Long> finalTimings = new LinkedHashMap<>(timings);
        finalTimings.put("answerCompleteMs", elapsedMsSupplier.getAsLong());
        try (RagEvalTraceContext.Scope ignored = RagEvalTraceContext.open(traceId, runId, queryId)) {
            traceWriter.write("eval.stream.completed", Map.of(
                    "status", status,
                    "responseChars", answer.length(),
                    "timings", finalTimings));
        }
        sender.sendEvent("finish", new RagEvalStreamEvents.FinishEvent(
                status,
                error,
                answer.length(),
                finalTimings));
        sender.sendEvent("done", "[DONE]");
        sender.complete();
    }
}
