package com.ycy.aiapplication.knowledge.control.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ycy.aiapplication.framework.idempotent.annotations.IdempotentSubmit;
import com.ycy.aiapplication.framework.web.Result;
import com.ycy.aiapplication.framework.web.Results;

import com.ycy.aiapplication.infrastructure.ai.config.AIModelProperties;
import com.ycy.aiapplication.knowledge.control.request.base.KnowledgeBaseCreateRequest;
import com.ycy.aiapplication.knowledge.control.request.base.KnowledgeBasePageRequest;
import com.ycy.aiapplication.knowledge.control.request.base.KnowledgeBaseUpdateRequest;
import com.ycy.aiapplication.knowledge.control.vo.KnowledgeBaseVO;
import com.ycy.aiapplication.knowledge.service.KnowledgeBaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 知识库控制层。
 */
@Tag(name = "知识库管理")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/knowledge")
@SecurityRequirement(name = "Authorization")
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;
    private final AIModelProperties aiModelProperties;

    @Operation(summary = "创建知识库")
    @PostMapping("/knowledge-base")
    @IdempotentSubmit(message = "尝试次数过多，请稍后再试")
    public Result<String> createKnowledgeBase(@RequestBody KnowledgeBaseCreateRequest requestParam) {
        return Results.success(knowledgeBaseService.create(requestParam));
    }

    @Operation(summary = "更新知识库")
    @PutMapping("/knowledge-base")
    @IdempotentSubmit(message = "尝试次数过多，请稍后再试")
    public Result<Void> updateKnowledgeBase(@RequestBody KnowledgeBaseUpdateRequest requestParam) {
        knowledgeBaseService.update(requestParam);
        return Results.success();
    }

    @Operation(summary = "重命名知识库")
    @PutMapping("/knowledge-base/rename")
    @IdempotentSubmit(message = "尝试次数过多，请稍后再试")
    public Result<Void> renameKnowledgeBase(@RequestBody KnowledgeBaseUpdateRequest requestParam) {
        knowledgeBaseService.rename(requestParam);
        return Results.success();
    }

    @Operation(summary = "删除知识库")
    @DeleteMapping("/knowledge-base/{kbId}")
    @IdempotentSubmit(message = "尝试次数过多，请稍后再试")
    public Result<Void> deleteKnowledgeBase(@PathVariable("kbId") String kbId) {
        knowledgeBaseService.delete(kbId);
        return Results.success();
    }

    @Operation(summary = "查询知识库详情")
    @GetMapping("/knowledge-base/{kbId}")
    @IdempotentSubmit(message = "尝试次数过多，请稍后再试")
    public Result<KnowledgeBaseVO> queryKnowledgeBase(@PathVariable("kbId") String kbId) {
        return Results.success(knowledgeBaseService.queryById(kbId));
    }

    @Operation(summary = "分页查询知识库")
    @GetMapping("/knowledge-base")
    @IdempotentSubmit(message = "尝试次数过多，请稍后再试")
    public Result<IPage<KnowledgeBaseVO>> pageQuery(KnowledgeBasePageRequest requestParam) {
        return Results.success(knowledgeBaseService.pageQuery(requestParam));
    }

    @Operation(summary = "查询可选择的向量化模型")
    @GetMapping("/embeddingModel")
    @IdempotentSubmit(message = "尝试次数过多，请稍后再试")
    public Result<Map<String,String>> embeddingModelConfigGet(){
        return Results.success(aiModelProperties.getEmbedding().getModels());
    }
}
