package com.ycy.aiapplication.infrastructure.ai.model;

/**
 * 聊天用模型元数据
 *
 * @param id 模型id
 * @param provider 提供者id
 * @param model 实际模型名称
 */
public record ModelTarget(
        String id,
        String provider,
        String model
) {
}
