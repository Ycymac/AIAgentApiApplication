package com.ycy.aiapplication.rag.core.prompt;

import cn.hutool.core.util.StrUtil;
import com.ycy.aiapplication.framework.convention.RetrievedChunk;

import com.ycy.aiapplication.rag.core.intent.common.NodeScore;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 提示词上下文
 */
@Data
@Builder
public class PromptContext {

    /**
     * 问题
     */
    private String question;

    /**
     * 知识库上下文
     */
    private String kbContext;

    /**
     * 知识库结点意图意图探测结果
     */
    private List<NodeScore> kbIntents;

    /**
     * 各个知识库结点检索到的分块列表
     */
    private Map<String, List<RetrievedChunk>> intentChunks;

    public boolean hasKb() {
        return StrUtil.isNotBlank(kbContext);
    }
}