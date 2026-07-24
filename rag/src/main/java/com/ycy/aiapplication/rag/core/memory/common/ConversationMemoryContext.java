package com.ycy.aiapplication.rag.core.memory.common;

import com.ycy.aiapplication.framework.convention.ChatMessage;
import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * 当前请求使用的会话记忆快照。
 * <p>
 * 同时保存回答所需的历史和后续扩展、偏好合并所需的水位信息，
 * 避免调用方分别读取摘要、偏好和当前消息而产生版本不一致。
 */
@Value
@Builder
public class ConversationMemoryContext {

    /**
     * 当前会话摘要。
     */
    String summary;

    /**
     * 已生效的会话级偏好。
     */
    List<PreferenceItem> preferences;

    /**
     * 摘要覆盖到的最后一条消息 ID。
     */
    String lastMessageId;

    /**
     * 偏好快照版本，用于并发更新时进行比较。
     */
    long preferenceVersion;

    /**
     * 已组装为模型消息的摘要、偏好及最近对话。
     */
    List<ChatMessage> history;

    /**
     * 本次刚写入的用户消息 ID，作为新增偏好的来源标识。
     */
    String currentMessageId;
}
