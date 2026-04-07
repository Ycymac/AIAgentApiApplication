package com.ycy.aiapplication.infrastructure.ai.chat.interfaces;



import com.ycy.aiapplication.framework.convention.ChatMessage;
import com.ycy.aiapplication.framework.convention.ChatRequest;

import java.util.List;

/**
 * 通用LLM访问接口
 * 用途：
 * -为业务层提供统一的大模型访问能力
 * -支持非流式/流式调用
 * 通过不同实现类适配各种平台
 * 注：
 * -默认方法 chat/streamChat 用于简单问答
 * -复杂场景（上下文、多轮对话、控制生成参数）需要使用chatRequest
 * 流式模式需正确处理cancel方法，并保证资源释放
 */
public interface LLMService {
    /**
     * 同步（非流式）调用（简单模式）
     * 说明：
     * -仅传入Prompt，不包含上下文、系统提示词、生成参数等
     * -底层自动构造ChatRequest并执行
     * -返回完成问答字符串
     * 常用场景：
     * -单轮简单提问
     * -偶发性工具调用
     * @param prompt 用户问答提示词
     * @return 模型生成的完整回答
     */
    default String chat(String prompt){
        ChatRequest req = ChatRequest.builder()
                .messages(List.of(ChatMessage.user(prompt)))
                .build();
        return chat(req);
    }

    /**
     * 同步调用（高级模式（通用版本）)
     * 说明：
     * -支持系统提示词+用户提示词、消息列表、
     * RAG上下文、生成参数（例如温度等）
     * -直接调用：适用于需要精细调控的大模型调用
     * 返回：
     * -一次性完整回答，无流式回溯
     * @param request 包含各个完整配置的聊天请求对象
     * @return 模型生成的完整回答
     */
    String chat(ChatRequest request);

    default StreamCancellationHandle streamChat(String prompt,StreamCallback callback){
        ChatRequest req = ChatRequest.builder()
                .messages(List.of(ChatMessage.user(prompt)))
                .build();
        return streamChat(req,callback);
    }

    /**
     * 流式调用（高级/通用版）
     * 说明：
     * -适用于需要上下文、多轮对话、参数控制的流式对话
     * -模型生成可能按照token/句段推送
     * -所有增量内容通过callback。onContent()回调
     *-正常调用结束通过callback。onComplete()
     * -出现异常时调用callback.onError()
     * @param request 完整请求
     * @param callback 流式回调接口
     * @return StreamCancellationHandle 用于推理
     */
    StreamCancellationHandle streamChat(ChatRequest request,StreamCallback callback);
}
