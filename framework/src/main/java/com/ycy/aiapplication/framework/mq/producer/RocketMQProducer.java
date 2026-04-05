package com.ycy.aiapplication.framework.mq.producer;


import com.ycy.aiapplication.framework.mq.base.BaseSendExtendDTO;
import org.apache.rocketmq.client.producer.SendResult;
import org.springframework.messaging.Message;

/**
 * 消息队列生产者规范接口
 */
public interface RocketMQProducer<T>{

    /**
     * 构建消息发送时间基础扩充属性实体
     * @param messageSendEvent 消息发送事件
     * @return 扩充属性实体
     */
    BaseSendExtendDTO buildBaseSendDTO(T messageSendEvent);

    /**
     * 构建消息基础参数：请求头，参数
     * @param messageEvent 消息发送事件
     * @param requestParam 扩充属性实体
     * @return 消息参数
     */
    Message<?> buildMessage(T messageEvent, BaseSendExtendDTO requestParam);

    /**
     * 消息发送标准定义
     * @param messageEvent 消息发送时间
     * @return 消息发送结果
     */
    SendResult sendMessage(T messageEvent,BaseSendExtendDTO requestParam);

}
