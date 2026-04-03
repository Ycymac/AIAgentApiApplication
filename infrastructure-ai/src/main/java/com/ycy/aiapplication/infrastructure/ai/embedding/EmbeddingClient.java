package com.ycy.aiapplication.infrastructure.ai.embedding;



import java.util.List;

/**
 * 文本嵌入客户端接口
 * 用于将文本转换为向量表示，支持单个文本和批量文本的嵌入操作
 * 因为sdk（主）和使用硅基流动（次）的配置不同，在方法中不再加入模型配置
 */
public interface EmbeddingClient {

    /**
     * 获取嵌入服务提供商名称
     *
     * @return 提供商标识字符串
     */
    String provider();

    /**
     * 将单个文本转换为嵌入向量
     *
     * @param text   待嵌入的文本内容
     * @return 文本的向量表示，以浮点数列表形式返回
     */
    List<Float> embed(String text);

    /**
     * 批量将多个文本转换为嵌入向量
     *
     * @param texts  待嵌入的文本列表
     * @return 文本向量列表，每个文本对应一个向量（浮点数列表）
     */
    List<List<Float>> embedBatch(List<String> texts);
}