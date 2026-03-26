package com.ycy.aiapplication.common.pojo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class IntervieweeForm {
    /**
     * 面试对象年级
     * 这里前端应该通过菜单栏进行选择而不是直接填写
     */
    @Schema(
            description = "面试对象年级",
            example = "大二"

    )
    private String grade;
    /**
     * 学习的专业
     * 前端进行填写
     */
    @Schema(
            description = "学习专业",
            example = "软件工程"

    )
    private String major;
    /**
     * 学习方向
     * 个人填写
     */
    @Schema(
            description = "学习方向",
            example = "Java后端开发"
    )
    private String learningDirection;
    /**
     * 学习进度
     * 个人填写
     */
    @Schema(
            description = "学习进度",
            example = "学习过的知识点有：1.SpringBoot框架 2.JavaSE基础 3.redis基础，熟悉所有数据类型 3.MySQL使用、底层，尤其是执行引擎相关部分 4.JVM底层知识，例如堆、栈、栈帧、执行引擎、方法区（永久代和元空间）"
    )
    private String learningProgress;
}
