package com.ycy.aiapplication.common.pojo;

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
 * 面试使用的简历结构。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Schema(description = "面试使用的简历结构")
public class IntervieweeForm {

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
    @ArraySchema(schema = @Schema(description = "专业技能项", example = "Spring Cloud"))
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
