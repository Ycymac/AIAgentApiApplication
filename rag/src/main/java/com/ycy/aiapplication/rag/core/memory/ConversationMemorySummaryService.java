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

package com.ycy.aiapplication.rag.core.memory;


import com.ycy.aiapplication.framework.convention.ChatMessage;

public interface ConversationMemorySummaryService {

    /**
     * 判断是否需要执行摘要压缩，需要的话使用CompletableFuture异步执行
     */
    void compressIfNeeded(String conversationId, String userId, ChatMessage message);

    /**
     * 加载最新总结
     */
    ChatMessage loadLatestSummary(String conversationId, String userId);

    /**
     * 为摘要总结添加“摘要”前缀
     */
    ChatMessage decorateIfNeeded(ChatMessage summary);
}
