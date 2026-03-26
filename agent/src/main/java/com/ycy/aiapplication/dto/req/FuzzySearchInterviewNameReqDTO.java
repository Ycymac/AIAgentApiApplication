package com.ycy.aiapplication.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FuzzySearchInterviewNameReqDTO {
    @Schema(
            description = "模糊查询用名称",
            example = "Java后端开发面试"

    )
    String name;

}
