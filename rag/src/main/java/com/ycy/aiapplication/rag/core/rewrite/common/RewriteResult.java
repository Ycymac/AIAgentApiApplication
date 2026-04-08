package com.ycy.aiapplication.rag.core.rewrite.common;

import java.util.List;

/**
 * 重写后的问题记录类
 * @param rewrittenQuestion 重写后的问题
 * @param subQuestions 重写之后拆分的问题列表
 */
public record RewriteResult(String rewrittenQuestion, List<String> subQuestions) {

}