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

package com.ycy.aiapplication.rag.core.guidance;

import lombok.Getter;

/**
 * 引导式问答决策结果。
 * <p>
 * 该类用于封装 guidance 服务的最终判断结果，
 * 明确当前请求是否需要返回引导式提示，以及在需要提示时应返回的 prompt 内容。
 */
@Getter
public class GuidanceDecision {

    /**
     * 引导决策动作枚举。
     * <p>
     * {@link #NONE} 表示不触发引导式回复；
     * {@link #PROMPT} 表示需要向用户输出引导式提示语。
     */
    public enum Action {
        NONE,
        PROMPT
    }

    /**
     * 当前决策动作。
     */
    private final Action action;

    /**
     * 需要返回给用户的引导式提示内容。
     * 当 {@link #action} 为 {@link Action#NONE} 时通常为空。
     */
    private final String prompt;

    /**
     * 构造引导决策对象。
     *
     * @param action 当前引导决策动作
     * @param prompt 引导提示内容；若不需要提示可为 {@code null}
     */
    private GuidanceDecision(Action action, String prompt) {
        this.action = action;
        this.prompt = prompt;
    }

    /**
     * 创建“不触发引导”的决策结果。
     *
     * @return 一个动作为 {@link Action#NONE} 的决策对象
     */
    public static GuidanceDecision none() {
        return new GuidanceDecision(Action.NONE, null);
    }

    /**
     * 创建“输出引导提示”的决策结果。
     *
     * @param prompt 需要返回给用户的引导式提示语
     * @return 一个动作为 {@link Action#PROMPT} 的决策对象
     */
    public static GuidanceDecision prompt(String prompt) {
        return new GuidanceDecision(Action.PROMPT, prompt);
    }

    /**
     * 判断当前是否需要输出引导式提示。
     *
     * @return 返回 {@code true} 表示需要向用户输出 prompt；否则表示继续走常规聊天链路
     */
    public boolean isPrompt() {
        return action == Action.PROMPT;
    }
}
