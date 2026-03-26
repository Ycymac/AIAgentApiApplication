package com.ycy.aiapplication.dao.entity;

import com.baomidou.mybatisplus.annotation.*;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@TableName("interview_record")
public class InterviewRecordDO {

    @TableId
    private Long id;

    private Long userId;

    private String recordName;
    /**
     * 面试回答记录，使用json形式存储
     * 将 ReportGenerationReqDTO 转换为json形式进行存储
     */
    private String interviewProcessRecord;
    /**
     * 面试总评
     */
    private String reportRecord;

    private Integer interviewPoint;

    private Integer accuracyScore;

    private Integer completenessScore;

    private Integer levelOfDetailScore;

    private Integer logicScore;

    private Integer expressionAbilityScore;
    /**
     * 面试创建时间
     * 使用配置进行自动注入
     */
    @TableField(fill= FieldFill.INSERT)
    private Date createTime;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Boolean deleted;
}
