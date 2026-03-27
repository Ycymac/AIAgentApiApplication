package com.ycy.aiapplication.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Schema(description = "生成面试题请求参数")
public class InterviewQuestionAskReqDTO {

    @Schema(description = "当前面试使用的简历id", example = "2035976738638413825")
    private String formId;
}
