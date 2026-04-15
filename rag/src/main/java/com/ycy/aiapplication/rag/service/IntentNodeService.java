package com.ycy.aiapplication.rag.service;

import com.ycy.aiapplication.rag.control.request.IntentNodeCreateRequest;
import com.ycy.aiapplication.rag.control.request.IntentNodeUpdateRequest;
import com.ycy.aiapplication.rag.control.vo.IntentNodeVO;

import java.util.List;

/**
 * 二层意图节点管理服务。
 */
public interface IntentNodeService {

    /**
     * 查询全部未删除的二层意图节点。
     *
     * @return 节点列表
     */
    List<IntentNodeVO> listAllNodes();

    /**
     * 创建意图节点。
     *
     * @param requestParam 创建参数
     * @return 新建节点 ID
     */
    String createNode(IntentNodeCreateRequest requestParam);

    /**
     * 更新意图节点。
     *
     * @param id           节点 ID
     * @param requestParam 更新参数
     */
    void updateNode(String id, IntentNodeUpdateRequest requestParam);

    /**
     * 删除意图节点。
     *
     * @param id 节点 ID
     */
    void deleteNode(String id);

    /**
     * 批量启用节点。
     *
     * @param ids 节点 ID 列表
     */
    void batchEnableNodes(List<String> ids);

    /**
     * 批量停用节点。
     *
     * @param ids 节点 ID 列表
     */
    void batchDisableNodes(List<String> ids);

    /**
     * 批量删除节点。
     *
     * @param ids 节点 ID 列表
     */
    void batchDeleteNodes(List<String> ids);
}
