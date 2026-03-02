package com.ycy.aiapplication.agent.common.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class InterviewQuestion {

    /**
     * 问题对应的等级
     */
    private int level;
    /**
     * 问题对应的问题描述
     */
    private String questionDescription;

}
