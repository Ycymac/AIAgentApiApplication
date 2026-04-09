package com.ycy.aiapplication.rag.core.prompt.plan;

import com.ycy.aiapplication.rag.core.prompt.PromptScene;
import lombok.Builder;
import lombok.Data;

/**
 * 提示词构建计划
 */
@Data
@Builder
public class PromptBuildPlan {

    private PromptScene scene;

    /**
     * 提示词模板
     */
    private String baseTemplate;

    /**
     * 知识库检索内容
     */
    private String kbContext;

    /**
     * 问题
     */
    private String question;
}