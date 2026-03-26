package com.ycy.aiapplication.dto.req;

import com.ycy.aiapplication.common.pojo.IntervieweeForm;
import io.swagger.v3.oas.annotations.media.Schema;
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
    @Schema(
            description = "面试表格"
    )
    private IntervieweeForm form;

    public String getFormDescription(){
        return "面试官您好，我是一位" +form.getGrade()+
                "年级学生，我的专业是" +form.getMajor()+
                "，我的主要的学习方向是" +form.getLearningDirection()+
                "，我当前的学习进度为："+form.getLearningProgress();

    }
}
