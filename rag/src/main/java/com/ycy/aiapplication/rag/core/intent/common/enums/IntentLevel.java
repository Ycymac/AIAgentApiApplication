package com.ycy.aiapplication.rag.core.intent.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 意图层级枚举。
 * 作用：
 * 1. 描述意图树中的层级关系。
 * 2. 为后续扩展更细粒度意图树结构保留层级能力。
 */
@Getter
@RequiredArgsConstructor
public enum IntentLevel {

    /**
     * 顶层领域。
     */
    DOMAIN(0),

    /**
     * 中间分类层。
     */
    CATEGORY(1),

    /**
     * 具体主题层。
     */
    TOPIC(2);

    private final int code;

    /**
     * 根据编码反查层级。
     *
     * @param code 层级编码
     * @return 命中的层级枚举；未命中时返回 null
     */
    public static IntentLevel fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (IntentLevel e : values()) {
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
