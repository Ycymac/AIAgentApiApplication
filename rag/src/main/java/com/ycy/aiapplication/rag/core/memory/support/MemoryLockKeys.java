package com.ycy.aiapplication.rag.core.memory.support;

/**
 * 会话记忆相关的 Redisson 锁键构造器。
 */
public final class MemoryLockKeys {

    /**
     * 摘要压缩锁前缀，同一用户会话同一时间只允许一个压缩任务。
     */
    private static final String SUMMARY_PREFIX = "ai:application:memory:summary:lock:";

    /**
     * 记忆更新锁前缀，串行化摘要和偏好的短事务写回阶段。
     */
    private static final String UPDATE_PREFIX = "ai:application:memory:update:lock:";

    private MemoryLockKeys() {
    }

    /**
     * 构建会话摘要压缩锁键。
     *
     * @param conversationId 会话 ID
     * @param userId         会话所属用户 ID
     * @return 用户与会话维度唯一的摘要锁键
     */
    public static String summary(String conversationId, String userId) {
        return SUMMARY_PREFIX + userId.trim() + ":" + conversationId.trim();
    }

    /**
     * 构建会话记忆更新锁键。
     *
     * @param conversationId 会话 ID
     * @param userId         会话所属用户 ID
     * @return 用户与会话维度唯一的更新锁键
     */
    public static String update(String conversationId, String userId) {
        return UPDATE_PREFIX + userId.trim() + ":" + conversationId.trim();
    }
}
