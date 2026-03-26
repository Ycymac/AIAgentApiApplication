package com.ycy.aiapplication.dto.req;

import com.ycy.aiapplication.common.pojo.QuestionWithAnswer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AnswerEvaluationReqDTO {
    @Schema(
            description = "所有的问题和回答"
    )
    private List<QuestionWithAnswer> questionWithAnswers;
}
