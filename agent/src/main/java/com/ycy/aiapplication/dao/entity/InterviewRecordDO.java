package com.ycy.aiapplication.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "面试记录持久化对象")
public class InterviewRecordDO {

    @TableId
    @Schema(description = "记录主键ID", example = "2031456789012345678")
    private Long id;

    @Schema(description = "所属用户ID", example = "2028780128997244929")
    private Long userId;

    @Schema(description = "记录名称", example = "Java后端面试")
    private String recordName;
    /**
     * 面试过程记录，仅存储 answerEvaluationRespS 的 JSON 字符串。
     */
    @Schema(description = "面试过程记录，仅存储 answerEvaluationRespS 的 JSON 字符串")
    private String interviewProcessRecord;
    /**
     * 面试关键字，对应简历 professionalSkills 的 JSON 字符串。
     */
    @Schema(description = "面试关键字，对应简历 professionalSkills 的 JSON 字符串")
    private String interviewKeywords;

    /**
     * 面试总评。
     */
    @Schema(description = "面试报告 JSON 字符串")
    private String reportRecord;

    @Schema(description = "面试总分，100分制", example = "86")
    private Integer interviewPoint;

    @Schema(description = "准确度总分，10分制", example = "9")
    private Integer accuracyScore;

    @Schema(description = "完整度总分，10分制", example = "8")
    private Integer completenessScore;

    @Schema(description = "详细度总分，10分制", example = "8")
    private Integer levelOfDetailScore;

    @Schema(description = "逻辑度总分，10分制", example = "9")
    private Integer logicScore;

    @Schema(description = "表达能力总分，10分制", example = "8")
    private Integer expressionAbilityScore;
    /**
     * 面试创建时间
     * 使用配置进行自动注入
     */
    @TableField(fill= FieldFill.INSERT)
    @Schema(description = "创建时间")
    private Date createTime;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "逻辑删除标记", example = "false")
    private Boolean deleted;
}
