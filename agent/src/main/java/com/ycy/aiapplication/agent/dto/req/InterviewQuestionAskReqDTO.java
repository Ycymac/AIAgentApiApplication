package com.ycy.aiapplication.agent.dto.req;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 面试的“简历”
 * 包含面试人的年级、专业、学习方向、学习进度
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class InterviewQuestionAskReqDTO {

    /**
     * 面试对象年级
     */
    private String grade;
    /**
     * 学习的专业
     */
    private String major;
    /**
     * 学习方向
     */
    private String learningDirection;
    /**
     * 学习进度
     */
    private String learningProgress;

    public String getDescription(){
        return "面试官您好，我是一位" +grade+
                "年级学生，我的专业是" +major+
                "，我的主要的学习方向是" +learningDirection+
                "，我当前的学习进度为："+learningProgress;

    }
}
