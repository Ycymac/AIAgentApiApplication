package com.ycy.aiapplication.agent.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SearchInterviewRecordByIdReqDTO {
    @Schema(
            description = "面试记录数据库id",
            example = "2029419678962561026"

    )
    private String id;
}
