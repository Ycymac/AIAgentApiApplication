package com.ycy.aiapplication.framework.mq.base;

import lombok.*;
import reactor.util.annotation.NonNull;

import java.io.Serial;
import java.io.Serializable;

/**
 * 消息队列统一消息封装
 * 最终放入消息队列的标准消息体
 * @param <T> 业务载荷类型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@RequiredArgsConstructor
public class MessageWrapper<T> implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 消息发送keys
     */
    @NonNull
    private String keys;

    /**
     * 消息体
     */
    @NonNull
    private T message;

    /**
     * 消息发送时间
     * 等于当前时间戳
     */
    @Builder.Default
    private long timestamp=System.currentTimeMillis();

}
