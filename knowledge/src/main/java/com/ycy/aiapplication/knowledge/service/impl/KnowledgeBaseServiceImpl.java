package com.ycy.aiapplication.knowledge.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ycy.aiapplication.framework.exception.ClientException;
import com.ycy.aiapplication.framework.exception.ServiceException;

import com.ycy.aiapplication.knowledge.control.request.base.KnowledgeBaseCreateRequest;
import com.ycy.aiapplication.knowledge.control.request.base.KnowledgeBasePageRequest;
import com.ycy.aiapplication.knowledge.control.request.base.KnowledgeBaseUpdateRequest;
import com.ycy.aiapplication.knowledge.control.vo.KnowledgeBaseVO;
import com.ycy.aiapplication.knowledge.dao.entity.KnowledgeBaseDO;
import com.ycy.aiapplication.knowledge.dao.entity.KnowledgeDocumentDO;
import com.ycy.aiapplication.knowledge.dao.mapper.KnowledgeBaseMapper;
import com.ycy.aiapplication.knowledge.dao.mapper.KnowledgeDocumentMapper;
import com.ycy.aiapplication.knowledge.service.KnowledgeBaseService;
import com.ycy.aiapplication.framework.context.UserContext;
import com.ycy.aiapplication.vector.VectorStoreAdmin;
import com.ycy.aiapplication.vector.common.VectorSpaceId;
import com.ycy.aiapplication.vector.common.VectorSpaceSpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final VectorStoreAdmin vectorStoreAdmin;
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(KnowledgeBaseCreateRequest requestParam) {
        if (requestParam == null) {
            throw new ClientException("创建知识库请求不能为空");
        }

        String name = normalizeName(requestParam.getName());
        checkNameUnique(name, null);
        String collectionName=buildCollectionName();
        KnowledgeBaseDO kbDO = KnowledgeBaseDO.builder()
                .name(name)
                .embeddingModel(requestParam.getEmbeddingModel())
                .collectionName(collectionName)
                .createdBy(currentOperator())
                .updatedBy(currentOperator())
                .deleted(0)
                .build();
        knowledgeBaseMapper.insert(kbDO);
        vectorStoreAdmin.ensureVectorSpace( VectorSpaceSpec.builder()
                        .spaceId(VectorSpaceId.builder()
                                .logicalName(collectionName)
                                .build())
                        .remark(requestParam.getName())
                .build());
        return kbDO.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(KnowledgeBaseUpdateRequest requestParam) {
        if (requestParam == null || !StringUtils.hasText(requestParam.getId())) {
            throw new ClientException("知识库ID不能为空");
        }

        KnowledgeBaseDO kbDO = getById(requestParam.getId());
        boolean changed = false;

        if (StringUtils.hasText(requestParam.getName())) {
            String name = normalizeName(requestParam.getName());
            checkNameUnique(name, kbDO.getId());
            kbDO.setName(name);
            changed = true;
        }

        if (StringUtils.hasText(requestParam.getEmbeddingModel())
                && !requestParam.getEmbeddingModel().equals(kbDO.getEmbeddingModel())) {
            if (hasVectorizedDocument(kbDO.getId())) {
                throw new ClientException("知识库已存在完成分块的文档，不允许修改嵌入模型");
            }
            kbDO.setEmbeddingModel(requestParam.getEmbeddingModel());
            changed = true;
        }

        if (!changed) {
            throw new ClientException("更新内容不能为空");
        }

        kbDO.setUpdatedBy(currentOperator());
        knowledgeBaseMapper.updateById(kbDO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rename(KnowledgeBaseUpdateRequest requestParam) {
        if (requestParam == null || !StringUtils.hasText(requestParam.getId())) {
            throw new ClientException("知识库ID不能为空");
        }

        KnowledgeBaseDO kbDO = getById(requestParam.getId());
        String name = normalizeName(requestParam.getName());
        checkNameUnique(name, kbDO.getId());

        kbDO.setName(name);
        kbDO.setUpdatedBy(currentOperator());
        knowledgeBaseMapper.updateById(kbDO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String kbId) {
        KnowledgeBaseDO kbDO = getById(kbId);
        Long docCount = knowledgeDocumentMapper.selectCount(
                Wrappers.lambdaQuery(KnowledgeDocumentDO.class)
                        .eq(KnowledgeDocumentDO::getKbId, kbId)
                        .eq(KnowledgeDocumentDO::getDeleted, 0)
        );
        if (docCount != null && docCount > 0) {
            throw new ClientException("当前知识库下还有文档，请先删除文档");
        }

        if (StringUtils.hasText(kbDO.getCollectionName())) {
            vectorStoreAdmin.deleteVectorSpace(VectorSpaceId.builder()
                    .logicalName(kbDO.getCollectionName())
                    .build());
        }

        kbDO.setDeleted(1);
        kbDO.setUpdatedBy(currentOperator());
        knowledgeBaseMapper.updateById(kbDO);
    }

    @Override
    public KnowledgeBaseVO queryById(String kbId) {
        KnowledgeBaseDO kbDO = getById(kbId);
        KnowledgeBaseVO knowledgeBaseVO = BeanUtil.toBean(kbDO, KnowledgeBaseVO.class);
        knowledgeBaseVO.setDocumentCount(queryDocumentCount(kbId));
        return knowledgeBaseVO;
    }

    @Override
    public IPage<KnowledgeBaseVO> pageQuery(KnowledgeBasePageRequest requestParam) {
        if (requestParam == null) {
            requestParam = new KnowledgeBasePageRequest();
        }

        String queryName = requestParam.getName();
        if (queryName != null) {
            queryName = queryName.trim();
        }

        LambdaQueryWrapper<KnowledgeBaseDO> queryWrapper = Wrappers.lambdaQuery(KnowledgeBaseDO.class)
                .like(StringUtils.hasText(queryName), KnowledgeBaseDO::getName, queryName)
                .eq(KnowledgeBaseDO::getDeleted, 0)
                .orderByDesc(KnowledgeBaseDO::getUpdateTime);

        Page<KnowledgeBaseDO> page = new Page<>(requestParam.getCurrent(), requestParam.getSize());
        IPage<KnowledgeBaseDO> result = knowledgeBaseMapper.selectPage(page, queryWrapper);
        Map<String, Long> documentCountMap = countDocumentsByKbIds(result.getRecords());

        return result.convert(each -> {
            KnowledgeBaseVO knowledgeBaseVO = BeanUtil.toBean(each, KnowledgeBaseVO.class);
            knowledgeBaseVO.setDocumentCount(documentCountMap.getOrDefault(each.getId(), 0L));
            return knowledgeBaseVO;
        });
    }

    private KnowledgeBaseDO getById(String kbId) {
        if (!StringUtils.hasText(kbId)) {
            throw new ClientException("知识库ID不能为空");
        }
        KnowledgeBaseDO kbDO = knowledgeBaseMapper.selectById(kbId);
        if (kbDO == null || Objects.equals(kbDO.getDeleted(), 1)) {
            throw new ClientException("知识库不存在");
        }
        return kbDO;
    }

    /**
     * 校验名称是否合法并去除首尾空格
     * @param name 名字
     * @return 去除首尾空格的名字
     */
    private String normalizeName(String name) {
        String normalizedName = name == null ? null : name.trim();
        if (!StringUtils.hasText(normalizedName)) {
            throw new ClientException("知识库名称不能为空");
        }
        if (normalizedName.matches(".*\\s+.*")) {
            throw new ClientException("知识库名称中间不能包含空格");
        }
        return normalizedName;
    }

    /**
     * 检查名称是否唯一
     * <p>
     * 使用场景：1.创建新知识库 2.修改知识库名称（需要检查是否和除了自己的知识库重名）
     * @param name 当前查询名称
     * @param excludeId  排除的id
     */
    private void checkNameUnique(String name, String excludeId) {
        LambdaQueryWrapper<KnowledgeBaseDO> queryWrapper = Wrappers.lambdaQuery(KnowledgeBaseDO.class)
                .eq(KnowledgeBaseDO::getName, name)
                .eq(KnowledgeBaseDO::getDeleted, 0);
        if (StringUtils.hasText(excludeId)) {
            queryWrapper.ne(KnowledgeBaseDO::getId, excludeId);
        }
        Long count = knowledgeBaseMapper.selectCount(queryWrapper);
        if (count != null && count > 0) {
            throw new ServiceException("知识库名称已存在：" + name);
        }
    }

    private boolean hasVectorizedDocument(String kbId) {
        Long count = knowledgeDocumentMapper.selectCount(
                Wrappers.lambdaQuery(KnowledgeDocumentDO.class)
                        .eq(KnowledgeDocumentDO::getKbId, kbId)
                        .gt(KnowledgeDocumentDO::getChunkCount, 0)
                        .eq(KnowledgeDocumentDO::getDeleted, 0)
        );
        return count != null && count > 0;
    }

    private Long queryDocumentCount(String kbId) {
        Long count = knowledgeDocumentMapper.selectCount(
                Wrappers.lambdaQuery(KnowledgeDocumentDO.class)
                        .eq(KnowledgeDocumentDO::getKbId, kbId)
                        .eq(KnowledgeDocumentDO::getDeleted, 0)
        );
        return count == null ? 0L : count;
    }

    private Map<String, Long> countDocumentsByKbIds(List<KnowledgeBaseDO> knowledgeBases) {
        Map<String, Long> countMap = new HashMap<>();
        if (CollUtil.isEmpty(knowledgeBases)) {
            return countMap;
        }

        List<String> kbIds = knowledgeBases.stream()
                .map(KnowledgeBaseDO::getId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
        if (CollUtil.isEmpty(kbIds)) {
            return countMap;
        }

        List<Map<String, Object>> rows = knowledgeDocumentMapper.selectMaps(
                Wrappers.query(KnowledgeDocumentDO.class)
                        .select("kb_id AS kbId", "COUNT(1) AS docCount")
                        .in("kb_id", kbIds)
                        .eq("deleted", 0)
                        .groupBy("kb_id")
        );
        for (Map<String, Object> row : rows) {
            Object kbIdValue = row.get("kbId");
            if (kbIdValue == null) {
                continue;
            }
            Object docCountValue = row.get("docCount");
            Long docCount = docCountValue instanceof Number
                    ? ((Number) docCountValue).longValue()
                    : docCountValue == null ? 0L : Long.parseLong(docCountValue.toString());
            countMap.put(kbIdValue.toString(), docCount);
        }
        return countMap;
    }

    private String buildCollectionName() {
        return "kb_" + IdUtil.getSnowflakeNextIdStr();
    }

    /**
     * 获取当前操作者名字
     */
    private String currentOperator() {
        if (StringUtils.hasText(UserContext.getNickName())) {
            return UserContext.getNickName();
        }
        Long userId = UserContext.getId();
        return userId == null ? "system" : String.valueOf(userId);
    }
}
