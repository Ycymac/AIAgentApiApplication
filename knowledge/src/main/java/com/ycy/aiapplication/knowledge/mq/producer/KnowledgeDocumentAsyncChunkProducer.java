package com.ycy.aiapplication.knowledge.mq.producer;

import cn.hutool.core.util.StrUtil;
import com.ycy.aiapplication.framework.mq.base.BaseSendExtendDTO;
import com.ycy.aiapplication.framework.mq.base.MessageWrapper;
import com.ycy.aiapplication.knowledge.mq.event.KnowledgeDocumentAsyncChunkEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageConst;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.util.UUID;

import static com.ycy.aiapplication.knowledge.common.constant.KnowledgeRocketMQConstant.KNOWLEDGE_DOCUMENT_CHUNK_TOPIC_KEY;

@Slf4j
@Component

public class KnowledgeDocumentAsyncChunkProducer extends AbstractCommonSendProduceTemplate<KnowledgeDocumentAsyncChunkEvent> {

    public KnowledgeDocumentAsyncChunkProducer(@Autowired RocketMQTemplate rocketMQTemplate){
        super(rocketMQTemplate);
    }

    @Override
    protected BaseSendExtendDTO buildBaseSendExtendDTO(KnowledgeDocumentAsyncChunkEvent messageSendEvent) {
        return BaseSendExtendDTO.builder()
                .eventName("知识库文件异步分块执行")
                .keys(String.valueOf(messageSendEvent.getDocId()))
                .topic(KNOWLEDGE_DOCUMENT_CHUNK_TOPIC_KEY)
                .sentTimeout(3000L)
                .build();
    }

    @Override
    protected Message<?> buildMessage(KnowledgeDocumentAsyncChunkEvent messageSendEvent, BaseSendExtendDTO requestParam) {
        String keys= StrUtil.isEmpty(requestParam.getKeys())? UUID.randomUUID().toString(): requestParam.getKeys();
        return MessageBuilder
                .withPayload(new MessageWrapper(keys,messageSendEvent))
                .setHeader(MessageConst.PROPERTY_KEYS,keys)
                .setHeader(MessageConst.PROPERTY_TAGS,requestParam.getTag())
                .build();
    }
}
