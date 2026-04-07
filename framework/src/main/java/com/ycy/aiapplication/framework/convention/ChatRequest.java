package com.ycy.aiapplication.framework.convention;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 大模型通用请求对象
 * 用于封装完整对话所需所有上下文以及控制参数，统一传给大模型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatRequest {

    /**
     * 完整消息列表
     * default注解用于防止用户没有给出消息列表，默认使用 new ArrayList（）
     */
    @Builder.Default
    private List<ChatMessage> messages=new ArrayList<>();

    /**
     * 模型提供商
     */
    private String provider;

    /**
     * 模型id
     */
    private String modelId;

    //模型控制参数
    /**
     * 模型温度参数
     */
    private Double temperature;
    /**
     * 模型核采样参数
     */
    private Double topP;
    /**
     * Top-K采样参数
     * 表示每一步只从概率最高的K个token当中采样
     */
    private Integer topK;
    /**
     * 限制模型本次回答最多生成使用的token数量
     */
    private Integer maxTokens;
    /**
     * 可选参数：是否开启深度思考
     * 用于兼容支持思考过程/reasoning扩展能力的模型
     */
    private Boolean thinking;
    /**
     * 可选：是否开启工具调用
     * 方便后续扩展为带工具调用能力的对话请求
     */
    private Boolean enableTools;
}
