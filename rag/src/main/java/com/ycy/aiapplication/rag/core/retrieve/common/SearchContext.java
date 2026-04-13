package com.ycy.aiapplication.rag.core.retrieve.common;

import com.ycy.aiapplication.rag.core.intent.common.SubQuestionIntent;
import lombok.Builder;
import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 检索上下文。
 * <p>
 * 整体职责：
 * 1. 统一承载一次检索流程所需的输入参数。
 * 2. 在检索编排器、检索通道与后处理器之间传递共享信息。
 * 3. 避免不同通道各自拼装参数，降低调用链耦合。
 */
@Data
@Builder
public class SearchContext {

    /**
     * 原始问题。
     */
    private String originalQuestion;

    /**
     * 改写后的问题。
     */
    private String rewrittenQuestion;

    /**
     * 子问题列表。
     */
    private List<String> subQuestions;

    /**
     * 意图识别结果列表。
     */
    private List<SubQuestionIntent> intents;

    /**
     * 期望返回的结果数量。
     */
    private int topK;

    /**
     * 扩展元数据。
     */
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    /**
     * 获取本次检索的主问题。
     *
     * @return 优先使用改写后的问题；若为空，则回退到原始问题。
     */
    public String getMainQuestion() {
        return rewrittenQuestion != null ? rewrittenQuestion : originalQuestion;
    }
}
