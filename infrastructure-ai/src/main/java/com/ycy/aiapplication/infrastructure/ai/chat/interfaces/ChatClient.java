package com.ycy.aiapplication.infrastructure.ai.chat.interfaces;

import com.ycy.aiapplication.framework.convention.ChatRequest;
import com.ycy.aiapplication.infrastructure.ai.model.ModelTarget;

/**
 * 聊天模型执行客户端。
 * <p>
 * provider 由 ModelTarget 承载，统一客户端根据 provider 解析平台配置。
 */
public interface ChatClient {

    /**
     * 发起一次非流式聊天调用。
     */
    String chat(ChatRequest request, ModelTarget target);

    /**
     * 发起一次流式聊天调用。
     */
    StreamCancellationHandle streamChat(ChatRequest request, StreamCallback callback, ModelTarget target);
}
