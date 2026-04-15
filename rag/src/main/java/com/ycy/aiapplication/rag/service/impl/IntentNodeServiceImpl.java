package com.ycy.aiapplication.rag.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ycy.aiapplication.framework.context.UserContext;
import com.ycy.aiapplication.framework.exception.ClientException;
import com.ycy.aiapplication.framework.exception.ServiceException;
import com.ycy.aiapplication.knowledge.dao.entity.KnowledgeBaseDO;
import com.ycy.aiapplication.knowledge.dao.mapper.KnowledgeBaseMapper;
import com.ycy.aiapplication.rag.control.request.IntentNodeCreateRequest;
import com.ycy.aiapplication.rag.control.request.IntentNodeUpdateRequest;
import com.ycy.aiapplication.rag.control.vo.IntentNodeVO;
import com.ycy.aiapplication.rag.core.intent.common.enums.IntentKind;
import com.ycy.aiapplication.rag.core.intent.management.IntentTreeCacheManager;
import com.ycy.aiapplication.rag.dao.entity.IntentNodeDO;
import com.ycy.aiapplication.rag.dao.mapper.IntentNodeMapper;
import com.ycy.aiapplication.rag.service.IntentNodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 二层意图节点管理服务实现。
 */
@Service
@RequiredArgsConstructor
public class IntentNodeServiceImpl implements IntentNodeService {

    private final IntentNodeMapper intentNodeMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final IntentTreeCacheManager intentTreeCacheManager;

