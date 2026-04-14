package com.ycy.aiapplication.rag.config;

import com.ycy.aiapplication.rag.core.intent.common.enums.IntentKind;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 引导式问答配置。
 * <p>
 * 该配置类用于集中管理 guidance 相关开关和阈值，
 * 包括是否启用引导式回复、按哪种意图类型触发引导、
 * 分数接近时的判定阈值，以及单次最多展示的候选项数量。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "rag.guidance")
public class GuidanceProperties {

    /**
     * 是否启用引导式问答。
     * 为 {@code true} 时，聊天链路会在满足触发条件时返回引导式提示；
     * 为 {@code false} 时，直接跳过 guidance 逻辑。
     */
    private Boolean enabled = true;

    /**
     * 触发引导式问答的意图类型。
     * 当前默认值为 {@link IntentKind#UNKNOWN}，
     * 表示只有当意图识别结果为 UNKNOWN 时才尝试返回引导式提示。
     */
    private IntentKind ambiguityIntentType = IntentKind.UNKNOWN;

    /**
     * 引导式响应触发分数
     */
    private Double ambiguityMinScore = 0.25D;
    /**
     * 单次最多展示的候选项数量。
     * 用于限制返回给用户的可选系统/主题数量，避免提示过长。
     */
    private Integer maxOptions = 6;
}
