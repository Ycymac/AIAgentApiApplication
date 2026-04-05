package com.ycy.aiapplication.knowledge.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.net.url.UrlBuilder;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ycy.aiapplication.chunk.ChunkingMode;
import com.ycy.aiapplication.chunk.ChunkingStrategyFactory;
import com.ycy.aiapplication.chunk.VectorChunk;
import com.ycy.aiapplication.chunk.records.ChunkingOptions;
import com.ycy.aiapplication.framework.exception.ClientException;
import com.ycy.aiapplication.framework.exception.ServiceException;

import com.ycy.aiapplication.infrastructure.ai.embedding.EmbeddingService;
import com.ycy.aiapplication.knowledge.common.enums.DocumentStatus;
import com.ycy.aiapplication.knowledge.common.enums.ProcessMode;
import com.ycy.aiapplication.knowledge.common.enums.SourceType;
import com.ycy.aiapplication.knowledge.control.request.doc.KnowledgeDocumentPageRequest;
import com.ycy.aiapplication.knowledge.control.request.doc.KnowledgeDocumentUpdateRequest;
import com.ycy.aiapplication.knowledge.control.request.doc.KnowledgeDocumentUploadRequest;
import com.ycy.aiapplication.knowledge.control.vo.KnowledgeDocumentChunkLogVO;
import com.ycy.aiapplication.knowledge.control.vo.KnowledgeDocumentSearchVO;
import com.ycy.aiapplication.knowledge.control.vo.KnowledgeDocumentVO;
import com.ycy.aiapplication.knowledge.dao.entity.KnowledgeBaseDO;
import com.ycy.aiapplication.knowledge.dao.entity.KnowledgeChunkDO;
import com.ycy.aiapplication.knowledge.dao.entity.KnowledgeDocumentChunkLogDO;
import com.ycy.aiapplication.knowledge.dao.entity.KnowledgeDocumentDO;
import com.ycy.aiapplication.knowledge.dao.mapper.KnowledgeBaseMapper;
import com.ycy.aiapplication.knowledge.dao.mapper.KnowledgeChunkDOMapper;
import com.ycy.aiapplication.knowledge.dao.mapper.KnowledgeDocumentChunkLogMapper;
import com.ycy.aiapplication.knowledge.dao.mapper.KnowledgeDocumentMapper;
import com.ycy.aiapplication.knowledge.service.KnowledgeDocumentService;
import com.ycy.aiapplication.knowledge.toolkit.AliOSSUtils;
import com.ycy.aiapplication.parse.parser.DocumentParser;
import com.ycy.aiapplication.framework.context.UserContext;
import com.ycy.aiapplication.vector.VectorStoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;


