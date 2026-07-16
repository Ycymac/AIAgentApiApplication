package com.ycy.aiapplication.rag.core.retrieve.channel;

import com.ycy.aiapplication.rag.core.retrieve.common.SearchContext;
import com.ycy.aiapplication.rag.core.retrieve.common.QueryEmbeddingContext;
import com.ycy.aiapplication.rag.core.retrieve.common.SearchTask;

import java.util.List;

/**
 * 检索通道统一接口。
 * <p>
 * 整体职责：
 * 1. 抽象不同类型的检索通道，例如全知识库检索、节点定向检索等。
 * 2. 对外暴露统一的启用判断、优先级与执行入口。
 * 3. 便于检索编排器按统一协议调度多通道执行。
 */
public interface SearchChannel {

    /**
     * 获取通道名称。
     *
     * @return 便于日志、监控与调试定位的通道名称。
     */
    String getName();

    /**
     * 获取通道优先级。
     *
     * @return 数值越小优先级越高，编排器会按该顺序执行或处理结果。
     */
    int getPriority();

    /**
     * 判断当前通道在本次检索中是否需要启用。
     *
     * @param context 检索上下文，包含子问题、意图结果与 topK 等信息。
     * @return true 表示当前通道需要参与本轮检索。
     */
    boolean isEnabled(SearchContext context);

    /**
     * 根据当前意图构建通道检索任务，不执行外部检索调用。
     */
    List<SearchTask> plan(SearchContext context);

    /**
     * 使用已规划任务和请求级预计算向量执行检索。
     */
    SearchChannelResult search(SearchContext context,
                               List<SearchTask> tasks,
                               QueryEmbeddingContext embeddingContext);

    /**
     * 获取通道类型。
     *
     * @return 当前通道对应的枚举类型。
     */
    SearchChannelType getType();
}
