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
 * 简历持久化对象
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@TableName("interviewee_form")
public class IntervieweeFormDO {

    @TableId
    private Long id;

    /**
     * 简历名称
     */
    private String formName;

    /**
     * 当前用户id
     */
    private Long userId;

    /**
     * 面试对象年级
     */
    private String grade;

    /**
     * 学习专业
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

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Boolean deleted;
}
