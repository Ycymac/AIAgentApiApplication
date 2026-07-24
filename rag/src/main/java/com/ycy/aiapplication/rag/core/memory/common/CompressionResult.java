package com.ycy.aiapplication.rag.core.memory.common;

import java.util.List;

/**
 * 一次记忆压缩产生的摘要与规范化会话偏好。
 *
 * @param summary     压缩后的会话摘要
 * @param preferences 从历史中保留的会话级偏好
 */
public record CompressionResult(String summary, List<PreferenceItem> preferences) {
}
