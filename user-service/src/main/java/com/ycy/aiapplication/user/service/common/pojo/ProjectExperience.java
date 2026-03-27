package com.ycy.aiapplication.user.service.common.pojo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 项目经历信息。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Schema(description = "项目经历")
public class ProjectExperience {

    /**
     * 项目名称。
     */
    @Schema(description = "项目名称", example = "分布式高并发秒杀系统")
    private String projectName;

    /**
     * 在项目中的角色。
     */
    @Schema(description = "在项目中的角色", example = "核心开发")
    private String projectRole;

    /**
     * 项目背景或整体描述。
     */
    @Schema(description = "项目背景或整体描述", example = "基于 Spring Cloud Alibaba 构建的分布式秒杀系统。")
    private String projectDescription;

    /**
     * 主要职责。
     */
    @Schema(description = "主要职责", example = "1. 使用 Redis 实现分布式锁；2. 引入 RabbitMQ 进行削峰。")
    private String responsibility;

    /**
     * 项目成果。
     */
    @Schema(description = "项目成果", example = "成功支撑了 10 万 QPS 的并发峰值。")
    private String achievement;
}
