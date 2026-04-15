package com.ycy.aiapplication.rag.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ycy.aiapplication.framework.context.UserContext;
import com.ycy.aiapplication.knowledge.dao.entity.KnowledgeBaseDO;
import com.ycy.aiapplication.knowledge.dao.mapper.KnowledgeBaseMapper;
import com.ycy.aiapplication.rag.core.intent.common.IntentNode;
import com.ycy.aiapplication.rag.core.intent.common.enums.IntentKind;
import com.ycy.aiapplication.rag.core.intent.management.IntentTreeCacheManager;
import com.ycy.aiapplication.rag.dao.entity.IntentNodeDO;
import com.ycy.aiapplication.rag.dao.mapper.IntentNodeMapper;
import com.ycy.aiapplication.rag.service.IntentNodeManageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 知识库意图节点管理服务实现。
 * 仅面向当前二层分类器暴露启用的知识库节点，不包含系统节点。
 */
@Service
@RequiredArgsConstructor
public class IntentNodeManageServiceImpl implements IntentNodeManageService {

    private final IntentNodeMapper intentNodeMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final IntentTreeCacheManager intentTreeCacheManager;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<IntentNode> listEnabledIntentNodes() {
        syncNodesFromKnowledgeBase();
        Map<String, KnowledgeBaseDO> knowledgeBaseMap = knowledgeBaseMapper.selectList(
                        Wrappers.lambdaQuery(KnowledgeBaseDO.class)
                                .eq(KnowledgeBaseDO::getDeleted, 0)
                ).stream()
                .collect(Collectors.toMap(KnowledgeBaseDO::getId, Function.identity(), (left, right) -> left));
        List<IntentNodeDO> nodes = intentNodeMapper.selectList(
                Wrappers.lambdaQuery(IntentNodeDO.class)
                        .eq(IntentNodeDO::getDeleted, 0)
                        .eq(IntentNodeDO::getEnabled, 1)
                        .isNotNull(IntentNodeDO::getKbId)
                        .orderByDesc(IntentNodeDO::getUpdateTime)
        );
        if (CollUtil.isEmpty(nodes)) {
            return List.of();
        }
        return nodes.stream()
                .map(each -> toIntentNode(each, knowledgeBaseMap.get(each.getKbId())))
                .toList();
    }

    @Override
    public void refreshIntentNodeCache() {
        intentTreeCacheManager.clearKnowledgeNodesCache();
        List<IntentNode> nodes = listEnabledIntentNodes();
        if (CollUtil.isNotEmpty(nodes)) {
            intentTreeCacheManager.saveKnowledgeNodesToCache(nodes);
        }
    }

    /**
     * 同步知识库表与知识库意图节点表。
     * 仅为从未创建过节点的新知识库自动补建，避免手动删除后被同步逻辑重新创建。
     */
    private void syncNodesFromKnowledgeBase() {
        List<KnowledgeBaseDO> knowledgeBases = knowledgeBaseMapper.selectList(
                Wrappers.lambdaQuery(KnowledgeBaseDO.class)
                        .eq(KnowledgeBaseDO::getDeleted, 0)
        );
        List<IntentNodeDO> existingKbNodes = intentNodeMapper.selectList(
                Wrappers.lambdaQuery(IntentNodeDO.class)
                        .isNotNull(IntentNodeDO::getKbId)
        );

        Map<String, IntentNodeDO> activeNodeByKbId = existingKbNodes.stream()
                .filter(each -> Objects.equals(each.getDeleted(), 0))
                .collect(Collectors.toMap(IntentNodeDO::getKbId, Function.identity(), (left, right) -> left));
        Set<String> historyKbIds = existingKbNodes.stream()
                .map(IntentNodeDO::getKbId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        Set<String> activeKbIds = new HashSet<>(knowledgeBases.stream()
                .map(KnowledgeBaseDO::getId)
                .collect(Collectors.toSet()));

        for (KnowledgeBaseDO each : knowledgeBases) {
            IntentNodeDO existing = activeNodeByKbId.get(each.getId());
            if (existing == null) {
                if (!historyKbIds.contains(each.getId())) {
                    intentNodeMapper.insert(IntentNodeDO.builder()
                            .kbId(each.getId())
                            .name(each.getName())
                            .description(buildDefaultDescription(each))
                            .enabled(1)
                            .createdBy(currentOperator())
                            .updatedBy(currentOperator())
                            .deleted(0)
                            .build());
                }
                continue;
            }

            boolean changed = false;
            if (!StringUtils.hasText(existing.getName())) {
                existing.setName(each.getName());
                changed = true;
            }
            if (!StringUtils.hasText(existing.getDescription())) {
                existing.setDescription(buildDefaultDescription(each));
                changed = true;
            }
            if (changed) {
                existing.setUpdatedBy(currentOperator());
                intentNodeMapper.updateById(existing);
            }
        }

        for (IntentNodeDO each : existingKbNodes) {
            if (Objects.equals(each.getDeleted(), 1) || activeKbIds.contains(each.getKbId())) {
                continue;
            }
            each.setDeleted(1);
            each.setUpdatedBy(currentOperator());
            intentNodeMapper.updateById(each);
        }
    }

    private IntentNode toIntentNode(IntentNodeDO source, KnowledgeBaseDO knowledgeBase) {
        return IntentNode.builder()
                .id(source.getId())
                .kbId(source.getKbId())
                .name(source.getName())
                .description(source.getDescription())
                .examples(parseExamples(source.getExamples()))
                .kind(IntentKind.KB)
                .collectionName(knowledgeBase == null ? null : knowledgeBase.getCollectionName())
                .promptSnippet(source.getPromptSnippet())
                .promptTemplate(source.getPromptTemplate())
                .build();
    }

    private List<String> parseExamples(String examples) {
        if (!StringUtils.hasText(examples)) {
            return Collections.emptyList();
        }
        return Arrays.stream(examples.split("\\r?\\n"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    private String buildDefaultDescription(KnowledgeBaseDO knowledgeBase) {
        return "知识库《" + knowledgeBase.getName() + "》相关问题、文档和业务问答";
    }

    private String currentOperator() {
        if (StringUtils.hasText(UserContext.getNickName())) {
            return UserContext.getNickName();
        }
        Long userId = UserContext.getId();
        return userId == null ? "system" : String.valueOf(userId);
    }
}
