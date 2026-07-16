package com.ycy.aiapplication.rag.core.retrieve.common;

import java.util.List;
import java.util.Map;

/**
 * 单个检索任务。
 *
 * @param question 当前任务对应的子问题
 * @param collections 当前任务要检索的 collection
 * @param topK 每个 collection 的召回数量
 * @param collectionIntentKeys collection 到意图节点 ID 的映射
 */
public record SearchTask(
        String question,
        List<String> collections,
        int topK,
        Map<String, List<String>> collectionIntentKeys
) {
}
