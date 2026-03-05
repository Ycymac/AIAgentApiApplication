package com.ycy.aiapplication.agent.dao.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@TableName("interview_record")
public class InterviewRecordDO {

    @TableId
    private Long id;

    private Long userId;

    private String recordName;
    /**
     * 面试回答记录，使用json形式存储
     * 将 ReportGenerationReqDTO 转换为json形式进行存储
     */
    private String interviewProcessRecord;
    /**
     * 面试总评
     */
    private String reportRecord;
    /**
     * 面试时间
     */
    private Date date;
    @TableLogic
    private boolean deleted;
}
