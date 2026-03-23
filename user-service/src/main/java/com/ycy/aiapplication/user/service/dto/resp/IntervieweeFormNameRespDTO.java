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
@Schema(description = "简历名称与时间返回参数")
public class IntervieweeFormNameRespDTO {

    @Schema(description = "简历id", example = "2031456789012345678")
    private Long id;

    @Schema(description = "简历名称", example = "Java后端实习生简历")
    private String formName;

    @Schema(description = "对应时间")
    private Date createTime;
}
