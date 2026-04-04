package com.ycy.aiapplication.knowledge.control.request.base;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;

/**
 * 知识库分页查询请求
 */
@Data
public class KnowledgeBasePageRequest extends Page {

    /**
     * 知识库名称（支持模糊匹配）
     */
    private String name;
}