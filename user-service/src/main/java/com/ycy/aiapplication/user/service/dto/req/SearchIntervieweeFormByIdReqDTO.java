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
@Schema(description = "根据id查询简历请求参数")
public class SearchIntervieweeFormByIdReqDTO {

    @Schema(description = "简历id", example = "2031456789012345678")
    private String id;
}
