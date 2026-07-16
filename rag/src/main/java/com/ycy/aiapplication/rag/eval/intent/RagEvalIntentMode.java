package com.ycy.aiapplication.rag.eval.intent;

import cn.hutool.core.util.StrUtil;

/**
 * Eval-only intent routing modes.
 */
public enum RagEvalIntentMode {

    /** Production-compatible two-stage routing. */
    LAYERED,

    /** One LLM call scores SYSTEM and every enabled knowledge-base node together. */
    COMBINED;

    public static RagEvalIntentMode parse(String value) {
        if (StrUtil.isBlank(value)) {
            return COMBINED;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("intentMode must be layered or combined");
        }
    }

    public String value() {
        return name().toLowerCase();
    }
}
