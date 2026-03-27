package com.ycy.aiapplication.user.service.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 简历持久化对象。
 * 其中专业技能、教育经历、工作经历、项目经历均以 JSON 字符串形式存储。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@TableName("interviewee_form")
public class IntervieweeFormDO {

    /**
     * 简历主键 ID。
     */
    @TableId
    private Long id;

    /**
     * 简历名称。
     */
    private String formName;

    /**
     * 所属用户 ID。
     */
    private Long userId;

    /**
     * 候选人姓名。
     */
    private String candidateName;

    /**
     * 求职意向。
     */
    private String jobIntention;

    /**
     * 专业技能 JSON 数组。
     */
    private String professionalSkills;

    /**
     * 教育经历 JSON 数组。
     */
    private String educationExperiences;

    /**
     * 工作经历 JSON 数组。
     */
    private String workExperiences;

    /**
     * 项目经历 JSON 数组。
     */
    private String projectExperiences;

    /**
     * 创建时间。
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    /**
     * 逻辑删除标记。
     */
    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Boolean deleted;
}
