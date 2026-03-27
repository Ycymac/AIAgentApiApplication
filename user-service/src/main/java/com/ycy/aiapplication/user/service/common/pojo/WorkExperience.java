package com.ycy.aiapplication.user.service.common.pojo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工作经历信息。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Schema(description = "工作经历")
public class WorkExperience {

    /**
     * 公司名称。
     */
    @Schema(description = "公司名称", example = "腾讯科技（深圳）有限公司")
    private String companyName;

    /**
     * 开始时间，建议使用 YYYY-MM 格式。
     */
    @Schema(description = "开始时间，建议使用 YYYY-MM 格式", example = "2020-07")
    private String startDate;

    /**
     * 结束时间，建议使用 YYYY-MM 格式或“至今”。
     */
    @Schema(description = "结束时间，建议使用 YYYY-MM 格式或“至今”", example = "至今")
    private String endDate;

    /**
     * 所属部门。
     */
    @Schema(description = "所属部门", example = "PCG 事业群")
    private String department;

    /**
     * 岗位名称。
     */
    @Schema(description = "岗位名称", example = "后端开发工程师")
    private String position;

    /**
     * 工作内容描述。
     */
    @Schema(description = "工作内容描述", example = "负责内容分发平台核心交易链路的设计与开发。")
    private String workContent;
}
