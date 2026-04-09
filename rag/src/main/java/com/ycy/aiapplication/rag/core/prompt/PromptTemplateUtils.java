/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ycy.aiapplication.rag.core.prompt;

import cn.hutool.core.util.StrUtil;

import java.util.Map;
import java.util.regex.Pattern;

public final class PromptTemplateUtils {
    //3个及以上的换行
    private static final Pattern MULTI_BLANK_LINES = Pattern.compile("(\\n){3,}");

    /**
     * 提示词清洗
     * @param prompt 提示词模板
     */
    public static String cleanupPrompt(String prompt) {

        if (prompt == null) {
            return "";
        }
        //将多换行替换为双换行
        return MULTI_BLANK_LINES.matcher(prompt).replaceAll("\n\n").trim();
    }

    /**
     * 提示词模板填充
     * @param template 模板
     * @param slots 槽位映射
     */
    public static String fillSlots(String template, Map<String, String> slots) {
        if (template == null) {
            return "";
        }
        if (slots == null || slots.isEmpty()) {
            return template;
        }
        String result = template;
        for (Map.Entry<String, String> entry : slots.entrySet()) {
            //获取替换value，value为null则返回空字符串
            String value = StrUtil.emptyIfNull(entry.getValue());
            //替换位置使用{}括起来了，使用replace方法进行替换
            result = result.replace("{" + entry.getKey() + "}", value);
        }
        return result;
    }
}
