package com.ycy.aiapplication.knowledge.mq.consumer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ycy.aiapplication.framework.idempotent.annotations.IdempotentConsume;
import com.ycy.aiapplication.framework.mq.base.MessageWrapper;
import com.ycy.aiapplication.knowledge.dao.entity.KnowledgeDocumentDO;
import com.ycy.aiapplication.knowledge.dao.mapper.KnowledgeDocumentMapper;
import com.ycy.aiapplication.knowledge.mq.event.KnowledgeDocumentAsyncChunkEvent;
import com.ycy.aiapplication.knowledge.service.KnowledgeDocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import static com.ycy.aiapplication.knowledge.common.constant.KnowledgeRocketMQConstant.KNOWLEDGE_DOCUMENT_CHUNK_CONSUMER_GROUP_KEY;
import static com.ycy.aiapplication.knowledge.common.constant.KnowledgeRocketMQConstant.KNOWLEDGE_DOCUMENT_CHUNK_TOPIC_KEY;

/**
 * 异步解耦执行文档分块消息消费者。
 */
@Component
@RequiredArgsConstructor
@Slf4j(topic = "KnowledgeDocumentAsyncChunkConsumer")
@RocketMQMessageListener(
        topic = KNOWLEDGE_DOCUMENT_CHUNK_TOPIC_KEY,
        consumerGroup = KNOWLEDGE_DOCUMENT_CHUNK_CONSUMER_GROUP_KEY
)
public class KnowledgeDocumentAsyncChunkEventConsumer implements RocketMQListener<MessageWrapper<KnowledgeDocumentAsyncChunkEvent>> {

    private final KnowledgeDocumentService knowledgeDocumentService;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;

    @Override
    @IdempotentConsume(
            keyPrefix = "knowledge_document_execute:idempotent",
            key = "#messageWrapper.message.docId",
            keyTimeOut = 120
    )
    public void onMessage(MessageWrapper<KnowledgeDocumentAsyncChunkEvent> messageWrapper) {
        if (messageWrapper == null ) {
            log.error("Chunk consumer received empty message");
            return;
        }
        KnowledgeDocumentAsyncChunkEvent message = messageWrapper.getMessage();
        if (message.getDocId() == null) {
            log.error("Chunk consumer received message without docId, messageKeys={}", messageWrapper.getKeys());
            return;
        }

        String docId = message.getDocId();
        KnowledgeDocumentDO documentDO = getKnowledgeDocument(docId);
        if (documentDO == null) {
            log.error("Chunk consumer skipped because document does not exist, docId={}", docId);
            return;
        }

        log.info("Chunk consumer received message, docId={}, messageKeys={}, currentStatus={}",
                docId, messageWrapper.getKeys(), documentDO.getStatus());
        knowledgeDocumentService.executeChunk(docId);
        log.info("Chunk consumer finished message handling, docId={}, messageKeys={}",
                docId, messageWrapper.getKeys());
    }

    private KnowledgeDocumentDO getKnowledgeDocument(String docId) {
        return knowledgeDocumentMapper.selectOne(
                new LambdaQueryWrapper<>(KnowledgeDocumentDO.class).eq(KnowledgeDocumentDO::getId, docId));
    }
}
