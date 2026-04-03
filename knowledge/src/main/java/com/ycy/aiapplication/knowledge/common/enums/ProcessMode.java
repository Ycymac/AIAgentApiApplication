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

package com.ycy.aiapplication.knowledge.common.enums;

import cn.hutool.core.util.StrUtil;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 文档处理模式枚举。
 */
@Getter
@RequiredArgsConstructor
public enum ProcessMode {

    /**
     * 分块处理模式。
     */
    CHUNK("chunk");

    /**
     * 处理模式值。
     */
    private final String value;

    public static ProcessMode fromValue(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase();
        for (ProcessMode mode : values()) {
            if (mode.value.equals(normalized)) {
                return mode;
            }
        }
        return null;
    }

    public static ProcessMode normalize(String value) {
        if (StrUtil.isBlank(value)) {
            throw new IllegalArgumentException("处理模式不能为空");
        }
        ProcessMode result = fromValue(value);
        if (result == null) {
            throw new IllegalArgumentException("仅支持 chunk 处理模式: " + value);
        }
        return result;
    }
}
