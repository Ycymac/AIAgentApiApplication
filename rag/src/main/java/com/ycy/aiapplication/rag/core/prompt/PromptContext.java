package com.ycy.aiapplication.rag.core.prompt;

import cn.hutool.core.util.StrUtil;
import com.ycy.aiapplication.framework.convention.RetrievedChunk;

import com.ycy.aiapplication.rag.core.intent.NodeScore;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class PromptContext {

    private String question;

    private String kbContext;

    private List<NodeScore> kbIntents;

    private Map<String, List<RetrievedChunk>> intentChunks;

    public boolean hasKb() {
        return StrUtil.isNotBlank(kbContext);
    }
}