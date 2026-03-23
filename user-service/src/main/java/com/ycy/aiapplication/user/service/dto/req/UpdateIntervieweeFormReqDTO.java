package com.ycy.aiapplication.user.service.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Schema(description = "修改简历请求参数")
public class UpdateIntervieweeFormReqDTO {

    @Schema(description = "简历id", example = "2031456789012345678")
    private Long id;

    @Schema(description = "简历名称", example = "Java后端秋招简历")
    private String formName;

    @Schema(description = "面试对象年级", example = "大三")
    private String grade;

    @Schema(description = "学习专业", example = "软件工程")
    private String major;

    @Schema(description = "学习方向", example = "Java后端开发")
    private String learningDirection;

    @Schema(description = "学习进度", example = "SpringBoot、Redis、MySQL、JUC、JVM")
    private String learningProgress;
}
