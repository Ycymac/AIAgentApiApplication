package com.ycy.aiapplication.infrastructure.ai.chat.interfaces;

/**
 * 流式请求取消句柄。
 * 为调用方提供一个统一入口，用于中断仍在运行的流式模型调用。
 */
public interface StreamCancellationHandle {

    /**
     * 取消当前流式推理任务。
     */
    void cancel();
}
