package com.ycy.aiapplication.rag.core.memory.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 一条可追溯的会话级用户偏好。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PreferenceItem {

    /**
     * 偏好唯一标识，用于并发合并时去重。
     */
    private String id;

    /**
     * 首次提取该偏好的用户消息 ID。
     */
    private String sourceMessageId;

    /**
     * 提供给模型使用的规范化偏好内容。
     */
    private String content;
}
