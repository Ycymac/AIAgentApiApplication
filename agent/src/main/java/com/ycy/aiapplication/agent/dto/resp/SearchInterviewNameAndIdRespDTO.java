package com.ycy.aiapplication.agent.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SearchInterviewNameAndIdRespDTO {

    private String name;

    private Long id;

    private Integer interviewPoint;

    private Date createTime;
}
