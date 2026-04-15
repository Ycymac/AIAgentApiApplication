package com.ycy.aiapplication.rag.control.vo;

import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * 意图节点视图对象。
 */
@Data
public class IntentNodeVO {

    private String id;

    /**
     * 节点类型：0=知识库节点，1=系统节点。
     */
    private Integer kind;

    private String kbId;

    private String kbName;

    private String collectionName;

    private String name;

    private String description;

    private List<String> examples;

    private String promptSnippet;

    private String promptTemplate;

    private Integer enabled;

    private String createdBy;

    private String updatedBy;

    private Date createTime;

    private Date updateTime;
}
