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

package com.ycy.aiapplication.rag.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ycy.aiapplication.rag.dao.entity.ConversationDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ConversationMapper extends BaseMapper<ConversationDO> {

    @Update("""
            UPDATE conversation
            SET conversation_preferences = #{preferencesJson},
                preference_version = preference_version + 1,
                update_time = NOW()
            WHERE conversation_id = #{conversationId}
              AND user_id = #{userId}
              AND deleted = 0
              AND preference_version = #{expectedPreferenceVersion}
            """)
    int compareAndSetPreferences(@Param("conversationId") String conversationId,
                                 @Param("userId") String userId,
                                 @Param("expectedPreferenceVersion") long expectedPreferenceVersion,
                                 @Param("preferencesJson") String preferencesJson);

    @Update("""
            UPDATE conversation
            SET summary = #{summary},
                conversation_preferences = #{preferencesJson},
                last_message_id = #{targetMessageId},
                preference_version = preference_version + 1,
                update_time = NOW()
            WHERE conversation_id = #{conversationId}
              AND user_id = #{userId}
              AND deleted = 0
              AND last_message_id <=> #{snapshotLastMessageId}
              AND preference_version = #{expectedPreferenceVersion}
            """)
    int publishMemory(@Param("conversationId") String conversationId,
                      @Param("userId") String userId,
                      @Param("snapshotLastMessageId") String snapshotLastMessageId,
                      @Param("expectedPreferenceVersion") long expectedPreferenceVersion,
                      @Param("summary") String summary,
                      @Param("preferencesJson") String preferencesJson,
                      @Param("targetMessageId") String targetMessageId);
}
