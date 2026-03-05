package com.ycy.aiapplication.agent.dto.resp;

import com.ycy.aiapplication.agent.dto.AgentInterviewReportDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;


@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ReportGenerationRespDTO {

    private Date date;

    private String recordName;

    private AgentInterviewReportDTO reportDTO;
}
