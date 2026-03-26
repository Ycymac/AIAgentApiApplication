package com.ycy.aiapplication.service;

import com.ycy.aiapplication.dto.req.FuzzySearchInterviewNameReqDTO;
import com.ycy.aiapplication.dto.resp.FuzzySearchInterviewRecordRespDTO;
import com.ycy.aiapplication.dto.resp.InterviewRecordRespDTO;
import com.ycy.aiapplication.dto.resp.SearchInterviewNameAndIdRespDTO;

import java.util.List;

/**
 * 记录相关服务
 */
public interface RecordService {
    /**
     * 通过名字进行模糊查询
     *用于搜索模块
     */
    List<FuzzySearchInterviewRecordRespDTO> fuzzySearchInterviewRecordName( FuzzySearchInterviewNameReqDTO requestParam);

    /**
     * 查询记录名称（缓存）
     */
    List<SearchInterviewNameAndIdRespDTO> searchInterviewNameAndId();

    /**
     * 因为我们在 ZSet当中存储的是当前记录的id，所以正常点击侧边栏的名称我们应当使用id查询，这样更加准确（名称很可能是重复的)
     *
     */
    InterviewRecordRespDTO searchInterviewRecordById(String id);

}
