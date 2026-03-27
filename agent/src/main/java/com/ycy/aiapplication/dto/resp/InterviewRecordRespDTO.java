package com.ycy.aiapplication.dto.resp;

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
@Schema(description = "面试记录详情返回参数")
public class InterviewRecordRespDTO {

    @Schema(description = "记录主键ID", example = "2031456789012345678")
    private String id;

    @Schema(description = "所属用户ID", example = "2028780128997244929")
    private String userId;

    @Schema(description = "记录名称", example = "Java后端面试")
    private String recordName;

    @Schema(description = "面试过程记录，仅存储 answerEvaluationRespS 的 JSON 字符串")
    private String interviewProcessRecord;

    @Schema(description = "面试关键字，对应简历 professionalSkills 的 JSON 字符串")
    private String interviewKeywords;

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

    @Schema(description = "创建时间")
    private Date createTime;

    @Schema(description = "逻辑删除标记", example = "false")
    private Boolean deleted;
}
