package com.ycy.aiapplication.rag.control.request;

import lombok.Data;

import java.util.List;

/**
 * 意图节点批量操作请求。
 */
@Data
public class IntentNodeBatchRequest {

    /**
     * 意图节点 ID 列表。
     */
    private List<String> ids;
}
