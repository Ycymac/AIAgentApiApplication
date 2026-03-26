package com.ycy.aiapplication.user.service.dto.resp;

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
@Schema(description = "简历详情返回参数")
public class IntervieweeFormRespDTO {

    @Schema(description = "简历id", example = "2031456789012345678")
    private String id;

    @Schema(description = "简历名称", example = "Java后端实习生简历")
    private String formName;

    @Schema(description = "当前用户id", example = "2028780128997244929")
    private String userId;

    @Schema(description = "面试对象年级", example = "大二")
    private String grade;

    @Schema(description = "学习专业", example = "软件工程")
    private String major;

    @Schema(description = "学习方向", example = "Java后端开发")
    private String learningDirection;

    @Schema(description = "学习进度", example = "SpringBoot、JavaSE、Redis、MySQL、JVM")
    private String learningProgress;

    @Schema(description = "创建时间")
    private Date createTime;
}
