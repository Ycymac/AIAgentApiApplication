package com.ycy.aiapplication.agent.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SearchInterviewNameAndIdRespDTO {

    private String name;

    private Long id;
}
