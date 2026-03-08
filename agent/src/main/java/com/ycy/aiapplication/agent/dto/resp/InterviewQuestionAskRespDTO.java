package com.ycy.aiapplication.agent.dto.resp;

import com.ycy.aiapplication.agent.common.pojo.InterviewQuestion;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 面试对应的问题，共15个
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class InterviewQuestionAskRespDTO {
    @Schema(
     description = "问题List"
    )
    private List<InterviewQuestion> questions;
}