    @Override
    public List<IntentNodeVO> listAllNodes() {
        List<IntentNodeDO> nodes = intentNodeMapper.selectList(
                Wrappers.lambdaQuery(IntentNodeDO.class)
                        .eq(IntentNodeDO::getDeleted, 0)
                        .orderByDesc(IntentNodeDO::getUpdateTime)
                        .orderByDesc(IntentNodeDO::getCreateTime)
        );
        if (CollUtil.isEmpty(nodes)) {
            return List.of();
        }

        Map<String, KnowledgeBaseDO> knowledgeBaseMap = listKnowledgeBaseMap(extractKbIds(nodes));
        return nodes.stream()
                .map(each -> toVO(each, knowledgeBaseMap))
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createNode(IntentNodeCreateRequest requestParam) {
        IntentKind kind = resolveKind(requestParam.getKind(), false);
        String normalizedName = normalizeRequiredName(requestParam.getName());
        String normalizedKbId = normalizeKbId(kind, requestParam.getKbId());
        validateCreateRequest(kind, normalizedKbId, normalizedName);

        String operator = currentOperator();
        IntentNodeDO node = IntentNodeDO.builder()
                .kbId(normalizedKbId)
                .name(normalizedName)
                .description(StrUtil.emptyToNull(StrUtil.trim(requestParam.getDescription())))
                .examples(joinExamples(requestParam.getExamples()))
                .promptSnippet(StrUtil.emptyToNull(requestParam.getPromptSnippet()))
                .promptTemplate(StrUtil.emptyToNull(requestParam.getPromptTemplate()))
                .enabled(normalizeEnabled(requestParam.getEnabled()))
                .createdBy(operator)
                .updatedBy(operator)
                .deleted(0)
                .build();
        intentNodeMapper.insert(node);
        clearKnowledgeNodeCache();
        return node.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateNode(String id, IntentNodeUpdateRequest requestParam) {
        IntentNodeDO existing = getActiveNode(id);
        IntentKind currentKind = inferKind(existing);
        IntentKind targetKind = resolveKind(requestParam.getKind(), true);
        IntentKind actualKind = targetKind == null ? currentKind : targetKind;

        String targetName = requestParam.getName() != null
                ? normalizeRequiredName(requestParam.getName())
                : existing.getName();
        String targetKbId = actualKind == IntentKind.KB
                ? normalizeKbId(actualKind, requestParam.getKbId() != null ? requestParam.getKbId() : existing.getKbId())
                : null;

        validateUpdateRequest(existing.getId(), actualKind, targetKbId, targetName);

        existing.setKbId(targetKbId);
        existing.setName(targetName);
        if (requestParam.getDescription() != null) {
            existing.setDescription(StrUtil.emptyToNull(StrUtil.trim(requestParam.getDescription())));
        }
        if (requestParam.getExamples() != null) {
            existing.setExamples(joinExamples(requestParam.getExamples()));
        }
        if (requestParam.getPromptSnippet() != null) {
            existing.setPromptSnippet(StrUtil.emptyToNull(requestParam.getPromptSnippet()));
        }
        if (requestParam.getPromptTemplate() != null) {
            existing.setPromptTemplate(StrUtil.emptyToNull(requestParam.getPromptTemplate()));
        }
        if (requestParam.getEnabled() != null) {
            existing.setEnabled(normalizeEnabled(requestParam.getEnabled()));
        }
        existing.setUpdatedBy(currentOperator());
        intentNodeMapper.updateById(existing);
        clearKnowledgeNodeCache();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteNode(String id) {
        IntentNodeDO existing = getActiveNode(id);
        intentNodeMapper.deleteById(existing.getId());
        clearKnowledgeNodeCache();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchEnableNodes(List<String> ids) {
        batchUpdateEnabled(ids, 1);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchDisableNodes(List<String> ids) {
        batchUpdateEnabled(ids, 0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchDeleteNodes(List<String> ids) {
        List<IntentNodeDO> nodes = listAndValidateTargetNodes(ids);
        nodes.forEach(each -> intentNodeMapper.deleteById(each.getId()));
        clearKnowledgeNodeCache();
    }

    private void batchUpdateEnabled(List<String> ids, int enabled) {
        List<IntentNodeDO> nodes = listAndValidateTargetNodes(ids);
        String operator = currentOperator();
        nodes.forEach(each -> {
            each.setEnabled(enabled);
            each.setUpdatedBy(operator);
            intentNodeMapper.updateById(each);
        });
        clearKnowledgeNodeCache();
    }

    private void validateCreateRequest(IntentKind kind, String kbId, String name) {
        if (kind == IntentKind.KB) {
            ensureKnowledgeBaseExists(kbId);
            ensureUniqueKbBinding(kbId, null);
            return;
        }
        ensureUniqueSystemNodeName(name, null);
    }

    private void validateUpdateRequest(String id, IntentKind kind, String kbId, String name) {
        if (kind == IntentKind.KB) {
            ensureKnowledgeBaseExists(kbId);
            ensureUniqueKbBinding(kbId, id);
            return;
        }
        ensureUniqueSystemNodeName(name, id);
    }

    private void ensureKnowledgeBaseExists(String kbId) {
        if (!StringUtils.hasText(kbId)) {
            throw new ClientException("知识库节点必须绑定知识库");
        }
        KnowledgeBaseDO knowledgeBase = knowledgeBaseMapper.selectOne(
                Wrappers.lambdaQuery(KnowledgeBaseDO.class)
                        .eq(KnowledgeBaseDO::getId, kbId)
                        .eq(KnowledgeBaseDO::getDeleted, 0)
                        .last("limit 1")
        );
        if (knowledgeBase == null) {
            throw new ClientException("关联知识库不存在: " + kbId);
        }
    }

    private void ensureUniqueKbBinding(String kbId, String excludeId) {
        List<IntentNodeDO> existingNodes = intentNodeMapper.selectList(
                Wrappers.lambdaQuery(IntentNodeDO.class)
                        .eq(IntentNodeDO::getDeleted, 0)
                        .eq(IntentNodeDO::getKbId, kbId)
        );
        boolean duplicated = existingNodes.stream()
                .anyMatch(each -> !Objects.equals(each.getId(), excludeId));
        if (duplicated) {
            throw new ClientException("该知识库已存在对应意图节点: " + kbId);
        }
    }

    private void ensureUniqueSystemNodeName(String name, String excludeId) {
        List<IntentNodeDO> existingNodes = intentNodeMapper.selectList(
                Wrappers.lambdaQuery(IntentNodeDO.class)
                        .eq(IntentNodeDO::getDeleted, 0)
                        .isNull(IntentNodeDO::getKbId)
                        .eq(IntentNodeDO::getName, name)
        );
        boolean duplicated = existingNodes.stream()
                .anyMatch(each -> !Objects.equals(each.getId(), excludeId));
        if (duplicated) {
            throw new ClientException("系统节点名称已存在: " + name);
        }
    }

    private IntentNodeDO getActiveNode(String id) {
        IntentNodeDO node = intentNodeMapper.selectOne(
                Wrappers.lambdaQuery(IntentNodeDO.class)
                        .eq(IntentNodeDO::getId, id)
                        .eq(IntentNodeDO::getDeleted, 0)
                        .last("limit 1")
        );
        if (node == null) {
            throw new ServiceException("意图节点不存在或已删除: " + id);
        }
        return node;
    }

    private List<IntentNodeDO> listAndValidateTargetNodes(List<String> ids) {
        Assert.notEmpty(ids, () -> new ClientException("请至少选择一个意图节点"));
        List<String> normalizedIds = ids.stream()
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        Assert.notEmpty(normalizedIds, () -> new ClientException("意图节点 ID 不能为空"));

        List<IntentNodeDO> nodes = intentNodeMapper.selectList(
                Wrappers.lambdaQuery(IntentNodeDO.class)
                        .in(IntentNodeDO::getId, normalizedIds)
                        .eq(IntentNodeDO::getDeleted, 0)
        );
        if (nodes.size() != normalizedIds.size()) {
            Set<String> existingIds = nodes.stream().map(IntentNodeDO::getId).collect(Collectors.toSet());
            List<String> missingIds = normalizedIds.stream()
                    .filter(each -> !existingIds.contains(each))
                    .toList();
            throw new ClientException("部分意图节点不存在或已删除: " + missingIds);
        }
        return nodes;
    }

    private Map<String, KnowledgeBaseDO> listKnowledgeBaseMap(Set<String> kbIds) {
        if (CollUtil.isEmpty(kbIds)) {
            return Collections.emptyMap();
        }
        return knowledgeBaseMapper.selectList(
                        Wrappers.lambdaQuery(KnowledgeBaseDO.class)
                                .in(KnowledgeBaseDO::getId, kbIds)
                                .eq(KnowledgeBaseDO::getDeleted, 0)
                ).stream()
                .collect(Collectors.toMap(KnowledgeBaseDO::getId, Function.identity(), (left, right) -> left));
    }

    private Set<String> extractKbIds(List<IntentNodeDO> nodes) {
        return nodes.stream()
                .map(IntentNodeDO::getKbId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
    }

    private IntentNodeVO toVO(IntentNodeDO source, Map<String, KnowledgeBaseDO> knowledgeBaseMap) {
        IntentNodeVO result = new IntentNodeVO();
        result.setId(source.getId());
        result.setKind(inferKind(source).getCode());
        result.setKbId(source.getKbId());
        KnowledgeBaseDO knowledgeBase = StringUtils.hasText(source.getKbId()) ? knowledgeBaseMap.get(source.getKbId()) : null;
        if (knowledgeBase != null) {
            result.setKbName(knowledgeBase.getName());
            result.setCollectionName(knowledgeBase.getCollectionName());
        }
        result.setName(source.getName());
        result.setDescription(source.getDescription());
        result.setExamples(splitExamples(source.getExamples()));
        result.setPromptSnippet(source.getPromptSnippet());
        result.setPromptTemplate(source.getPromptTemplate());
        result.setEnabled(source.getEnabled());
        result.setCreatedBy(source.getCreatedBy());
        result.setUpdatedBy(source.getUpdatedBy());
        result.setCreateTime(source.getCreateTime());
        result.setUpdateTime(source.getUpdateTime());
        return result;
    }

    private IntentKind resolveKind(Integer kindCode, boolean allowNull) {
        if (kindCode == null) {
            if (allowNull) {
                return null;
            }
            throw new ClientException("节点类型不能为空");
        }
        IntentKind kind = IntentKind.fromCode(kindCode);
        if (kind == null || kind == IntentKind.UNKNOWN) {
            throw new ClientException("不支持的节点类型: " + kindCode);
        }
        return kind;
    }

    private IntentKind inferKind(IntentNodeDO node) {
        return StringUtils.hasText(node.getKbId()) ? IntentKind.KB : IntentKind.SYSTEM;
    }

    private String normalizeKbId(IntentKind kind, String kbId) {
        if (kind == IntentKind.SYSTEM) {
            return null;
        }
        String normalized = StrUtil.trim(kbId);
        if (!StringUtils.hasText(normalized)) {
            throw new ClientException("知识库节点必须绑定知识库");
        }
        return normalized;
    }

    private String normalizeRequiredName(String name) {
        String normalized = StrUtil.trim(name);
        if (!StringUtils.hasText(normalized)) {
            throw new ClientException("节点名称不能为空");
        }
        return normalized;
    }

    private Integer normalizeEnabled(Integer enabled) {
        if (enabled == null) {
            return 1;
        }
        if (!Objects.equals(enabled, 0) && !Objects.equals(enabled, 1)) {
            throw new ClientException("enabled 仅支持 0 或 1");
        }
        return enabled;
    }

    private String joinExamples(List<String> examples) {
        if (examples == null) {
            return null;
        }
        List<String> normalized = examples.stream()
                .map(StrUtil::trim)
                .filter(StringUtils::hasText)
                .toList();
        return CollUtil.isEmpty(normalized) ? null : String.join("\n", normalized);
    }

    private List<String> splitExamples(String examples) {
        if (!StringUtils.hasText(examples)) {
            return List.of();
        }
        return Arrays.stream(examples.split("\\r?\\n"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    private String currentOperator() {
        if (StringUtils.hasText(UserContext.getNickName())) {
            return UserContext.getNickName();
        }
        Long userId = UserContext.getId();
        return userId == null ? "system" : String.valueOf(userId);
    }

    private void clearKnowledgeNodeCache() {
        intentTreeCacheManager.clearKnowledgeNodesCache();
    }
}
