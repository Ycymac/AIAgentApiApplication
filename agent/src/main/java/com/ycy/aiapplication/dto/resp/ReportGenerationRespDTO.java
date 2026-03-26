package com.ycy.aiapplication.dto.resp;

import com.ycy.aiapplication.dto.AgentInterviewReportDTO;
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
