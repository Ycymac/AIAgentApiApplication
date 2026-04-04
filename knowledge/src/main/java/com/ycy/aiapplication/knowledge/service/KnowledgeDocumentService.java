package com.ycy.aiapplication.knowledge.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ycy.aiapplication.knowledge.control.request.doc.KnowledgeDocumentPageRequest;
import com.ycy.aiapplication.knowledge.control.request.doc.KnowledgeDocumentUpdateRequest;
import com.ycy.aiapplication.knowledge.control.request.doc.KnowledgeDocumentUploadRequest;
import com.ycy.aiapplication.knowledge.control.vo.KnowledgeDocumentChunkLogVO;
import com.ycy.aiapplication.knowledge.control.vo.KnowledgeDocumentSearchVO;
import com.ycy.aiapplication.knowledge.control.vo.KnowledgeDocumentVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 知识库文档服务接口。
 */
public interface KnowledgeDocumentService {

    KnowledgeDocumentVO upload(String kbId, KnowledgeDocumentUploadRequest requestParam, MultipartFile file);

    /**
     * 开始文档分块处理。
     */
    void startChunk(String docId);

    /**
     * 执行文档分块，当前按同步串行方式实现。
     */
    void executeChunk(String docId);

    void delete(String docId);

    KnowledgeDocumentVO get(String docId);

    void update(String docId, KnowledgeDocumentUpdateRequest requestParam);

    IPage<KnowledgeDocumentVO> page(String kbId, KnowledgeDocumentPageRequest requestParam);

    void enable(String docId, boolean enabled);

    List<KnowledgeDocumentSearchVO> search(String keyword, int limit);

    /**
     * 查询文档分块日志
     *
     * @param docId 文档 ID
     * @param page  分页参数
     * @return 分块日志分页结果
     */
    IPage<KnowledgeDocumentChunkLogVO> getChunkLogs(String docId, Page<KnowledgeDocumentChunkLogVO> page);
}
