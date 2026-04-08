package com.ycy.aiapplication.infrastructure.ai.toolkit;

import java.util.regex.Pattern;

/**
 * LLM 输出清理工具类
 */
public final class LLMResponseCleaner {
    /**
     * 正则表达式：
     * 1. ^     匹配字符串开始
     * 2. ```   匹配三个反引号
     * 3. [\w-]* 匹配代码语言标识 \w表示字符，前面再加上\用于转义表示其不是一个普通字符而是正则语法 ，等价于[a-zA-Z0-9_-](也就是大小写字符、数字以及下划线和短横线)
     *  后面的*表示可选，可以重复0或者多次（1、2、3、4……）
     * 4. \s*   匹配可选空格
     * 5. \n?   匹配可选换行（？表示0或1个）
     * 6. $     表示字符串结尾
     */
    private static final Pattern LEADING_CODE_FENCE = Pattern.compile("^```[\\w-]*\\s*\\n?");
    private static final Pattern TRAILING_CODE_FENCE = Pattern.compile("\\n?```\\s*$");

    private LLMResponseCleaner() {
    }

    /**
     * 大模型喜欢使用markdown格式输出，即使我们强制其输出json格式
     * 移除 Markdown 代码块围栏（例如 ```json ... ```）
     */
    public static String stripMarkdownCodeFence(String raw) {
        if (raw == null) {
            return null;
        }
        //去除LLM输出的首位空白
        String cleaned = raw.trim();
        //清除对应的md格式
        cleaned = LEADING_CODE_FENCE.matcher(cleaned).replaceFirst("");
        cleaned = TRAILING_CODE_FENCE.matcher(cleaned).replaceFirst("");
        return cleaned.trim();
    }
}
