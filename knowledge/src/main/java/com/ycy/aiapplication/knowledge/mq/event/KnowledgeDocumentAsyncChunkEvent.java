package com.ycy.aiapplication.knowledge.mq.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 分块异步解耦事件
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeDocumentAsyncChunkEvent {
        private String docId;
}
