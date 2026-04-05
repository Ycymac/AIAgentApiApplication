package com.ycy.aiapplication.framework.idempotent.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * MQ幂等注解，防止消息队列重复消费消息
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface IdempotentConsume {
    /**
     * 设置防重复令牌Key前缀
     * @return
     */
    String keyPrefix()default"";
    /**
     * 通过SpEL生成唯一key
     */
    String key();
    /**
     * 设置防止重复令牌Key过期事件，单位为秒，默认1小时
     * @return
     */
    long keyTimeOut()default 3600L;


}
