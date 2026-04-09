package com.ycy.aiapplication.rag.core.intent.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 意图类型枚举。
 * 作用：
 * 1. 标识当前问题最终应该走知识库、系统问答还是 UNKNOWN。
 * 2. 为解析器、检索层和回答层提供统一的路由标识。
 */
@Getter
@RequiredArgsConstructor
public enum IntentKind {

    /**
     * 知识库类问题，进入 RAG 检索流程。
     */
    KB(0),

    /**
     * 系统问答、欢迎语或闲聊类问题。
     */
    SYSTEM(1),

    /**
     * 两层识别信号都不足，且没有知识库命中时的兜底类型。
     */
    UNKNOWN(2);

    /**
     * 意图类型编码。
     */
    private final int code;

    /**
     * 根据编码反查意图类型。
     *
     * @param code 意图类型编码
     * @return 命中的枚举值；未命中时返回 null
     */
    public static IntentKind fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (IntentKind e : values()) {
            if (e.code == code) {
                return e;
            }
        }
        return null;
    }

    /**
     * 返回枚举名称。
     *
     * @return 枚举名
     */
    @Override
    public String toString() {
        return name();
    }
}
