package com.ycy.aiapplication.agent.common.pojo;

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
    private String grade;
    /**
     * 学习的专业
     * 前端进行填写
     */
    private String major;
    /**
     * 学习方向
     * 个人填写
     */
    private String learningDirection;
    /**
     * 学习进度
     * 个人填写
     */
    private String learningProgress;
}
