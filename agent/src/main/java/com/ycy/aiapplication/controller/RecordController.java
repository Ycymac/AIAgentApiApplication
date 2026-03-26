package com.ycy.aiapplication.controller;

import com.ycy.aiapplication.dto.req.FuzzySearchInterviewNameReqDTO;
import com.ycy.aiapplication.dto.req.SearchInterviewRecordByIdReqDTO;
import com.ycy.aiapplication.dto.resp.FuzzySearchInterviewRecordRespDTO;
import com.ycy.aiapplication.dto.resp.InterviewRecordRespDTO;
import com.ycy.aiapplication.dto.resp.SearchInterviewNameAndIdRespDTO;
import com.ycy.aiapplication.service.RecordService;
import com.ycy.aiapplication.framework.web.Result;
import com.ycy.aiapplication.framework.web.Results;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name="记录访问模块")
@RestController
@RequestMapping("/record/service")
@RequiredArgsConstructor
public class RecordController {

    private final RecordService recordService;

    @Operation(summary = "搜索（支持模糊查询）")
    @PostMapping("/fuzzy/search")
    public Result<List<FuzzySearchInterviewRecordRespDTO>> fuzzySearchInterviewName(@RequestBody FuzzySearchInterviewNameReqDTO requestParam){
        return Results.success(recordService.fuzzySearchInterviewRecordName(requestParam));
    }

    @Operation(summary="记录名称显示")
    @GetMapping("/search/record")
    public Result<List<SearchInterviewNameAndIdRespDTO>> searchInterviewNameAndId(){
        return Results.success(recordService.searchInterviewNameAndId());
    }

    @Operation(summary = "显示点击的当前记录")
    @PostMapping("/click/record")
    public Result<InterviewRecordRespDTO> searchInterviewRecordById(@RequestBody SearchInterviewRecordByIdReqDTO requestParam){
        return Results.success(recordService.searchInterviewRecordById(requestParam.getId()));
    }





}
