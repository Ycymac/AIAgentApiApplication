package com.ycy.aiapplication.rag.service;

import com.ycy.aiapplication.rag.control.vo.ConversationMessageVO;
import com.ycy.aiapplication.rag.enums.ConversationMessageOrder;
import com.ycy.aiapplication.rag.service.bo.ConversationMessageBO;

import java.util.List;

public interface ConversationMessageService {

    /**
     * 新增对话消息
     *
     * @param conversationMessage 消息内容
     */
    String addMessage(ConversationMessageBO conversationMessage);

    /**
     * 获取对话消息列表通用方法（支持排序与数量限制）
     *
     * @param conversationId 对话ID
     * @param userId         用户ID
     * @param limit          限制数量
     * @param order          排序方式
     * @return 对话消息列表
     */
    List<ConversationMessageVO> listMessages(String conversationId, String userId, Integer limit, ConversationMessageOrder order);

}
