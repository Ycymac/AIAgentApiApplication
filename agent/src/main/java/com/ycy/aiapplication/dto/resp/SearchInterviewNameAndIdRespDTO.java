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
public class SearchInterviewNameAndIdRespDTO {

    private String name;

    private String id;

    private Integer interviewPoint;

    private Date createTime;
}
