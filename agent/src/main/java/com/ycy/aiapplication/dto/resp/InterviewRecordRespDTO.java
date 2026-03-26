package com.ycy.aiapplication.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class InterviewRecordRespDTO {

    private String id;

    private String userId;

    private String recordName;

    private String interviewProcessRecord;

    private String reportRecord;

    private Integer interviewPoint;

    private Integer accuracyScore;

    private Integer completenessScore;

    private Integer levelOfDetailScore;

    private Integer logicScore;

    private Integer expressionAbilityScore;

    private Date createTime;

    private Boolean deleted;
}
