package com.ycy.aiapplication.rag.core.rewrite.common;

/**
 * 查询改写状态。
 */
public enum RewriteStatus {
    /**
     * 当前历史足以完成改写，可继续意图识别。
     */
    SUCCESS,

    /**
     * 当前问题存在无法消解的指代，需要加载更完整历史或引导用户补充信息。
     */
    NEED_MORE_CONTEXT
}
