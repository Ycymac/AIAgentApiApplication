package com.ycy.aiapplication.rag.core.retrieve.channel;

import com.ycy.aiapplication.framework.convention.RetrievedChunk;
import lombok.Builder;
import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 检索通道结果。
 * <p>
 * 整体职责：
 * 1. 封装单个检索通道的执行结果。
 * 2. 统一承载通道命中的分块、耗时、置信度与扩展元数据。
 * 3. 作为检索编排器与后处理器之间的标准数据结构。
 */
@Data
@Builder
public class SearchChannelResult {

    /**
     * 通道类型。
     */
    private SearchChannelType channelType;

    /**
     * 通道名称。
     */
    private String channelName;

    /**
     * 当前通道检索到的分块列表。
     */
    private List<RetrievedChunk> chunks;

    /**
     * 通道置信度，范围通常为 0 到 1。
     */
    private double confidence;

    /**
     * 检索耗时，单位毫秒。
     */
    private long latencyMs;

    /**
     * 扩展元数据，例如意图分块映射、统计信息等。
     */
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();
}
