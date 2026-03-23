package com.ycy.aiapplication.user.service.service;

import com.ycy.aiapplication.user.service.dto.req.CreateIntervieweeFormReqDTO;
import com.ycy.aiapplication.user.service.dto.req.FuzzySearchIntervieweeFormReqDTO;
import com.ycy.aiapplication.user.service.dto.req.UpdateIntervieweeFormReqDTO;
import com.ycy.aiapplication.user.service.dto.resp.IntervieweeFormNameRespDTO;
import com.ycy.aiapplication.user.service.dto.resp.IntervieweeFormRespDTO;

import java.util.List;

/**
 * 简历持久化服务
 */
public interface IntervieweeFormService {

    /**
     * 新增简历
     */
    IntervieweeFormRespDTO addIntervieweeForm(CreateIntervieweeFormReqDTO requestParam);

    /**
     * 修改简历
     */
    void updateIntervieweeForm(UpdateIntervieweeFormReqDTO requestParam);

    /**
     * 删除简历
     */
    void deleteIntervieweeForm(Long id);

    /**
     * 根据id查询简历
     */
    IntervieweeFormRespDTO searchIntervieweeFormById(Long id);

    /**
     * 模糊查询简历
     */
    List<IntervieweeFormRespDTO> fuzzySearchIntervieweeForm(FuzzySearchIntervieweeFormReqDTO requestParam);

    /**
     * 查询redis中的简历名称和时间
     */
    List<IntervieweeFormNameRespDTO> searchIntervieweeFormNameList();
}
