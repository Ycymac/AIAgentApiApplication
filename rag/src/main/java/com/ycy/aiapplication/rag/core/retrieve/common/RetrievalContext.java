package com.ycy.aiapplication.rag.core.retrieve.common;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.ycy.aiapplication.framework.convention.RetrievedChunk;
import com.ycy.aiapplication.rag.core.retrieve.channel.SearchChannelResult;
import lombok.Builder;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 检索阶段的统一输出上下文。
 * <p>
 * 整体职责：
 * 1. 承载最终拼装好的知识库正文。
 * 2. 保留按意图节点聚合的分块结果，便于 Prompt 规划阶段做模板与证据选择。
 * 3. 保留原始通道返回，方便后续扩展调试、监控或后处理链。
 */
@Data
@Builder
public class RetrievalContext {

    /**
     * 供通用 KB Prompt 直接引用的知识库正文。
     */
    private String kbContext;

    /**
     * 按意图节点聚合后的检索分块。
     * key 通常为意图节点 ID，兜底全库检索场景下会使用统一占位 key。
     */
    private Map<String, List<RetrievedChunk>> intentChunks;

    /**
     * 各检索通道的原始返回结果，便于调试与后续扩展。
     */
    private List<SearchChannelResult> channelResults;

    /**
     * 判断当前检索上下文是否为空。
     *
     * @return true 表示当前没有可用于构建 KB Prompt 的有效检索内容。
     */
    public boolean isEmpty() {
        return StrUtil.isBlank(kbContext) || CollUtil.isEmpty(intentChunks);
    }

    /**
     * 构造一个空的检索上下文对象，避免上层出现空指针判断分散。
     *
     * @return 空检索上下文。
     */
    public static RetrievalContext empty() {
        return RetrievalContext.builder()
                .kbContext("")
                .intentChunks(new LinkedHashMap<>())
                .channelResults(List.of())
                .build();
    }
}
