package com.ycy.aiapplication.infrastructure.ai.model;

/**
 * 模型调用器函数式接口
 * 接口定义了一个通用的模型调用方法，用于执行模型操作
 * 通过泛型支持不同类型客户端和返回值，提供扩展能力
 *
 * @param <C>客户端类型，表示用于调用模型的客户端实例
 * @param <T>返回值类型，表示模型调用返回结果
 */
@FunctionalInterface
public interface ModelCaller<C, T> {
    /**
     * 模型调用
     * @param client 模型客户端实例，用于和模型进行交互
     * @param target 模型目标配置
     * @return 模型调用结果
     * @throws Exception 异常
     */
    T call(C client, ModelTarget target) throws Exception;
}
