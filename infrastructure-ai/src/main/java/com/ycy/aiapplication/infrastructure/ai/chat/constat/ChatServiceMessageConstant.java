package com.ycy.aiapplication.infrastructure.ai.chat.constat;

/**
 * Chat 服务消息常量。
 * 收敛流式聊天路由中的统一提示文案，避免在服务实现中散落硬编码字符串。
 */
public final class ChatServiceMessageConstant {
    public static final String STREAM_NO_PROVIDER_MESSAGE = "No available chat model";
    public static final String STREAM_START_FAILED_MESSAGE = "Stream start failed";
    public static final String STREAM_TIMEOUT_MESSAGE = "Stream first packet timeout";
    public static final String STREAM_NO_CONTENT_MESSAGE = "Stream completed without content";
    public static final String STREAM_INTERRUPTED_MESSAGE = "Stream request interrupted";
    public static final String STREAM_ALL_FAILED_MESSAGE = "All chat model candidates failed";
}
