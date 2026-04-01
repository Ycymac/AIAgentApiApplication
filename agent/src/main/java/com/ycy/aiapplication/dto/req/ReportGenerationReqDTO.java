package com.ycy.aiapplication.dto.req;

import com.ycy.aiapplication.dto.resp.AnswerEvaluationRespDTO;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Schema(description = "生成面试报告请求参数")
public class ReportGenerationReqDTO {

    @ArraySchema(schema = @Schema(implementation = AnswerEvaluationRespDTO.class))
    private AnswerEvaluationRespDTO[] answerEvaluationRespS;

    @Schema(description = "当前面试使用的简历id", example = "2031456789012345678")
    private String formId;

    public List<AnswerEvaluationRespDTO> getAnswerEvaluationRespList() {
        if (answerEvaluationRespS == null || answerEvaluationRespS.length == 0) {
            return Collections.emptyList();
        }
        return Arrays.asList(answerEvaluationRespS);
    }
}
