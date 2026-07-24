package com.ycy.aiapplication.rag.core.rewrite.common;

import java.util.List;

/**
 * 查询改写阶段的结构化输出。
 *
 * @param status                改写状态，决定是否需要加载更完整历史
 * @param rewrittenQuestion     面向意图识别和检索的改写问题
 * @param subQuestions          拆分后的子问题列表
 * @param currentConstraints    仅对本轮回答生效的约束
 * @param persistentPreferences 用户明确要求后续持续生效的会话偏好
 */
public record RewriteResult(RewriteStatus status,
                            String rewrittenQuestion,
                            List<String> subQuestions,
                            List<String> currentConstraints,
                            List<String> persistentPreferences) {

    /**
     * 判断当前结果是否需要扩大历史窗口后再次改写。
     *
     * @return true 表示现有历史无法完成指代消解
     */
    public boolean needsMoreContext() {
        return status == RewriteStatus.NEED_MORE_CONTEXT;
    }

    /**
     * 构建不包含约束和偏好的普通成功结果。
     *
     * @param rewrittenQuestion 改写后的问题
     * @param subQuestions      子问题列表
     * @return SUCCESS 状态的改写结果
     */
    public static RewriteResult success(String rewrittenQuestion, List<String> subQuestions) {
        return new RewriteResult(
                RewriteStatus.SUCCESS,
                rewrittenQuestion,
                subQuestions,
                List.of(),
                List.of()
        );
    }
}
