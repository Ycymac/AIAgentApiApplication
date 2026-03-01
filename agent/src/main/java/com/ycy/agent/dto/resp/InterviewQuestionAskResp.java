package com.ycy.agent.dto.resp;

import com.ycy.agent.common.pojo.InterviewQuestion;
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
public class InterviewQuestionAskResp {

    private List<InterviewQuestion> questions;
}
