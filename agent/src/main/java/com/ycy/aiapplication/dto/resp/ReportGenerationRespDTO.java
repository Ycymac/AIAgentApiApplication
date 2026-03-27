package com.ycy.aiapplication.dto.resp;

import com.ycy.aiapplication.dto.AgentInterviewReportDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;


@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Schema(description = "生成面试报告返回参数")
public class ReportGenerationRespDTO {

    @Schema(description = "记录创建时间")
    private Date date;

    @Schema(description = "记录名称", example = "Java后端面试")
    private String recordName;

    @Schema(description = "面试报告结果")
    private AgentInterviewReportDTO reportDTO;
}
