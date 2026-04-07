package com.ycy.aiapplication.infrastructure.ai.chat.interfaces;

import com.ycy.aiapplication.framework.convention.ChatRequest;
import com.ycy.aiapplication.infrastructure.ai.model.ModelTarget;

/**
 * 聊天客户端接口
 * 定义了与模型对话的核心方法
 */
public interface ChatClient {

    /**
     * 获取模型提供商名称
     */
    String provider();

    /**
     * 非流式聊天方法
     * @param request 聊天请求对象，包含用户消息和历史记录
     * @return 返回完整响应文本
     */
    String chat(ChatRequest request, ModelTarget target);

    /**
     * 流式聊天方法
     * 流式方式接收模型相应你，用于实时展示场景
     * @param request 聊天请求 对象
     * @param callback 流式回调接口，用于接收响应片段
     * @return 流取消处理器，可用于正在进行的流式响应取消
     */
    StreamCancellationHandle streamChat(ChatRequest request,StreamCallback callback, ModelTarget target);


}
