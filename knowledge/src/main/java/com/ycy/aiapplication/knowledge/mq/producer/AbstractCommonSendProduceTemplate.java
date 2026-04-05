package com.ycy.aiapplication.knowledge.mq.producer;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;

import com.ycy.aiapplication.framework.mq.base.BaseSendExtendDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.Message;

@RequiredArgsConstructor
@Slf4j
public abstract  class AbstractCommonSendProduceTemplate<T>{
    private final RocketMQTemplate rocketMQTemplate;

    /**
     * 构建消息发送事件基础扩充性实体
     * @param messageSendEvent 消息发送事件
     * @return 扩充属性实体
     */
    protected abstract BaseSendExtendDTO buildBaseSendExtendDTO(T messageSendEvent);

    /**
     * 构建消息基本参数：请求头、Keys
     *
     * @param messageSendEvent 消息发送事件
     * @param requestParam 扩充属性实体
     * @return 消息节参数
     */
    protected abstract Message<?> buildMessage(T messageSendEvent, BaseSendExtendDTO requestParam);

    /**
     * 消息通用发送
     * @param messageSendEvent 消息发送事件
     * @return 消息发送结果
     */
    public SendResult sendMessage(T messageSendEvent){
        BaseSendExtendDTO baseSendExtendDTO=buildBaseSendExtendDTO(messageSendEvent);
        SendResult sendResult;
        try{
            //构建Topic目标落点formats：‘topicName：tags’
            StringBuilder destinationBuilder = StrUtil.builder().append(baseSendExtendDTO.getTopic());
            //有标签再添加标签（代码的健壮性）
            if (StrUtil.isNotBlank(baseSendExtendDTO.getTag())) {
                destinationBuilder.append(":").append(baseSendExtendDTO.getTag());
            }

            //延迟时间不为空，发送延迟消息，否则发送普通消息
            if (baseSendExtendDTO.getDelayTime()!=null) {
                sendResult=rocketMQTemplate.syncSendDeliverTimeMills(
                        destinationBuilder.toString(),
                        buildMessage(messageSendEvent,baseSendExtendDTO),
                        baseSendExtendDTO.getDelayTime()
                );
            }
            else{
                //使用topic+tag进行消息发送，类似于rabbit当中的topic exchange+binding key
                sendResult=rocketMQTemplate.syncSend(
                        destinationBuilder.toString(),
                        buildMessage(messageSendEvent,baseSendExtendDTO),
                        baseSendExtendDTO.getSentTimeout()
                );
            }
            log.info("[Knowledge生产者]{}-发送结果：{}，消息ID：{}，消息keys：{}",baseSendExtendDTO.getEventName(),sendResult.getSendStatus(),sendResult.getMsgId(),baseSendExtendDTO.getKeys());

        }catch (Throwable ex)
        {
            log.error("[生产者] {} - 消息发送失败，消息体：{}", baseSendExtendDTO.getEventName(), JSON.toJSONString(messageSendEvent), ex);
            throw ex;
        }
        return sendResult;
    }


}
