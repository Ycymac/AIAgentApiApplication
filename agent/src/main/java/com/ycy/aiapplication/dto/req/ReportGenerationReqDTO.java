package com.ycy.aiapplication.dto.req;


import com.ycy.aiapplication.common.pojo.IntervieweeForm;
import com.ycy.aiapplication.dto.resp.AnswerEvaluationRespDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ReportGenerationReqDTO {

    //所有问题&ai的评价，通过所有的评价构建总评
    private List<AnswerEvaluationRespDTO> answerEvaluationRespS;
    //面试“简历”
    private IntervieweeForm form;


}
