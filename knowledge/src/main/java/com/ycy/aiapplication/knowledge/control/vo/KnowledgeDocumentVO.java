package com.ycy.aiapplication.knowledge.control.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库文档视图对象。
 */
@Data
public class KnowledgeDocumentVO {

    private String id;

    private String kbId;

    private String docName;

    private String sourceType;

    private String sourceLocation;

    private Integer scheduleEnabled;

    private String scheduleCron;

    private Boolean enabled;

    private Integer chunkCount;

    private String fileUrl;

    private String fileType;

    private Long fileSize;

    private String chunkStrategy;

    /**
     * 处理模式，当前仅支持 chunk。
     */
    private String processMode;

    private String chunkConfig;

    private String status;

    private String createdBy;

    private String updatedBy;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
