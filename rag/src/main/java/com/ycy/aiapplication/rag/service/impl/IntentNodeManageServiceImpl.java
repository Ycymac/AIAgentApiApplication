package com.ycy.aiapplication.rag.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
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
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 知识库意图节点管理服务实现。
 * 作用：
 * 1. 维护知识库表与意图节点表的一一对应关系。
 * 2. 对外提供启用节点列表，供第二层知识库意图识别使用。
 * 3. 在节点变化时统一负责缓存刷新。
 * <p>
 * 执行顺序：
 * 1. 外部调用 {@link #listEnabledIntentNodes()} 获取节点列表。
 * 2. 该方法内部会先调用 {@link #syncNodesFromKnowledgeBase()}，保证节点与知识库同步。
 * 3. 同步完成后查询启用节点，再转换成意图识别使用的内存对象。
 * 4. 若需要主动更新缓存，则调用 {@link #refreshIntentNodeCache()}。
 */
@Service
@RequiredArgsConstructor
public class IntentNodeManageServiceImpl implements IntentNodeManageService {

    private final IntentNodeMapper intentNodeMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final IntentTreeCacheManager intentTreeCacheManager;

    /**
     * 查询当前启用的知识库意图节点。
     *
     * @return 启用状态的知识库意图节点列表
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<IntentNode> listEnabledIntentNodes() {
        // 每次装载节点前先同步，避免知识库表和节点表不一致。
        syncNodesFromKnowledgeBase();
        List<IntentNodeDO> nodes = intentNodeMapper.selectList(
                Wrappers.lambdaQuery(IntentNodeDO.class)
                        .eq(IntentNodeDO::getDeleted, 0)
                        .eq(IntentNodeDO::getEnabled, 1)
                        .orderByDesc(IntentNodeDO::getUpdateTime)
        );
        if (CollUtil.isEmpty(nodes)) {
            return List.of();
        }
        return nodes.stream()
                .map(this::toIntentNode)
                .toList();
    }

    /**
     * 刷新知识库意图节点缓存。
     * 采用“先清理、后重建”的方式，避免旧缓存与新数据混用。
     */
    @Override
    public void refreshIntentNodeCache() {
        intentTreeCacheManager.clearKnowledgeNodesCache();
        List<IntentNode> nodes = listEnabledIntentNodes();
        if (CollUtil.isNotEmpty(nodes)) {
            intentTreeCacheManager.saveKnowledgeNodesToCache(nodes);
        }
    }

    /**
     * 同步知识库表与意图节点表。
     * 规则：
     * 1. 知识库存在但节点不存在时，自动补建节点。
     * 2. 知识库重命名后，同步节点名称。
     * 3. 描述为空时补默认描述，但不覆盖人工维护的描述。
     * 4. 节点绑定的知识库已删除时，将节点逻辑删除。
     */
    private void syncNodesFromKnowledgeBase() {
        List<KnowledgeBaseDO> knowledgeBases = knowledgeBaseMapper.selectList(
                Wrappers.lambdaQuery(KnowledgeBaseDO.class)
                        .eq(KnowledgeBaseDO::getDeleted, 0)
        );
        if (CollUtil.isEmpty(knowledgeBases)) {
            return;
        }

        List<IntentNodeDO> existingNodes = intentNodeMapper.selectList(
                Wrappers.lambdaQuery(IntentNodeDO.class)
                        .eq(IntentNodeDO::getDeleted, 0)
        );
        Map<String, IntentNodeDO> existingByKbId = existingNodes.stream()
                .filter(each -> StringUtils.hasText(each.getKbId()))
                .collect(Collectors.toMap(IntentNodeDO::getKbId, Function.identity(), (left, right) -> left));
        Set<String> activeKbIds = new HashSet<>(knowledgeBases.stream()
                .map(KnowledgeBaseDO::getId)
                .collect(Collectors.toSet()));

        for (KnowledgeBaseDO each : knowledgeBases) {
            IntentNodeDO existing = existingByKbId.get(each.getId());
            if (existing == null) {
                // 一个知识库自动对应一个意图节点，不存在就补建。
                intentNodeMapper.insert(IntentNodeDO.builder()
                        .kbId(each.getId())
                        .name(each.getName())
                        .description(buildDefaultDescription(each))
                        .enabled(1)
                        .createdBy(currentOperator())
                        .updatedBy(currentOperator())
                        .deleted(0)
                        .build());
                continue;
            }

            boolean changed = false;
            if (!StrUtil.equals(existing.getName(), each.getName())) {
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

        for (IntentNodeDO each : existingNodes) {
            if (!StringUtils.hasText(each.getKbId()) || activeKbIds.contains(each.getKbId())) {
                continue;
            }
            // 绑定的知识库已不存在时，逻辑删除该节点，避免脏节点继续参与识别。
            each.setDeleted(1);
            each.setUpdatedBy(currentOperator());
            intentNodeMapper.updateById(each);
        }
    }

    /**
     * 将数据库中的节点记录转换为意图识别使用的内存对象。
     *
     * @param source 数据库中的节点记录
     * @return 内存态的知识库意图节点
     */
    private IntentNode toIntentNode(IntentNodeDO source) {
        return IntentNode.builder()
                .id(source.getId())
                .kbId(source.getKbId())
                .name(source.getName())
                .description(source.getDescription())
                .examples(parseExamples(source.getExamples()))
                .kind(IntentKind.KB)
                .promptSnippet(source.getPromptSnippet())
                .promptTemplate(source.getPromptTemplate())
                .build();
    }

    /**
     * 将数据库中按换行存储的示例问题文本拆分为列表。
     *
     * @param examples 数据库中存储的示例问题文本
     * @return 清洗后的示例问题列表
     */
    private List<String> parseExamples(String examples) {
        if (!StringUtils.hasText(examples)) {
            return Collections.emptyList();
        }
        return Arrays.stream(examples.split("\\r?\\n"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    /**
     * 生成默认的节点描述。
     *
     * @param knowledgeBase 关联的知识库记录
     * @return 默认描述文本
     */
    private String buildDefaultDescription(KnowledgeBaseDO knowledgeBase) {
        return "知识库《" + knowledgeBase.getName() + "》相关问题、文档和业务问答";
    }

    /**
     * 获取当前操作人。
     *
     * @return 优先返回昵称，其次返回用户 ID，最后兜底为 system
     */
    private String currentOperator() {
        if (StringUtils.hasText(UserContext.getNickName())) {
            return UserContext.getNickName();
        }
        Long userId = UserContext.getId();
        return userId == null ? "system" : String.valueOf(userId);
    }
}
