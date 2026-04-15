package com.ycy.aiapplication.rag.control.controller;

import com.ycy.aiapplication.framework.idempotent.annotations.IdempotentSubmit;
import com.ycy.aiapplication.framework.web.Result;
import com.ycy.aiapplication.framework.web.Results;
import com.ycy.aiapplication.rag.control.request.IntentNodeBatchRequest;
import com.ycy.aiapplication.rag.control.request.IntentNodeCreateRequest;
import com.ycy.aiapplication.rag.control.request.IntentNodeUpdateRequest;
import com.ycy.aiapplication.rag.control.vo.IntentNodeVO;
import com.ycy.aiapplication.rag.service.IntentNodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 二层意图节点控制器。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/rag/intent-node")
public class IntentNodeController {

    private final IntentNodeService intentNodeService;

    /**
     * 查询全部二层意图节点。
     */
    @GetMapping
    public Result<List<IntentNodeVO>> listAllNodes() {
        return Results.success(intentNodeService.listAllNodes());
    }

    /**
     * 创建二层意图节点。
     */
    @PostMapping
    @IdempotentSubmit(message = "尝试次数过多，请稍后再试")
    public Result<String> createNode(@RequestBody IntentNodeCreateRequest requestParam) {
        return Results.success(intentNodeService.createNode(requestParam));
    }

    /**
     * 更新二层意图节点。
     */
    @PutMapping("/{id}")
    @IdempotentSubmit(message = "尝试次数过多，请稍后再试")
    public Result<Void> updateNode(@PathVariable String id,
                                   @RequestBody IntentNodeUpdateRequest requestParam) {
        intentNodeService.updateNode(id, requestParam);
        return Results.success();
    }

    /**
     * 删除二层意图节点。
     */
    @DeleteMapping("/{id}")
    @IdempotentSubmit(message = "尝试次数过多，请稍后再试")
    public Result<Void> deleteNode(@PathVariable String id) {
        intentNodeService.deleteNode(id);
        return Results.success();
    }

    /**
     * 批量启用节点。
     */
    @PostMapping("/batch/enable")
    @IdempotentSubmit(message = "尝试次数过多，请稍后再试")
    public Result<Void> batchEnable(@RequestBody IntentNodeBatchRequest requestParam) {
        intentNodeService.batchEnableNodes(requestParam.getIds());
        return Results.success();
    }

    /**
     * 批量停用节点。
     */
    @PostMapping("/batch/disable")
    @IdempotentSubmit(message = "尝试次数过多，请稍后再试")
    public Result<Void> batchDisable(@RequestBody IntentNodeBatchRequest requestParam) {
        intentNodeService.batchDisableNodes(requestParam.getIds());
        return Results.success();
    }

    /**
     * 批量删除节点。
     */
    @PostMapping("/batch/delete")
    @IdempotentSubmit(message = "尝试次数过多，请稍后再试")
    public Result<Void> batchDelete(@RequestBody IntentNodeBatchRequest requestParam) {
        intentNodeService.batchDeleteNodes(requestParam.getIds());
        return Results.success();
    }
}
