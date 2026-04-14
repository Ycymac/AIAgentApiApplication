package com.ycy.aiapplication.rag.control.controller;

import com.ycy.aiapplication.framework.context.UserContext;
import com.ycy.aiapplication.framework.web.Result;
import com.ycy.aiapplication.framework.web.Results;
import com.ycy.aiapplication.rag.control.request.ConversationUpdateRequest;
import com.ycy.aiapplication.rag.control.vo.ConversationMessageVO;
import com.ycy.aiapplication.rag.control.vo.ConversationVO;
import com.ycy.aiapplication.rag.enums.ConversationMessageOrder;
import com.ycy.aiapplication.rag.service.ConversationMessageService;
import com.ycy.aiapplication.rag.service.ConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 会话控制器
 * 提供会话相关的REST API接口，包括会话列表获取、重命名、删除以及会话消息列表获取等功能
 */
@RestController
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;
    private final ConversationMessageService conversationMessageService;

    /**
     * 获取会话列表
     */
    @GetMapping("/conversations")
    public Result<List<ConversationVO>> listConversations() {
        return Results.success(conversationService.listByUserId(String.valueOf(UserContext.getId())));
    }

    /**
     * 重命名会话
     */
    @PutMapping("/conversations/{conversationId}")
    public Result<Void> rename(@PathVariable String conversationId,
                               @RequestBody ConversationUpdateRequest request) {
        conversationService.rename(conversationId, request);
        return Results.success();
    }

    /**
     * 删除会话
     */
    @DeleteMapping("/conversations/{conversationId}")
    public Result<Void> delete(@PathVariable String conversationId) {
        conversationService.delete(conversationId);
        return Results.success();
    }

    /**
     * 获取会话消息列表
     */
    @GetMapping("/conversations/{conversationId}/messages")
    public Result<List<ConversationMessageVO>> listMessages(@PathVariable String conversationId) {
        return Results.success(conversationMessageService.listMessages(conversationId, String.valueOf(UserContext.getId()), null, ConversationMessageOrder.ASC));
    }
}