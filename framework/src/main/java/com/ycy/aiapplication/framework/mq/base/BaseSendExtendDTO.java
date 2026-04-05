package com.ycy.aiapplication.framework.mq.base;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 消息发送基础扩充实体
 * 类似“快递单”
 * 消息队列相关信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BaseSendExtendDTO {
    /**
     * 事件名称
     */
    private String eventName;

    /**
     * 主题
     */
    private String topic;

    /**
     * 标签
     */
    private String tag;

    /**
     * 业务标识
     */
    private String keys;

    /**
     * 发送消息超时时间
     */
    private Long sentTimeout;

    /**
     * 具体延迟时间
     */
    private Long delayTime;
}
