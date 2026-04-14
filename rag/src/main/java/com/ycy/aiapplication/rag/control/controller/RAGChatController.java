package com.ycy.aiapplication.rag.control.controller;

import com.ycy.aiapplication.framework.idempotent.annotations.IdempotentSubmit;
import com.ycy.aiapplication.framework.web.Result;
import com.ycy.aiapplication.framework.web.Results;
import com.ycy.aiapplication.rag.service.RAGChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * RAG 对话控制器
 * 提供流式问答与任务取消接口
 */
@RestController
@RequiredArgsConstructor
public class RAGChatController {

    private final RAGChatService ragChatService;

    /**
     * 发起 SSE 流式对话
     */
    @IdempotentSubmit(
            key = "T(com.ycy.aiapplication.rag.framework.context.UserContext).getId()",
            message = "当前会话处理中，请稍后再发起新的对话"
    )
    @GetMapping(value = "/rag/v3/chat", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter chat(@RequestParam String question,
                           @RequestParam(required = false) String conversationId,
                           @RequestParam(required = false, defaultValue = "false") Boolean deepThinking) {
        SseEmitter emitter = new SseEmitter(0L);
        ragChatService.streamChat(question, conversationId, deepThinking, emitter);
        return emitter;
    }

    /**
     * 停止指定任务
     */
    @IdempotentSubmit
    @PostMapping(value = "/rag/v3/stop")
    public Result<Void> stop(@RequestParam String taskId) {
        ragChatService.stopTask(taskId);
        return Results.success();
    }
}