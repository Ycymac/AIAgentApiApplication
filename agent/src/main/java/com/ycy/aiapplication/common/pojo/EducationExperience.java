package com.ycy.aiapplication.common.pojo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 教育经历信息。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Schema(description = "教育经历")
public class EducationExperience {

    /**
     * 学校名称。
     */
    @Schema(description = "学校名称", example = "华中科技大学")
    private String schoolName;

    /**
     * 开始时间，建议使用 YYYY-MM 格式。
     */
    @Schema(description = "开始时间，建议使用 YYYY-MM 格式", example = "2016-09")
    private String startDate;

    /**
     * 结束时间，建议使用 YYYY-MM 格式。
     */
    @Schema(description = "结束时间，建议使用 YYYY-MM 格式", example = "2020-07")
    private String endDate;

    /**
     * 所学专业。
     */
    @Schema(description = "所学专业", example = "计算机科学与技术")
    private String major;

    /**
     * 学历层次。
     */
    @Schema(description = "学历层次", example = "本科")
    private String degree;

    /**
     * 补充说明。
     */
    @Schema(description = "补充说明，例如 GPA、院校标签等", example = "985/211 | GPA: 3.8/4.0")
    private String highlights;
}
