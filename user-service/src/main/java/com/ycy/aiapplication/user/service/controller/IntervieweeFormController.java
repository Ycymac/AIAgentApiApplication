package com.ycy.aiapplication.user.service.controller;

import com.ycy.aiapplication.framework.web.Result;
import com.ycy.aiapplication.framework.web.Results;
import com.ycy.aiapplication.user.service.dto.req.CreateIntervieweeFormReqDTO;
import com.ycy.aiapplication.user.service.dto.req.DeleteIntervieweeFormReqDTO;
import com.ycy.aiapplication.user.service.dto.req.FuzzySearchIntervieweeFormReqDTO;
import com.ycy.aiapplication.user.service.dto.req.SearchIntervieweeFormByIdReqDTO;
import com.ycy.aiapplication.user.service.dto.req.UpdateIntervieweeFormReqDTO;
import com.ycy.aiapplication.user.service.dto.resp.IntervieweeFormNameRespDTO;
import com.ycy.aiapplication.user.service.dto.resp.IntervieweeFormRespDTO;
import com.ycy.aiapplication.user.service.service.IntervieweeFormService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("api/interviewee/form")
@RequiredArgsConstructor
@Tag(name = "简历持久化管理")
public class IntervieweeFormController {

    private final IntervieweeFormService intervieweeFormService;

    @Operation(summary = "新增简历")
    @PostMapping("/add")
    public Result<IntervieweeFormRespDTO> addIntervieweeForm(@RequestBody CreateIntervieweeFormReqDTO requestParam) {
        return Results.success(intervieweeFormService.addIntervieweeForm(requestParam));
    }

    @Operation(summary = "修改简历")
    @PutMapping("/update")
    public Result<Void> updateIntervieweeForm(@RequestBody UpdateIntervieweeFormReqDTO requestParam) {
        intervieweeFormService.updateIntervieweeForm(requestParam);
        return Results.success();
    }

    @Operation(summary = "删除简历")
    @DeleteMapping("/delete")
    public Result<Void> deleteIntervieweeForm(@RequestBody DeleteIntervieweeFormReqDTO requestParam) {
        intervieweeFormService.deleteIntervieweeForm(requestParam.getId());
        return Results.success();
    }

    @Operation(summary = "根据id查询简历")
    @PostMapping("/search/by/id")
    public Result<IntervieweeFormRespDTO> searchIntervieweeFormById(@RequestBody SearchIntervieweeFormByIdReqDTO requestParam) {
        return Results.success(intervieweeFormService.searchIntervieweeFormById(requestParam.getId()));
    }

    @Operation(summary = "简历名称模糊查询")
    @PostMapping("/fuzzy/search")
    public Result<List<IntervieweeFormRespDTO>> fuzzySearchIntervieweeForm(@RequestBody FuzzySearchIntervieweeFormReqDTO requestParam) {
        return Results.success(intervieweeFormService.fuzzySearchIntervieweeForm(requestParam));
    }

    @Operation(summary = "查询redis中的简历名称和时间")
    @GetMapping("/search/name/list")
    public Result<List<IntervieweeFormNameRespDTO>> searchIntervieweeFormNameList() {
        return Results.success(intervieweeFormService.searchIntervieweeFormNameList());
    }
}
