package com.ycy.aiapplication.knowledge.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ycy.aiapplication.chunk.VectorChunk;
import com.ycy.aiapplication.framework.context.UserContext;
import com.ycy.aiapplication.framework.exception.ClientException;
import com.ycy.aiapplication.framework.exception.ServiceException;
import com.ycy.aiapplication.infrastructure.ai.embedding.EmbeddingService;
import com.ycy.aiapplication.knowledge.control.request.chunk.KnowledgeChunkBatchRequest;
import com.ycy.aiapplication.knowledge.control.request.chunk.KnowledgeChunkPageRequest;
import com.ycy.aiapplication.knowledge.control.vo.KnowledgeChunkVO;
import com.ycy.aiapplication.knowledge.dao.entity.KnowledgeBaseDO;
import com.ycy.aiapplication.knowledge.dao.entity.KnowledgeChunkDO;
import com.ycy.aiapplication.knowledge.dao.entity.KnowledgeDocumentDO;
import com.ycy.aiapplication.knowledge.dao.mapper.KnowledgeBaseMapper;
import com.ycy.aiapplication.knowledge.dao.mapper.KnowledgeChunkDOMapper;
import com.ycy.aiapplication.knowledge.dao.mapper.KnowledgeDocumentMapper;
import com.ycy.aiapplication.knowledge.service.KnowledgeChunkService;
import com.ycy.aiapplication.vector.VectorStoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeChunkServiceImpl implements KnowledgeChunkService {

    private final KnowledgeChunkDOMapper knowledgeChunkDOMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final ObjectProvider<EmbeddingService> embeddingServiceProvider;
    private final VectorStoreService vectorStoreService;

    @Override
    public Boolean existsByDocId(String docId) {
        if (!StringUtils.hasText(docId)) {
            return false;
        }
        return knowledgeChunkDOMapper.exists(
                Wrappers.lambdaQuery(KnowledgeChunkDO.class).eq(KnowledgeChunkDO::getDocId, docId)
        );
    }

    @Override
    public IPage<KnowledgeChunkVO> pageQuery(String docId, KnowledgeChunkPageRequest requestParam) {
        getDocument(docId);
        KnowledgeChunkPageRequest finalRequest = requestParam == null ? new KnowledgeChunkPageRequest() : requestParam;

        LambdaQueryWrapper<KnowledgeChunkDO> queryWrapper = Wrappers.lambdaQuery(KnowledgeChunkDO.class)
                .eq(KnowledgeChunkDO::getDocId, docId)
                .eq(finalRequest.getEnabled() != null, KnowledgeChunkDO::getEnabled, finalRequest.getEnabled())
                .orderByAsc(KnowledgeChunkDO::getChunkIndex);

        long current = finalRequest.getCurrent() <= 0 ? 1L : finalRequest.getCurrent();
        long size = finalRequest.getSize() <= 0 ? 10L : finalRequest.getSize();
        Page<KnowledgeChunkDO> page = new Page<>(current, size);
        return knowledgeChunkDOMapper.selectPage(page, queryWrapper).convert(this::toChunkVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String docId, String chunkId) {
        KnowledgeDocumentDO documentDO = getDocument(docId);
        //使用这个方法校验Chunk是否存在
        getChunk(docId, chunkId);

        knowledgeChunkDOMapper.deleteById(chunkId);
        updateDocumentChunkCount(docId);

        KnowledgeBaseDO kbDO = getKnowledgeBase(documentDO.getKbId());
        deleteChunkVectorQuietly(kbDO.getCollectionName(), chunkId);
        log.info("删除分块成功, kbId={}, docId={}, chunkId={}", documentDO.getKbId(), docId, chunkId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void enableChunk(String docId, String chunkId, boolean enabled) {
        KnowledgeDocumentDO documentDO = getDocument(docId);
        validateDocumentEnabledForChunkEnable(documentDO, enabled);

        KnowledgeChunkDO chunkDO = getChunk(docId, chunkId);
        int enabledValue = enabled ? 1 : 0;
        if (Objects.equals(chunkDO.getEnabled(), enabledValue)) {
            return;
        }

        chunkDO.setEnabled(enabledValue);
        chunkDO.setUpdatedBy(currentOperator());
        knowledgeChunkDOMapper.updateById(chunkDO);

        KnowledgeBaseDO kbDO = getKnowledgeBase(documentDO.getKbId());
        if (enabled) {
            syncChunkToVector(kbDO, documentDO, chunkDO);
        } else {
            deleteChunkVectorQuietly(kbDO.getCollectionName(), chunkId);
        }
        log.info("{} knowledge chunk success, kbId={}, docId={}, chunkId={}",
                enabled ? "Enable" : "Disable", documentDO.getKbId(), docId, chunkId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchEnable(String docId, KnowledgeChunkBatchRequest requestParam) {
        batchUpdateEnabled(docId, requestParam, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchDisable(String docId, KnowledgeChunkBatchRequest requestParam) {
        batchUpdateEnabled(docId, requestParam, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rebuildByDocId(String docId) {
        doRebuildByDocId(docId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateEnabledByDocId(String docId, boolean enabled) {
        getDocument(docId);
        knowledgeChunkDOMapper.update(
                null,
                Wrappers.lambdaUpdate(KnowledgeChunkDO.class)
                        .eq(KnowledgeChunkDO::getDocId, docId)
                        .set(KnowledgeChunkDO::getEnabled, enabled ? 1 : 0)
                        .set(KnowledgeChunkDO::getUpdatedBy, currentOperator())
        );
    }

    @Override
    public List<KnowledgeChunkVO> listByDocId(String docId) {
        getDocument(docId);
        return knowledgeChunkDOMapper.selectList(
                        Wrappers.lambdaQuery(KnowledgeChunkDO.class)
                                .eq(KnowledgeChunkDO::getDocId, docId)
                                .orderByAsc(KnowledgeChunkDO::getChunkIndex)
                ).stream()
                .map(this::toChunkVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByDocId(String docId) {
        if (!StringUtils.hasText(docId)) {
            return;
        }
        knowledgeChunkDOMapper.delete(
                Wrappers.lambdaQuery(KnowledgeChunkDO.class).eq(KnowledgeChunkDO::getDocId, docId)
        );
    }

    private void batchUpdateEnabled(String docId, KnowledgeChunkBatchRequest requestParam, boolean enabled) {
        KnowledgeDocumentDO documentDO = getDocument(docId);
        validateDocumentEnabledForChunkEnable(documentDO, enabled);

        List<KnowledgeChunkDO> chunks = resolveTargetChunks(docId, requestParam);
        if (CollectionUtils.isEmpty(chunks)) {
            return;
        }

        int enabledValue = enabled ? 1 : 0;
        String operator = currentOperator();
        List<String> changedChunkIds = new ArrayList<>();

        for (KnowledgeChunkDO chunk : chunks) {
            if (Objects.equals(chunk.getEnabled(), enabledValue)) {
                continue;
            }
            chunk.setEnabled(enabledValue);
            chunk.setUpdatedBy(operator);
            knowledgeChunkDOMapper.updateById(chunk);
            changedChunkIds.add(chunk.getId());
        }

        if (CollectionUtils.isEmpty(changedChunkIds)) {
            return;
        }

        KnowledgeBaseDO kbDO = getKnowledgeBase(documentDO.getKbId());
        if (enabled) {
            doRebuildByDocId(docId);
        } else {
            for (String chunkId : changedChunkIds) {
                deleteChunkVectorQuietly(kbDO.getCollectionName(), chunkId);
            }
        }
        log.info(" {} 知识库分块批量更新成功, kbId={}, docId={}, count={}",
                enabled ? "已生效" : "已失效", documentDO.getKbId(), docId, changedChunkIds.size());
    }

    private void doRebuildByDocId(String docId) {
        KnowledgeDocumentDO documentDO = getDocument(docId);
        KnowledgeBaseDO kbDO = getKnowledgeBase(documentDO.getKbId());
        String collectionName = kbDO.getCollectionName();

        deleteDocumentVectorsQuietly(collectionName, docId);

        List<KnowledgeChunkDO> enabledChunks = knowledgeChunkDOMapper.selectList(
                Wrappers.lambdaQuery(KnowledgeChunkDO.class)
                        .eq(KnowledgeChunkDO::getDocId, docId)
                        .eq(KnowledgeChunkDO::getEnabled, 1)
                        .orderByAsc(KnowledgeChunkDO::getChunkIndex)
        );
        if (CollectionUtils.isEmpty(enabledChunks)) {
            log.info("没有生效分块，跳过文本分块重建, kbId={}, docId={}",
                    documentDO.getKbId(), docId);
            return;
        }

        EmbeddingService embeddingService = getEmbeddingService();
        List<String> texts = enabledChunks.stream().map(KnowledgeChunkDO::getContent).toList();
        List<List<Float>> embeddings = embeddingService.embedBatch(texts, kbDO.getEmbeddingModel());
        if (CollectionUtils.isEmpty(embeddings) || embeddings.size() != enabledChunks.size()) {
            throw new ServiceException("向量化大小和分块设定大小不匹配");
        }

        List<VectorChunk> chunks = new ArrayList<>(enabledChunks.size());
        for (int i = 0; i < enabledChunks.size(); i++) {
            KnowledgeChunkDO chunkDO = enabledChunks.get(i);
            chunks.add(VectorChunk.builder()
                    .chunkId(chunkDO.getId())
                    .index(chunkDO.getChunkIndex())
                    .content(chunkDO.getContent())
                    .embedding(toPrimitiveArray(embeddings.get(i)))
                    .build());
        }
        vectorStoreService.indexDocumentChunks(collectionName, docId, chunks);
        log.info("重构分块向量成功, kbId={}, docId={}, count={}",
                documentDO.getKbId(), docId, enabledChunks.size());
    }

    private List<KnowledgeChunkDO> resolveTargetChunks(String docId, KnowledgeChunkBatchRequest requestParam) {
        if (requestParam == null || CollectionUtils.isEmpty(requestParam.getChunkIds())) {
            return knowledgeChunkDOMapper.selectList(
                    Wrappers.lambdaQuery(KnowledgeChunkDO.class).eq(KnowledgeChunkDO::getDocId, docId)
            );
        }

        List<KnowledgeChunkDO> chunks = knowledgeChunkDOMapper.selectBatchIds(requestParam.getChunkIds());
        if (CollectionUtils.isEmpty(chunks)) {
            return List.of();
        }
        for (KnowledgeChunkDO chunk : chunks) {
            if (!Objects.equals(chunk.getDocId(), docId)) {
                throw new ClientException("分块不属于指定文档: " + chunk.getId());
            }
        }
        return chunks;
    }

    private void validateDocumentEnabledForChunkEnable(KnowledgeDocumentDO documentDO, boolean enableChunk) {
        if (!enableChunk) {
            return;
        }
        if (!Objects.equals(documentDO.getEnabled(), 1)) {
            throw new ClientException("文档设定为失效, 不能生效分块");
        }
    }

    private void syncChunkToVector(KnowledgeBaseDO kbDO, KnowledgeDocumentDO documentDO, KnowledgeChunkDO chunkDO) {
        EmbeddingService embeddingService = getEmbeddingService();
        List<Float> embedding = embeddingService.embed(chunkDO.getContent(), kbDO.getEmbeddingModel());
        vectorStoreService.indexDocumentChunks(
                kbDO.getCollectionName(),
                documentDO.getId(),
                List.of(VectorChunk.builder()
                        .chunkId(chunkDO.getId())
                        .index(chunkDO.getChunkIndex())
                        .content(chunkDO.getContent())
                        .embedding(toPrimitiveArray(embedding))
                        .build())
        );
    }

    private void deleteChunkVectorQuietly(String collectionName, String chunkId) {
        if (!StringUtils.hasText(collectionName) || !StringUtils.hasText(chunkId)) {
            return;
        }
        vectorStoreService.deleteChunkById(collectionName, chunkId);
    }

    private void deleteDocumentVectorsQuietly(String collectionName, String docId) {
        if (!StringUtils.hasText(collectionName) || !StringUtils.hasText(docId)) {
            return;
        }
        vectorStoreService.deleteDocumentVectors(collectionName, docId);
    }

    private void updateDocumentChunkCount(String docId) {
        long chunkCount = knowledgeChunkDOMapper.selectCount(
                Wrappers.lambdaQuery(KnowledgeChunkDO.class).eq(KnowledgeChunkDO::getDocId, docId)
        );
        KnowledgeDocumentDO updateDO = new KnowledgeDocumentDO();
        updateDO.setId(docId);
        updateDO.setChunkCount((int) chunkCount);
        updateDO.setUpdatedBy(currentOperator());
        knowledgeDocumentMapper.updateById(updateDO);
    }

    private KnowledgeChunkVO toChunkVO(KnowledgeChunkDO chunkDO) {
        return BeanUtil.toBean(chunkDO, KnowledgeChunkVO.class);
    }

    private KnowledgeChunkDO getChunk(String docId, String chunkId) {
        if (!StringUtils.hasText(chunkId)) {
            throw new ClientException("分块 id 不能为空");
        }
        KnowledgeChunkDO chunkDO = knowledgeChunkDOMapper.selectById(chunkId);
        if (chunkDO == null) {
            throw new ClientException("分块不存在");
        }
        if (!Objects.equals(chunkDO.getDocId(), docId)) {
            throw new ClientException("当前分块不属于指定文件");
        }
        return chunkDO;
    }

    private KnowledgeDocumentDO getDocument(String docId) {
        if (!StringUtils.hasText(docId)) {
            throw new ClientException("文档 id 不能为空");
        }
        KnowledgeDocumentDO documentDO = knowledgeDocumentMapper.selectById(docId);
        if (documentDO == null || Objects.equals(documentDO.getDeleted(), 1)) {
            throw new ClientException("对应文件不存在");
        }
        return documentDO;
    }

    /**
     * 获取对应的知识库对象
     */
    private KnowledgeBaseDO getKnowledgeBase(String kbId) {
        if (!StringUtils.hasText(kbId)) {
            throw new ClientException("知识库 id 不能为空");
        }
        KnowledgeBaseDO kbDO = knowledgeBaseMapper.selectById(kbId);
        if (kbDO == null || Objects.equals(kbDO.getDeleted(), 1)) {
            throw new ClientException("对应知识库不存在");
        }
        return kbDO;
    }


    /**
     * 为了防止出现循环依赖等情况，使用ObjectProvider包装EmbeddingService，这里获取EmbeddingService
     */
    private EmbeddingService getEmbeddingService() {
        EmbeddingService embeddingService = embeddingServiceProvider.getIfAvailable();
        if (embeddingService == null) {
            throw new ServiceException("未配置向量服务");
        }
        return embeddingService;
    }


    /**
     * 将向量列表转换为数组
     */
    private float[] toPrimitiveArray(List<Float> embedding) {
        if (CollectionUtils.isEmpty(embedding)) {
            return new float[0];
        }
        float[] result = new float[embedding.size()];
        for (int i = 0; i < embedding.size(); i++) {
            result[i] = Optional.ofNullable(embedding.get(i)).orElse(0F);
        }
        return result;
    }

    /**
     * 解析当前操作作者
     */
    private String currentOperator() {
        if (StringUtils.hasText(UserContext.getNickName())) {
            return UserContext.getNickName();
        }
        Long userId = UserContext.getId();
        return userId == null ? "system" : String.valueOf(userId);
    }
}
