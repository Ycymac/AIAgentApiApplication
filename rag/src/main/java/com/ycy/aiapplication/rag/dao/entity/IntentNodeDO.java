package com.ycy.aiapplication.rag.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 知识库意图节点持久化对象。
 * 作用：
 * 1. 对应数据库表 {@code rag_intent_node}。
 * 2. 保存第二层知识库识别所需的节点信息。
 * 3. 为“一个知识库对应一个意图节点”的持久化关系提供落库结构。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("rag_intent_node")
public class IntentNodeDO {

    /**
     * 节点主键 ID。
     */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /**
     * 关联知识库 ID。
     */
    private String kbId;

    /**
     * 节点名称。
     */
    private String name;

    /**
     * 节点语义描述。
     */
    private String description;

    /**
     * 示例问题文本，使用换行拼接存储。
     */
    private String examples;

    /**
     * 可选的短提示片段。
     */
    private String promptSnippet;

    /**
     * 可选的完整 Prompt 模板。
     */
    private String promptTemplate;

    /**
     * 是否启用，1 表示启用，0 表示禁用。
     */
    private Integer enabled;

    /**
     * 创建人。
     */
    private String createdBy;

    /**
     * 更新人。
     */
    private String updatedBy;

    /**
     * 创建时间。
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    /**
     * 更新时间。
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    /**
     * 逻辑删除标记。
     */
    @TableLogic
    private Integer deleted;
}