/**
 * 知识库文档服务实现类。
 * <p>
 * 当前版本负责文档上传、文档管理、同步分块、同步向量化以及处理日志记录。
 * 当前不接入 pipeline，也不通过 MQ 做异步解耦。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeDocumentServiceImpl implements KnowledgeDocumentService {

    //文件上传时不限制大小，由 OSS 工具类按当前配置处理。
    private static final long NO_UPLOAD_LIMIT = -1L;
    //默认分页大小。
    private static final int DEFAULT_PAGE_SIZE = 10;
    //默认搜索返回条数
    private static final int DEFAULT_SEARCH_LIMIT = 8;

    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final KnowledgeDocumentChunkLogMapper knowledgeDocumentChunkLogMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeChunkDOMapper knowledgeChunkDOMapper;
    private final AliOSSUtils aliOSSUtils;
    private final ChunkingStrategyFactory chunkingStrategyFactory;
    private final List<DocumentParser> documentParsers;
    private final ObjectProvider<EmbeddingService> embeddingServiceProvider;
    private final VectorStoreService vectorStoreService;


    /**
     * 上传知识库文档。(仅进行文件上传)
     * <p>
     * 执行步骤：
     * 1. 校验知识库和请求参数
     * 2. 解析来源类型、处理模式、分块策略与分块配置
     * 3. 文件来源时上传 OSS，URL 来源时仅记录来源地址
     * 4. 初始化文档记录并保存，文档状态置为 pending
     *
     * @param kbId         知识库 ID
     * @param requestParam 上传请求参数
     * @param file         上传文件，URL 模式下可为空
     * @return 文档视图对象
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeDocumentVO upload(String kbId, KnowledgeDocumentUploadRequest requestParam, MultipartFile file) {
        KnowledgeBaseDO kbDO = getKnowledgeBase(kbId);
        if (requestParam == null) {
            throw new ClientException("Upload request must not be null");
        }

        //标准化参数
        SourceType sourceType = normalizeSourceType(requestParam.getSourceType(), file);
        ProcessMode processMode = normalizeProcessMode(requestParam.getProcessMode());
        ChunkingMode chunkingMode = normalizeChunkingMode(requestParam.getChunkStrategy());
        String chunkConfig = normalizeChunkConfig(requestParam.getChunkConfig(), chunkingMode);

        KnowledgeDocumentDO documentDO = KnowledgeDocumentDO.builder()
                .kbId(kbDO.getId())
                .docName(buildDocumentName(sourceType, requestParam, file))
                .sourceType(sourceType.getValue())
                .sourceLocation(sourceType == SourceType.URL ? requestParam.getSourceLocation() : null)
                .scheduleEnabled(Boolean.TRUE.equals(requestParam.getScheduleEnabled()) ? 1 : 0)
                .scheduleCron(StrUtil.blankToDefault(requestParam.getScheduleCron(), null))
                .enabled(1)
                .chunkCount(0)
                .fileUrl(uploadFileIfNecessary(sourceType, kbId, file))
                .fileType(resolveFileType(sourceType, requestParam, file))
                .fileSize(resolveFileSize(sourceType, file))
                .processMode(processMode.getValue())
                .chunkStrategy(chunkingMode.getValue())
                .chunkConfig(chunkConfig)
                .status(DocumentStatus.PENDING.getCode())
                .createdBy(currentOperator())
                .updatedBy(currentOperator())
                .deleted(0)
                .build();
        knowledgeDocumentMapper.insert(documentDO);
        return toDocumentVO(documentDO);
    }


    /**
     * 删除文档。
     * <p>
     * 幂等设计，保障不会多次删除
     * 当前采用逻辑删除，同时删除持久化层和向量数据库当中数据
     *
     * @param docId 文档 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String docId) {
        KnowledgeDocumentDO documentDO = getDocument(docId);
        if (Objects.equals(documentDO.getDeleted(), 1)) {
            return;
        }
        KnowledgeBaseDO kbDO = getKnowledgeBase(documentDO.getKbId());
        //删除向量数据库数据
        deleteDocumentVectorsQuietly(kbDO.getCollectionName(), documentDO.getId());
        documentDO.setDeleted(1);
        documentDO.setChunkCount(0);
        documentDO.setStatus(DocumentStatus.PENDING.getCode());
        documentDO.setUpdatedBy(currentOperator());
        knowledgeDocumentMapper.updateById(documentDO);
        //删除数据库持久化
        deleteDocumentChunksQuietly(docId);
    }


    /**
     * 启动文档分块处理。
     * <p>
     * 当前阶段不引入消息队列，因此这里直接同步调用 executeChunk。
     * @param docId 文档 ID
     */
    @Override
    public void startChunk(String docId) {
        executeChunk(docId);
    }

    /**
     * 执行文档分块与向量化。
     * <p>
     * 当前采用同步串行处理，整体流程如下：
     * 1. 查询文档和所属知识库
     * 2. 创建运行中的处理日志
     * 3. 提取纯文本
     * 4. 执行文本分块
     * 5. 执行向量化
     * 6. 更新文档状态、chunk 数量和处理日志
     * <p>
     * 注意：
     * 当前版本只更新聚合状态，不写 chunk 明细表，也不写向量库。
     *
     * @param docId 文档 ID
     */
    @Override
    public void executeChunk(String docId) {
        KnowledgeDocumentDO documentDO = getDocument(docId);
        KnowledgeBaseDO kbDO = getKnowledgeBase(documentDO.getKbId());
        KnowledgeDocumentChunkLogDO chunkLog = createRunningChunkLog(documentDO);
        String collectionName = kbDO.getCollectionName();

        long totalStart = System.currentTimeMillis();
        //解析时常
        long extractDuration = 0L;
        //分块时间
        long chunkDuration = 0L;
        //向量化时间
        long embedDuration = 0L;
        //持久化时间
        long persistDuration = 0L;

        try {
            markDocumentRunning(documentDO);

            // 第一步：读取文档来源，并通过 parser 抽取纯文本。
            long extractStart = System.currentTimeMillis();
            String extractedText = extractDocumentText(documentDO);
            extractDuration = System.currentTimeMillis() - extractStart;

            // 第二步：按文档当前保存的分块策略与分块配置执行切分。
            long chunkStart = System.currentTimeMillis();
            List<VectorChunk> chunks = doChunk(documentDO, extractedText);
            chunkDuration = System.currentTimeMillis() - chunkStart;

            // 第三步：同步调用 embedding 服务，为每个 chunk 回填向量。
            long embedStart = System.currentTimeMillis();
            enrichEmbedding(kbDO, chunks);
            embedDuration = System.currentTimeMillis() - embedStart;

            //第四步：执行向量持久化保存&向量数据库保存
            long persistStart = System.currentTimeMillis();
            replaceDocumentVectors(collectionName, documentDO.getId(), chunks);
            replaceDocumentChunks(kbDO, documentDO, chunks);
            documentDO.setChunkCount(chunks.size());
            documentDO.setStatus(DocumentStatus.SUCCESS.getCode());
            documentDO.setUpdatedBy(currentOperator());
            knowledgeDocumentMapper.updateById(documentDO);
            persistDuration = System.currentTimeMillis() - persistStart;

            fillSuccessLog(chunkLog, extractDuration, chunkDuration, embedDuration, persistDuration, chunks.size(), totalStart);
            knowledgeDocumentChunkLogMapper.updateById(chunkLog);
        } catch (Exception ex) {
            log.error("Execute knowledge document chunk failed, docId={}", docId, ex);
            markDocumentFailed(documentDO);
            fillFailedLog(chunkLog, extractDuration, chunkDuration, embedDuration, persistDuration, totalStart, ex);
            knowledgeDocumentChunkLogMapper.updateById(chunkLog);
            throw ex instanceof RuntimeException ? (RuntimeException) ex : new ServiceException("Document chunking failed");
        }
    }

    /**
     * 查询文档详情。
     *
     * @param docId 文档 ID
     * @return 文档视图对象
     */
    @Override
    public KnowledgeDocumentVO get(String docId) {
        return toDocumentVO(getDocument(docId));
    }

    /**
     * 更新文档信息。
     * <p>
     * 支持更新：
     * 1. 文档名称
     * 2. 处理模式
     * 3. 分块策略
     * 4. 分块配置
     * <p>
     * 当分块相关配置变更时，文档需要重新处理，因此状态会回退为 pending。
     *
     * @param docId        文档 ID
     * @param requestParam 更新请求
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(String docId, KnowledgeDocumentUpdateRequest requestParam) {
        KnowledgeDocumentDO documentDO = getDocument(docId);
        if (requestParam == null) {
            throw new ClientException("Update request must not be null");
        }

        boolean changed = false;
        boolean chunkRelatedChanged = false;

        if (StringUtils.hasText(requestParam.getDocName())) {
            documentDO.setDocName(requestParam.getDocName().trim());
            changed = true;
        }
        if (StringUtils.hasText(requestParam.getProcessMode())) {
            ProcessMode processMode = normalizeProcessMode(requestParam.getProcessMode());
            if (!Objects.equals(documentDO.getProcessMode(), processMode.getValue())) {
                documentDO.setProcessMode(processMode.getValue());
                changed = true;
                chunkRelatedChanged = true;
            }
        }
        if (StringUtils.hasText(requestParam.getChunkStrategy())) {
            ChunkingMode chunkingMode = normalizeChunkingMode(requestParam.getChunkStrategy());
            if (!Objects.equals(documentDO.getChunkStrategy(), chunkingMode.getValue())) {
                documentDO.setChunkStrategy(chunkingMode.getValue());
                changed = true;
                chunkRelatedChanged = true;
            }
        }
        if (StringUtils.hasText(requestParam.getChunkConfig())) {
            ChunkingMode targetChunkMode = normalizeChunkingMode(
                    StringUtils.hasText(requestParam.getChunkStrategy()) ? requestParam.getChunkStrategy() : documentDO.getChunkStrategy());
            String normalizedChunkConfig = normalizeChunkConfig(requestParam.getChunkConfig(), targetChunkMode);
            if (!Objects.equals(documentDO.getChunkConfig(), normalizedChunkConfig)) {
                documentDO.setChunkConfig(normalizedChunkConfig);
                changed = true;
                chunkRelatedChanged = true;
            }
        }

        if (!changed) {
            throw new ClientException("No updatable fields found");
        }

        // 分块相关配置变化后，历史 chunkCount 已不再可信，需要重新进入待处理状态。
        //删除向量数据库&持久化当中的分块数据
        if (chunkRelatedChanged) {
            KnowledgeBaseDO kbDO = getKnowledgeBase(documentDO.getKbId());
            deleteDocumentVectorsQuietly(kbDO.getCollectionName(), documentDO.getId());
            deleteDocumentChunksQuietly(documentDO.getId());
            documentDO.setChunkCount(0);
            documentDO.setStatus(DocumentStatus.PENDING.getCode());
        }
        documentDO.setUpdatedBy(currentOperator());
        knowledgeDocumentMapper.updateById(documentDO);
    }

    /**
     * 分页查询知识库下的文档列表。
     * <p>
     * 支持按状态和关键词筛选，关键词匹配文档名称与来源地址。
     *
     * @param kbId         知识库 ID
     * @param requestParam 分页请求
     * @return 文档分页结果
     */
    @Override
    public IPage<KnowledgeDocumentVO> page(String kbId, KnowledgeDocumentPageRequest requestParam) {
        getKnowledgeBase(kbId);
        KnowledgeDocumentPageRequest pageRequest = requestParam == null ? new KnowledgeDocumentPageRequest() : requestParam;
        long current = pageRequest.getCurrent() <= 0 ? 1L : pageRequest.getCurrent();
        long size = pageRequest.getSize() <= 0 ? DEFAULT_PAGE_SIZE : pageRequest.getSize();

        LambdaQueryWrapper<KnowledgeDocumentDO> queryWrapper = Wrappers.lambdaQuery(KnowledgeDocumentDO.class)
                .eq(KnowledgeDocumentDO::getKbId, kbId)
                .eq(KnowledgeDocumentDO::getDeleted, 0)
                .eq(StringUtils.hasText(pageRequest.getStatus()), KnowledgeDocumentDO::getStatus, pageRequest.getStatus().trim())
                .and(StringUtils.hasText(pageRequest.getKeyword()), wrapper -> wrapper
                        .like(KnowledgeDocumentDO::getDocName, pageRequest.getKeyword().trim())
                        .or()
                        .like(KnowledgeDocumentDO::getSourceLocation, pageRequest.getKeyword().trim()))
                .orderByDesc(KnowledgeDocumentDO::getUpdateTime);

        Page<KnowledgeDocumentDO> page = new Page<>(current, size);
        return knowledgeDocumentMapper.selectPage(page, queryWrapper).convert(this::toDocumentVO);
    }

    /**
     * 启用或禁用文档。
     * <p>
     * 这里只维护业务开关，不触发重新分块，也不影响历史日志。
     *
     * @param docId   文档 ID
     * @param enabled 是否启用
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void enable(String docId, boolean enabled) {
        KnowledgeDocumentDO documentDO = getDocument(docId);
        documentDO.setEnabled(enabled ? 1 : 0);
        documentDO.setUpdatedBy(currentOperator());
        knowledgeDocumentMapper.updateById(documentDO);
    }

    /**
     * 搜索文档。
     * <p>
     * 当前是基于数据库字段的轻量搜索，不是向量检索。
     * 搜索范围包括文档名称和来源地址。
     *
     * @param keyword 关键词
     * @param limit   返回数量上限
     * @return 搜索结果
     */
    @Override
    public List<KnowledgeDocumentSearchVO> search(String keyword, int limit) {
        int finalLimit = limit <= 0 ? DEFAULT_SEARCH_LIMIT : limit;
        LambdaQueryWrapper<KnowledgeDocumentDO> queryWrapper = Wrappers.lambdaQuery(KnowledgeDocumentDO.class)
                .eq(KnowledgeDocumentDO::getDeleted, 0)
                .orderByDesc(KnowledgeDocumentDO::getUpdateTime)
                .last("limit " + finalLimit);

        if (StringUtils.hasText(keyword)) {
            queryWrapper.and(wrapper -> wrapper
                    .like(KnowledgeDocumentDO::getDocName, keyword.trim())
                    .or()
                    .like(KnowledgeDocumentDO::getSourceLocation, keyword.trim()));
        }

        List<KnowledgeDocumentDO> documents = knowledgeDocumentMapper.selectList(queryWrapper);
        if (CollectionUtils.isEmpty(documents)) {
            return List.of();
        }

        Map<String, String> kbNameMap = knowledgeBaseMapper.selectBatchIds(
                        documents.stream().map(KnowledgeDocumentDO::getKbId).distinct().collect(Collectors.toList()))
                .stream()
                .collect(Collectors.toMap(KnowledgeBaseDO::getId, KnowledgeBaseDO::getName, (left, right) -> left));

        return documents.stream()
                .map(each -> {
                    KnowledgeDocumentSearchVO searchVO = new KnowledgeDocumentSearchVO();
                    searchVO.setId(each.getId());
                    searchVO.setKbId(each.getKbId());
                    searchVO.setDocName(each.getDocName());
                    searchVO.setKbName(kbNameMap.get(each.getKbId()));
                    return searchVO;
                })
                .collect(Collectors.toList());
    }

    /**
     * 分页查询文档分块日志。
     *
     * @param docId 文档 ID
     * @param page  分页参数
     * @return 分块日志分页结果
     */
    @Override
    public IPage<KnowledgeDocumentChunkLogVO> getChunkLogs(String docId, Page<KnowledgeDocumentChunkLogVO> page) {
        getDocument(docId);
        Page<KnowledgeDocumentChunkLogDO> pageRequest = new Page<>(
                page == null || page.getCurrent() <= 0 ? 1L : page.getCurrent(),
                page == null || page.getSize() <= 0 ? DEFAULT_PAGE_SIZE : page.getSize()
        );
        IPage<KnowledgeDocumentChunkLogDO> result = knowledgeDocumentChunkLogMapper.selectPage(
                pageRequest,
                Wrappers.lambdaQuery(KnowledgeDocumentChunkLogDO.class)
                        .eq(KnowledgeDocumentChunkLogDO::getDocId, docId)
                        .orderByDesc(KnowledgeDocumentChunkLogDO::getCreateTime)
        );
        return result.convert(this::toChunkLogVO);
    }

    /**
     * 将文档实体转换为视图对象。
     * <p>
     * 这里额外处理：
     * 1. enabled 的 Integer -> Boolean 转换
     * 2. Date -> LocalDateTime 的时间类型转换
     *
     * @param documentDO 文档实体
     * @return 文档视图对象
     */
    private KnowledgeDocumentVO toDocumentVO(KnowledgeDocumentDO documentDO) {
        KnowledgeDocumentVO documentVO = BeanUtil.toBean(documentDO, KnowledgeDocumentVO.class);
        documentVO.setEnabled(Objects.equals(documentDO.getEnabled(), 1));
        if (documentDO.getCreateTime() != null) {
            documentVO.setCreateTime(documentDO.getCreateTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
        }
        if (documentDO.getUpdateTime() != null) {
            documentVO.setUpdateTime(documentDO.getUpdateTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
        }
        return documentVO;
    }

    /**
     * 将分块日志实体转换为视图对象。
     * <p>
     * otherDuration 没有单独落库，而是由总耗时减去主要阶段耗时动态计算得出。
     *
     * @param logDO 分块日志实体
     * @return 分块日志视图对象
     */
    private KnowledgeDocumentChunkLogVO toChunkLogVO(KnowledgeDocumentChunkLogDO logDO) {
        KnowledgeDocumentChunkLogVO logVO = BeanUtil.toBean(logDO, KnowledgeDocumentChunkLogVO.class);
        long otherDuration = Optional.ofNullable(logDO.getTotalDuration()).orElse(0L)
                - Optional.ofNullable(logDO.getExtractDuration()).orElse(0L)
                - Optional.ofNullable(logDO.getChunkDuration()).orElse(0L)
                - Optional.ofNullable(logDO.getEmbedDuration()).orElse(0L)
                - Optional.ofNullable(logDO.getPersistDuration()).orElse(0L);
        logVO.setOtherDuration(Math.max(otherDuration, 0L));
        return logVO;
    }

    /**
     * 执行文本分块。
     * <p>
     * 处理过程：
     * 1. 读取文档保存的分块策略
     * 2. 解析并补全分块配置
     * 3. 构造 ChunkingOptions
     * 4. 调用对应的 ChunkingStrategy 执行切分
     *
     * @param documentDO    文档实体
     * @param extractedText 提取后的文本
     * @return 分块结果
     */
    private List<VectorChunk> doChunk(KnowledgeDocumentDO documentDO, String extractedText) {
        if (!StringUtils.hasText(extractedText)) {
            return List.of();
        }
        ChunkingMode chunkingMode = normalizeChunkingMode(documentDO.getChunkStrategy());
        Map<String, Object> configMap = parseChunkConfig(documentDO.getChunkConfig());
        ChunkingOptions options = chunkingMode.createOptions(configMap);
        return chunkingStrategyFactory.requireStrategy(chunkingMode).chunk(extractedText, options);
    }

    /**
     * 为 chunk 列表补充向量数据。
     * <p>
     * 当前采用批量 embedding，要求返回结果数量与 chunk 数量完全一致。
     *
     * @param kbDO   知识库实体
     * @param chunks 分块结果
     */
    private void enrichEmbedding(KnowledgeBaseDO kbDO, List<VectorChunk> chunks) {
        if (CollUtil.isEmpty(chunks)) {
            return;
        }
        EmbeddingService embeddingService = embeddingServiceProvider.getIfAvailable();
        if (embeddingService == null) {
            throw new ServiceException("EmbeddingService is not configured");
        }

        List<VectorChunk> validChunks = chunks.stream()
                .filter(chunk -> StringUtils.hasText(chunk.getContent()))
                .toList();
        if (CollectionUtils.isEmpty(validChunks)) {
            return;
        }

        List<String> texts = validChunks.stream()
                .map(VectorChunk::getContent)
                .collect(Collectors.toList());

        List<List<Float>> embeddings = embeddingService.embedBatch(texts, kbDO.getEmbeddingModel());
        if (embeddings == null || embeddings.size() != texts.size()) {
            throw new ServiceException("Embedding result size does not match chunk size");
        }
        // embedding 结果与输入 texts 的顺序保持一致，因此可以按下标直接回填。
        for (int index = 0; index < validChunks.size(); index++) {
            List<Float> embedding = embeddings.get(index);
            validChunks.get(index).setEmbedding(toPrimitiveArray(embedding));
        }
    }

    /**
     * 使用“先删后建”的方式重建文档向量，避免多次执行分块时出现重复向量。
     */
    private void replaceDocumentVectors(String collectionName, String docId, List<VectorChunk> chunks) {
        deleteDocumentVectorsQuietly(collectionName, docId);
        if (CollUtil.isEmpty(chunks)) {
            return;
        }
        vectorStoreService.indexDocumentChunks(collectionName, docId, chunks);
    }

    /**
     * 向量删除按幂等处理，文档不存在旧向量时也允许继续后续流程。
     */
    private void deleteDocumentVectorsQuietly(String collectionName, String docId) {
        if (!StringUtils.hasText(collectionName) || !StringUtils.hasText(docId)) {
            return;
        }
        vectorStoreService.deleteDocumentVectors(collectionName, docId);
    }

    /**
     * 删除旧的文档分块，插入新的文档分块，确保无残留
     */
    private void replaceDocumentChunks(KnowledgeBaseDO kbDO, KnowledgeDocumentDO documentDO, List<VectorChunk> chunks) {
        deleteDocumentChunksQuietly(documentDO.getId());
        if (CollUtil.isEmpty(chunks)) {
            return;
        }
        String operator = currentOperator();
        for (VectorChunk chunk : chunks) {
            knowledgeChunkDOMapper.insert(buildChunkDO(kbDO, documentDO, chunk, operator));
        }
    }

    /**
     * 删除数据库当中当前文件的所有分块
     */
    private void deleteDocumentChunksQuietly(String docId) {
        if (!StringUtils.hasText(docId)) {
            return;
        }
        knowledgeChunkDOMapper.delete(Wrappers.lambdaQuery(KnowledgeChunkDO.class)
                .eq(KnowledgeChunkDO::getDocId, docId));
    }

    /**
     *构建持久层分块对象
     */
    private KnowledgeChunkDO buildChunkDO(KnowledgeBaseDO kbDO, KnowledgeDocumentDO documentDO, VectorChunk chunk, String operator) {
        String content = StrUtil.nullToEmpty(chunk.getContent());
        return KnowledgeChunkDO.builder()
                .kbId(kbDO.getId())
                .docId(documentDO.getId())
                .chunkIndex(chunk.getIndex())
                .content(content)
                //使用sha256哈希算法计算唯一的哈希指纹
                .contentHash(DigestUtil.sha256Hex(content))
                .charCount(content.length())
                .tokenCount(null)
                .enabled(1)
                .createdBy(operator)
                .updatedBy(operator)
                .deleted(0)
                .build();
    }


    /**
     * 将 List<Float> 转换为 float[]。
     * <p>
     * 使用原始数组是为了和 VectorChunk 的定义保持一致，并减少装箱开销。
     *
     * @param embedding 向量列表
     * @return 原始 float 数组
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
     * 提取文档文本。
     * <p>
     * 处理步骤：
     * 1. 解析文件名
     * 2. 推断 MIME 类型
     * 3. 选择合适的 parser
     * 4. 打开来源输入流并提取文本
     *
     * @param documentDO 文档实体
     * @return 提取后的文本
     */
    private String extractDocumentText(KnowledgeDocumentDO documentDO) {
        String fileName = resolveSourceFileName(documentDO);
        String mimeType = probeMimeType(documentDO, fileName);
        DocumentParser parser = selectParser(fileName, mimeType);
        try (InputStream inputStream = openSourceStream(documentDO)) {
            return parser.extractText(inputStream, fileName);
        } catch (IOException ex) {
            throw new ServiceException("Failed to read document source: " + fileName);
        }
    }

    /**
     * 打开文档来源输入流。
     * <p>
     * file 来源读取 OSS 地址；
     * url 来源读取原始来源地址。
     *
     * @param documentDO 文档实体
     * @return 输入流
     * @throws IOException IO 异常
     */
    private InputStream openSourceStream(KnowledgeDocumentDO documentDO) throws IOException {
        String source = SourceType.FILE.getValue().equalsIgnoreCase(documentDO.getSourceType())
                ? documentDO.getFileUrl()
                : documentDO.getSourceLocation();
        if (!StringUtils.hasText(source)) {
            throw new ClientException("Document source must not be blank");
        }
        return new URL(source).openStream();
    }

    /**
     * 选择文档解析器。
     * <p>
     * 选择规则：
     * 1. markdown 后缀优先使用 Markdown 解析器
     * 2. 其余情况按 supports(mimeType) 匹配
     * 3. 若 MIME 无法识别，则兜底使用 Tika 解析器
     *
     * @param fileName 文件名
     * @param mimeType MIME 类型
     * @return 文档解析器
     */
    private DocumentParser selectParser(String fileName, String mimeType) {
        boolean markdown = StrUtil.endWithAnyIgnoreCase(fileName, ".md", ".markdown");
        if (markdown) {
            for (DocumentParser parser : documentParsers) {
                if (parser.getClass().getSimpleName().contains("Markdown")) {
                    return parser;
                }
            }
        }
        for (DocumentParser parser : documentParsers) {
            if (parser.supports(mimeType)) {
                return parser;
            }
        }
        // MIME 推断失败时，优先使用 Tika 作为通用解析兜底。
        for (DocumentParser parser : documentParsers) {
            if (parser.getClass().getSimpleName().contains("Tika")) {
                return parser;
            }
        }
        throw new ServiceException("No parser found for file: " + fileName);
    }

    /**
     * 推断文档 MIME 类型。
     * <p>
     * 优先使用数据库已记录的 fileType，没有记录时再根据文件名后缀猜测。
     *
     * @param documentDO 文档实体
     * @param fileName   文件名
     * @return MIME 类型
     */
    private String probeMimeType(KnowledgeDocumentDO documentDO, String fileName) {
        if (StringUtils.hasText(documentDO.getFileType())) {
            return documentDO.getFileType();
        }
        return URLConnection.guessContentTypeFromName(fileName);
    }

    /**
     * 解析来源文件名。
     * <p>
     * 优先返回 docName，如果为空则从来源地址提取文件名。
     *
     * @param documentDO 文档实体
     * @return 文件名
     */
    private String resolveSourceFileName(KnowledgeDocumentDO documentDO) {
        if (StringUtils.hasText(documentDO.getDocName())) {
            return documentDO.getDocName();
        }
        String source = SourceType.FILE.getValue().equalsIgnoreCase(documentDO.getSourceType())
                ? documentDO.getFileUrl()
                : documentDO.getSourceLocation();
        return Optional.ofNullable(FileUtil.getName(source)).filter(StringUtils::hasText).orElse("document.txt");
    }

    /**
     * 将文档状态标记为运行中。
     *
     * @param documentDO 文档实体
     */
    private void markDocumentRunning(KnowledgeDocumentDO documentDO) {
        documentDO.setStatus(DocumentStatus.RUNNING.getCode());
        documentDO.setUpdatedBy(currentOperator());
        knowledgeDocumentMapper.updateById(documentDO);
    }

    /**
     * 将文档状态标记为失败。
     *
     * @param documentDO 文档实体
     */
    private void markDocumentFailed(KnowledgeDocumentDO documentDO) {
        documentDO.setStatus(DocumentStatus.FAILED.getCode());
        documentDO.setUpdatedBy(currentOperator());
        knowledgeDocumentMapper.updateById(documentDO);
    }

    /**
     * 创建运行中的分块日志。
     * <p>
     * 分块开始时先插入一条 running 记录，后续在同一条记录上补充耗时和结果。
     *
     * @param documentDO 文档实体
     * @return 分块日志实体
     */
    private KnowledgeDocumentChunkLogDO createRunningChunkLog(KnowledgeDocumentDO documentDO) {
        KnowledgeDocumentChunkLogDO chunkLogDO = KnowledgeDocumentChunkLogDO.builder()
                .docId(documentDO.getId())
                .status(DocumentStatus.RUNNING.getCode())
                .processMode(documentDO.getProcessMode())
                .chunkStrategy(documentDO.getChunkStrategy())
                .startTime(new Date())
                .build();
        knowledgeDocumentChunkLogMapper.insert(chunkLogDO);
        return chunkLogDO;
    }

    /**
     * 填充成功日志。
     *
     * @param chunkLogDO      日志实体
     * @param extractDuration 文本提取耗时
     * @param chunkDuration   分块耗时
     * @param embedDuration   向量化耗时
     * @param persistDuration 持久化耗时
     * @param chunkCount      chunk 数量
     * @param totalStart      总开始时间戳
     */
    private void fillSuccessLog(KnowledgeDocumentChunkLogDO chunkLogDO, long extractDuration, long chunkDuration,
                                long embedDuration, long persistDuration, int chunkCount, long totalStart) {
        chunkLogDO.setStatus(DocumentStatus.SUCCESS.getCode());
        chunkLogDO.setExtractDuration(extractDuration);
        chunkLogDO.setChunkDuration(chunkDuration);
        chunkLogDO.setEmbedDuration(embedDuration);
        chunkLogDO.setPersistDuration(persistDuration);
        chunkLogDO.setChunkCount(chunkCount);
        chunkLogDO.setErrorMessage(null);
        chunkLogDO.setEndTime(new Date());
        chunkLogDO.setTotalDuration(System.currentTimeMillis() - totalStart);
    }

    /**
     * 填充失败日志。
     *
     * @param chunkLogDO      日志实体
     * @param extractDuration 文本提取耗时
     * @param chunkDuration   分块耗时
     * @param embedDuration   向量化耗时
     * @param persistDuration 持久化耗时
     * @param totalStart      总开始时间戳
     * @param ex              异常信息
     */
    private void fillFailedLog(KnowledgeDocumentChunkLogDO chunkLogDO, long extractDuration, long chunkDuration,
                               long embedDuration, long persistDuration, long totalStart, Exception ex) {
        chunkLogDO.setStatus(DocumentStatus.FAILED.getCode());
        chunkLogDO.setExtractDuration(extractDuration);
        chunkLogDO.setChunkDuration(chunkDuration);
        chunkLogDO.setEmbedDuration(embedDuration);
        chunkLogDO.setPersistDuration(persistDuration);
        chunkLogDO.setErrorMessage(StrUtil.maxLength(ex.getMessage(), 500));
        chunkLogDO.setEndTime(new Date());
        chunkLogDO.setTotalDuration(System.currentTimeMillis() - totalStart);
    }

    /**
     * 在文件来源场景下执行文件上传。
     * <p>
     * URL 来源无需上传文件，因此直接返回 null。
     *
     * @param sourceType 来源类型
     * @param kbId       知识库 ID
     * @param file       上传文件
     * @return OSS 文件地址
     */
    private String uploadFileIfNecessary(SourceType sourceType, String kbId, MultipartFile file) {
        if (sourceType != SourceType.FILE) {
            return null;
        }
        if (file == null || file.isEmpty()) {
            throw new ClientException("File source requires a multipart file");
        }
        return aliOSSUtils.upload(file, kbId, NO_UPLOAD_LIMIT);
    }

    /**
     * 解析文件大小。
     *
     * @param sourceType 来源类型
     * @param file       上传文件
     * @return 文件大小，非文件来源时返回 null
     */
    private Long resolveFileSize(SourceType sourceType, MultipartFile file) {
        if (sourceType != SourceType.FILE || file == null) {
            return null;
        }
        return file.getSize();
    }

    /**
     * 解析文件类型。
     * <p>
     * 文件来源优先使用 MultipartFile 的 contentType；
     * URL 来源则根据来源地址后缀进行推断。
     *
     * @param sourceType   来源类型
     * @param requestParam 上传请求
     * @param file         上传文件
     * @return 文件类型
     */
    private String resolveFileType(SourceType sourceType, KnowledgeDocumentUploadRequest requestParam, MultipartFile file) {
        if (sourceType == SourceType.FILE && file != null) {
            return file.getContentType();
        }
        if (sourceType == SourceType.URL && StringUtils.hasText(requestParam.getSourceLocation())) {
            return URLConnection.guessContentTypeFromName(requestParam.getSourceLocation());
        }
        return null;
    }

    /**
     * 构建文档名称。
     * <p>
     * 文件来源使用原始文件名；
     * URL 来源优先从 URL 路径提取文件名，提取不到则回退为原 URL。
     *
     * @param sourceType   来源类型
     * @param requestParam 上传请求
     * @param file         上传文件
     * @return 文档名称
     */
    private String buildDocumentName(SourceType sourceType, KnowledgeDocumentUploadRequest requestParam, MultipartFile file) {
        if (sourceType == SourceType.FILE) {
            if (file == null || file.isEmpty()) {
                throw new ClientException("File source requires a multipart file");
            }
            return Optional.ofNullable(file.getOriginalFilename()).filter(StringUtils::hasText).orElse("unknown");
        }
        String sourceLocation = requestParam.getSourceLocation();
        if (!StringUtils.hasText(sourceLocation)) {
            throw new ClientException("URL source requires sourceLocation");
        }
        String path = UrlBuilder.ofHttp(sourceLocation).getPath().toString();
        return StrUtil.blankToDefault(FileUtil.getName(path), sourceLocation);
    }

    /**
     * 规范化来源类型。
     * <p>
     * 如果前端未显式传 sourceType，则根据是否携带文件自动推断：
     * 1. 有文件 -> file
     * 2. 无文件 -> url
     *
     * @param sourceTypeValue 原始来源类型
     * @param file            上传文件
     * @return 规范化后的来源类型
     */
    private SourceType normalizeSourceType(String sourceTypeValue, MultipartFile file) {
        if (!StringUtils.hasText(sourceTypeValue)) {
            return file != null && !file.isEmpty() ? SourceType.FILE : SourceType.URL;
        }
        try {
            return SourceType.normalize(sourceTypeValue);
        } catch (IllegalArgumentException ex) {
            throw new ClientException(ex.getMessage());
        }
    }

    /**
     * 规范化处理模式。
     * <p>
     * 当前只支持 chunk，未传时默认回落到 chunk。
     *
     * @param processModeValue 原始处理模式
     * @return 规范化后的处理模式
     */
    private ProcessMode normalizeProcessMode(String processModeValue) {
        String finalMode = StringUtils.hasText(processModeValue) ? processModeValue : ProcessMode.CHUNK.getValue();
        try {
            return ProcessMode.normalize(finalMode);
        } catch (IllegalArgumentException ex) {
            throw new ClientException(ex.getMessage());
        }
    }

    /**
     * 规范化分块策略。
     * <p>
     * 未传时默认使用 fixed_size。
     *
     * @param chunkStrategyValue 原始分块策略
     * @return 规范化后的分块策略
     */
    private ChunkingMode normalizeChunkingMode(String chunkStrategyValue) {
        String finalStrategy = StringUtils.hasText(chunkStrategyValue) ? chunkStrategyValue : ChunkingMode.FIXED_SIZE.getValue();
        try {
            return ChunkingMode.fromValue(finalStrategy);
        } catch (IllegalArgumentException ex) {
            throw new ClientException(ex.getMessage());
        }
    }

    /**
     * 规范化分块配置。
     * <p>
     * 原始 JSON 会先被解析成 Map，再通过 ChunkingMode 补齐默认值，
     * 最终重新序列化后保存，保证数据库中保存的是标准化配置。
     *
     * @param chunkConfig  原始分块配置 JSON
     * @param chunkingMode 分块策略
     * @return 标准化后的分块配置 JSON
     */
    private String normalizeChunkConfig(String chunkConfig, ChunkingMode chunkingMode) {
        Map<String, Object> configMap = parseChunkConfig(chunkConfig);
        ChunkingOptions options = chunkingMode.createOptions(configMap);
        return JSON.toJSONString(options.toConfigMap());
    }

    /**
     * 解析分块配置 JSON。
     *
     * @param chunkConfig 分块配置 JSON
     * @return 配置 Map
     */
    private Map<String, Object> parseChunkConfig(String chunkConfig) {
        if (!StringUtils.hasText(chunkConfig)) {
            return new HashMap<>();
        }
        try {
            Map<String, Object> configMap = JSON.parseObject(chunkConfig, new TypeReference<Map<String, Object>>() {
            });
            return configMap == null ? new HashMap<>() : new HashMap<>(configMap);
        } catch (Exception ex) {
            throw new ClientException("chunkConfig must be valid JSON");
        }
    }

    /**
     * 查询文档实体。
     * <p>
     * 会校验：
     * 1. 文档 ID 不能为空
     * 2. 文档必须存在
     * 3. 文档不能已被逻辑删除
     *
     * @param docId 文档 ID
     * @return 文档实体
     */
    private KnowledgeDocumentDO getDocument(String docId) {
        if (!StringUtils.hasText(docId)) {
            throw new ClientException("Document id must not be blank");
        }
        KnowledgeDocumentDO documentDO = knowledgeDocumentMapper.selectById(docId);
        if (documentDO == null || Objects.equals(documentDO.getDeleted(), 1)) {
            throw new ClientException("Document does not exist");
        }
        return documentDO;
    }


    /**
     * 查询知识库实体。
     * <p>
     * 会校验：
     * 1. 知识库 ID 不能为空
     * 2. 知识库必须存在
     * 3. 知识库不能已被逻辑删除
     *
     * @param kbId 知识库 ID
     * @return 知识库实体
     */
    private KnowledgeBaseDO getKnowledgeBase(String kbId) {
        if (!StringUtils.hasText(kbId)) {
            throw new ClientException("Knowledge base id must not be blank");
        }
        KnowledgeBaseDO kbDO = knowledgeBaseMapper.selectById(kbId);
        if (kbDO == null || Objects.equals(kbDO.getDeleted(), 1)) {
            throw new ClientException("Knowledge base does not exist");
        }
        return kbDO;
    }

    /**
     * 获取当前操作人。
     * <p>
     * 优先使用昵称，其次使用用户 ID，最后兜底为 system。
     *
     * @return 当前操作人标识
     */
    private String currentOperator() {
        if (StringUtils.hasText(UserContext.getNickName())) {
            return UserContext.getNickName();
        }
        Long userId = UserContext.getId();
        return userId == null ? "system" : String.valueOf(userId);
    }
}
