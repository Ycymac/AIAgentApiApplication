package com.ycy.aiapplication.user.service.dto.resp;

import com.ycy.aiapplication.user.service.common.pojo.EducationExperience;
import com.ycy.aiapplication.user.service.common.pojo.ProjectExperience;
import com.ycy.aiapplication.user.service.common.pojo.WorkExperience;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

/**
 * 简历详情返回参数。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Schema(description = "简历详情返回参数")
public class IntervieweeFormRespDTO {

    /**
     * 简历主键 ID。
     */
    @Schema(description = "简历主键 ID", example = "2031456789012345678")
    private String id;

    /**
     * 简历名称。
     */
    @Schema(description = "简历名称", example = "Java后端秋招简历")
    private String formName;

    /**
     * 当前用户 ID。
     */
    @Schema(description = "当前用户 ID", example = "2028780128997244929")
    private String userId;

    /**
     * 候选人姓名。
     */
    @Schema(description = "候选人姓名", example = "张三")
    private String candidateName;

    /**
     * 求职意向或目标岗位。
     */
    @Schema(description = "求职意向或目标岗位", example = "高级Java开发工程师")
    private String jobIntention;

    /**
     * 专业技能列表。
     */
    @ArraySchema(schema = @Schema(description = "专业技能项", example = "MySQL"))
    private List<String> professionalSkills;

    /**
     * 教育经历列表。
     */
    @ArraySchema(schema = @Schema(implementation = EducationExperience.class))
    private List<EducationExperience> educationExperiences;

    /**
     * 工作经历列表。
     */
    @ArraySchema(schema = @Schema(implementation = WorkExperience.class))
    private List<WorkExperience> workExperiences;

    /**
     * 项目经历列表。
     */
    @ArraySchema(schema = @Schema(implementation = ProjectExperience.class))
    private List<ProjectExperience> projectExperiences;

    /**
     * 创建时间。
     */
    @Schema(description = "创建时间")
    private Date createTime;
}
