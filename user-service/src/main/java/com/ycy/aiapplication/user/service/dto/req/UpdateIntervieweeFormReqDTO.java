package com.ycy.aiapplication.user.service.dto.req;

import com.ycy.aiapplication.user.service.common.pojo.EducationExperience;
import com.ycy.aiapplication.user.service.common.pojo.ProjectExperience;
import com.ycy.aiapplication.user.service.common.pojo.WorkExperience;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 修改简历请求参数。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Schema(description = "修改简历请求参数")
public class UpdateIntervieweeFormReqDTO {

    /**
     * 简历主键 ID。
     */
    @Schema(description = "简历主键 ID", example = "2031456789012345678")
    private String id;

    /**
     * 简历名称，用于前端展示和列表搜索。
     */
    @Schema(description = "简历名称，用于前端展示和列表搜索", example = "Java后端秋招简历")
    private String formName;

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
    @ArraySchema(schema = @Schema(description = "专业技能项", example = "Redis"))
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
}
