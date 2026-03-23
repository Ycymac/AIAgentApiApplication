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
@Schema(description = "简历名称模糊查询请求参数")
public class FuzzySearchIntervieweeFormReqDTO {

    @Schema(description = "简历名称关键字", example = "Java")
    private String formName;
}
