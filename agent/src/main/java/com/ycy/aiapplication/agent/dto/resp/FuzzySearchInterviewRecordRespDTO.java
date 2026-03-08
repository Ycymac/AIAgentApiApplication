package com.ycy.aiapplication.agent.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FuzzySearchInterviewRecordRespDTO {
    /**
     * 记录名称
     */
    private String recordName;
    /**
     * 记录id
     */
    private Long id;
}
