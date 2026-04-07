package com.ycy.aiapplication.infrastructure.ai.chat.interfaces;

/**
 * 流式聊天回调接口。
 * 用于统一接收模型增量输出、thinking 过程、完成事件和异常事件。
 */
public interface StreamCallback {
    /**
     * 接收一段正文增量内容。
     */
    void onContent(String content);

    /**
     * 接收一段 thinking / reasoning 增量内容。
     */
    default void onThinking(String content) {
    }

    /**
     * 通知当前流式响应已经正常结束。
     */
    void onComplete();

    /**
     * 通知当前流式响应出现异常。
     */
    void onError(Throwable error);
}
