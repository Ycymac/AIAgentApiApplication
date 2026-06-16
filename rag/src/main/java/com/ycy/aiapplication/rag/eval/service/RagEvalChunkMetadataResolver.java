package com.ycy.aiapplication.rag.eval.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ycy.aiapplication.framework.convention.RetrievedChunk;
import com.ycy.aiapplication.knowledge.dao.entity.KnowledgeChunkDO;
import com.ycy.aiapplication.knowledge.dao.entity.KnowledgeDocumentDO;
import com.ycy.aiapplication.knowledge.dao.mapper.KnowledgeChunkDOMapper;
import com.ycy.aiapplication.knowledge.dao.mapper.KnowledgeDocumentMapper;
import com.ycy.aiapplication.rag.core.retrieve.channel.SearchChannelResult;
import com.ycy.aiapplication.rag.eval.dto.RagEvalResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.rag-eval.enabled", havingValue = "true")
public class RagEvalChunkMetadataResolver {

    private final KnowledgeChunkDOMapper knowledgeChunkDOMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;

    public Metadata resolve(List<SearchChannelResult> channelResults, List<RetrievedChunk> finalChunks) {
        List<RetrievedChunk> allChunks = Stream.concat(
                        finalChunks == null ? Stream.empty() : finalChunks.stream(),
                        channelResults == null
                                ? Stream.empty()
                                : channelResults.stream()
                                .flatMap(channel -> channel.getChunks() == null
                                        ? Stream.empty()
                                        : channel.getChunks().stream()))
                .filter(Objects::nonNull)
                .filter(chunk -> StrUtil.isNotBlank(chunk.getId()))
                .collect(Collectors.collectingAndThen(
                        Collectors.toMap(
                                RetrievedChunk::getId,
                                Function.identity(),
                                (left, right) -> left,
                                LinkedHashMap::new),
                        map -> new ArrayList<>(map.values())));

        Map<String, KnowledgeChunkDO> chunkByVectorId = resolveChunks(allChunks);
        Map<String, KnowledgeDocumentDO> documentById = loadDocumentMetadata(chunkByVectorId.values());
        return new Metadata(chunkByVectorId, documentById);
    }

    public List<RagEvalResponse.ChunkView> buildChunkViews(
            List<RetrievedChunk> chunks,
            Metadata metadata,
            boolean includeContexts) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }
        List<RagEvalResponse.ChunkView> views = new ArrayList<>();
        for (int index = 0; index < chunks.size(); index++) {
            RetrievedChunk chunk = chunks.get(index);
            KnowledgeChunkDO chunkDO = metadata.chunkByVectorId().get(chunk.getId());
            KnowledgeDocumentDO documentDO = chunkDO == null ? null : metadata.documentById().get(chunkDO.getDocId());
            String resolvedChunkId = chunkDO == null ? chunk.getId() : chunkDO.getId();
            views.add(new RagEvalResponse.ChunkView(
                    index + 1,
                    resolvedChunkId,
                    chunk.getId(),
                    chunkDO == null ? null : chunkDO.getDocId(),
                    documentDO == null ? null : documentDO.getDocName(),
                    chunkDO == null ? null : chunkDO.getKbId(),
                    chunk.getScore(),
                    includeContexts ? chunk.getText() : null));
        }
        return views;
    }

    public List<RagEvalResponse.ChannelView> buildChannelViews(
            List<SearchChannelResult> channelResults,
            Metadata metadata,
            boolean includeContexts) {
        if (channelResults == null || channelResults.isEmpty()) {
            return List.of();
        }
        return channelResults.stream()
                .map(channel -> new RagEvalResponse.ChannelView(
                        channel.getChannelName(),
                        channel.getChannelType() == null ? null : channel.getChannelType().name(),
                        channel.getConfidence(),
                        channel.getLatencyMs(),
                        buildChunkViews(
                                channel.getChunks() == null ? List.of() : channel.getChunks(),
                                metadata,
                                includeContexts)))
                .toList();
    }

    private Map<String, KnowledgeChunkDO> resolveChunks(List<RetrievedChunk> chunks) {
        if (chunks.isEmpty()) {
            return Map.of();
        }
        List<String> vectorIds = chunks.stream()
                .map(RetrievedChunk::getId)
                .filter(StrUtil::isNotBlank)
                .distinct()
                .toList();
        Map<String, KnowledgeChunkDO> byDbId = vectorIds.isEmpty()
                ? Map.of()
                : knowledgeChunkDOMapper.selectBatchIds(vectorIds).stream()
                .collect(Collectors.toMap(
                        KnowledgeChunkDO::getId,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new));

        Map<String, KnowledgeChunkDO> result = new LinkedHashMap<>();
        chunks.forEach(chunk -> {
            KnowledgeChunkDO chunkDO = byDbId.get(chunk.getId());
            if (chunkDO != null) {
                result.put(chunk.getId(), chunkDO);
            }
        });

        List<RetrievedChunk> missing = chunks.stream()
                .filter(chunk -> !result.containsKey(chunk.getId()))
                .filter(chunk -> StrUtil.isNotBlank(chunk.getText()))
                .toList();
        if (missing.isEmpty()) {
            return result;
        }

        List<String> hashes = missing.stream()
                .map(chunk -> DigestUtil.sha256Hex(chunk.getText()))
                .distinct()
                .toList();
        if (hashes.isEmpty()) {
            return result;
        }
        Map<String, KnowledgeChunkDO> byHash = knowledgeChunkDOMapper.selectList(
                        Wrappers.lambdaQuery(KnowledgeChunkDO.class)
                                .in(KnowledgeChunkDO::getContentHash, hashes)
                                .eq(KnowledgeChunkDO::getEnabled, 1))
                .stream()
                .collect(Collectors.toMap(
                        KnowledgeChunkDO::getContentHash,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new));
        missing.forEach(chunk -> {
            KnowledgeChunkDO chunkDO = byHash.get(DigestUtil.sha256Hex(chunk.getText()));
            if (chunkDO != null) {
                result.put(chunk.getId(), chunkDO);
            }
        });
        return result;
    }

    private Map<String, KnowledgeDocumentDO> loadDocumentMetadata(Collection<KnowledgeChunkDO> chunks) {
        List<String> documentIds = chunks.stream()
                .map(KnowledgeChunkDO::getDocId)
                .filter(StrUtil::isNotBlank)
                .distinct()
                .toList();
        if (documentIds.isEmpty()) {
            return Map.of();
        }
        return knowledgeDocumentMapper.selectBatchIds(documentIds).stream()
                .collect(Collectors.toMap(
                        KnowledgeDocumentDO::getId,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new));
    }

    public record Metadata(
            Map<String, KnowledgeChunkDO> chunkByVectorId,
            Map<String, KnowledgeDocumentDO> documentById) {
    }
}
