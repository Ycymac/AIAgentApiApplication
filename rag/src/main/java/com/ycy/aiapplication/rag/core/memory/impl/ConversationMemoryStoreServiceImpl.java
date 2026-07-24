package com.ycy.aiapplication.rag.core.memory.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.ycy.aiapplication.framework.convention.ChatMessage;
import com.ycy.aiapplication.rag.config.MemoryProperties;
import com.ycy.aiapplication.rag.control.request.ConversationCreateRequest;
import com.ycy.aiapplication.rag.control.vo.ConversationMessageVO;
import com.ycy.aiapplication.rag.core.memory.ConversationMemoryStoreService;
import com.ycy.aiapplication.rag.dao.entity.ConversationMessageDO;
import com.ycy.aiapplication.rag.enums.ConversationMessageOrder;
import com.ycy.aiapplication.rag.service.ConversationComplexQueryService;
import com.ycy.aiapplication.rag.service.ConversationMessageService;
import com.ycy.aiapplication.rag.service.ConversationService;
import com.ycy.aiapplication.rag.service.bo.ConversationMessageBO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationMemoryStoreServiceImpl implements ConversationMemoryStoreService {

    private final ConversationService conversationService;
    private final ConversationMessageService conversationMessageService;
    private final ConversationComplexQueryService complexQueryService;
    private final MemoryProperties memoryProperties;

    @Override
    public List<ChatMessage> loadHistory(String conversationId, String userId) {
        int maxMessages = resolveMaxHistoryMessages();
        List<ConversationMessageVO> dbMessages = conversationMessageService.listMessages(
                conversationId,
                userId,
                maxMessages,
                ConversationMessageOrder.DESC
        );
        if (CollUtil.isEmpty(dbMessages)) {
            return List.of();
        }
        List<ChatMessage> result = dbMessages.stream()
                .map(this::toChatMessage)
                .filter(this::isHistoryMessage)
                .collect(Collectors.toList());

        return normalizeHistory(result);
    }

    @Override
    public List<ChatMessage> loadHistoryBefore(String conversationId, String userId, String beforeMessageId) {
        List<ConversationMessageDO> records = complexQueryService.listLatestMessagesBeforeId(
                conversationId,
                userId,
                beforeMessageId,
                resolveMaxHistoryMessages()
        );
        return normalizeHistory(toChatMessages(records));
    }

    @Override
    public List<ChatMessage> loadHistoryBetween(String conversationId,
                                                String userId,
                                                String afterMessageId,
                                                String beforeMessageId) {
        List<ConversationMessageDO> records = complexQueryService.listMessagesBetweenIds(
                conversationId,
                userId,
                afterMessageId,
                beforeMessageId
        );
        return normalizeHistory(toChatMessages(records));
    }

    @Override
    public String append(String conversationId, String userId, ChatMessage message) {
        ConversationMessageBO conversationMessage = ConversationMessageBO.builder()
                .conversationId(conversationId)
                .userId(userId)
                .role(message.getRole().name().toLowerCase())
                .content(message.getContent())
                .build();
        String messageId = conversationMessageService.addMessage(conversationMessage);

        //当前是用户回答，更新活跃时间
        if (message.getRole() == ChatMessage.Role.USER) {
            ConversationCreateRequest conversation = ConversationCreateRequest.builder()
                    .conversationId(conversationId)
                    .userId(userId)
                    .question(message.getContent())
                    .lastTime(new Date())
                    .build();
            conversationService.createOrUpdate(conversation);
        }
        return messageId;
    }

    @Override
    public void refreshCache(String conversationId, String userId) {
        //当前是直接读取数据库，无需缓存
    }

    /**
     * 对话消息视图转换为消息对话实体
     */
    private ChatMessage toChatMessage(ConversationMessageVO record) {
        if (record == null || StrUtil.isBlank(record.getContent())) {
            return null;
        }
        ChatMessage.Role role = ChatMessage.Role.fromString(record.getRole());
        return new ChatMessage(role, record.getContent());
    }

    private List<ChatMessage> toChatMessages(List<ConversationMessageDO> records) {
        if (CollUtil.isEmpty(records)) {
            return List.of();
        }
        return records.stream()
                .filter(record -> record != null && StrUtil.isNotBlank(record.getContent()))
                .map(record -> new ChatMessage(
                        ChatMessage.Role.fromString(record.getRole()),
                        record.getContent()
                ))
                .filter(this::isHistoryMessage)
                .toList();
    }

    /**
     * 归一化消息
     * <p>
     *     1.清除消息当中的系统消息
     *     2.清除消息当中的不完整消息
     */
    private List<ChatMessage> normalizeHistory(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        List<ChatMessage> cleaned = messages.stream()
                .filter(this::isHistoryMessage)
                .toList();
        if (cleaned.isEmpty()) {
            return List.of();
        }
        int start = 0;
        //assistant消息开始，说明完整的消息被截断了，应当是用户开头
        while (start < cleaned.size() && cleaned.get(start).getRole() == ChatMessage.Role.ASSISTANT) {
            start++;
        }

        if (start >= cleaned.size()) {
            return List.of();
        }
        return cleaned.subList(start, cleaned.size());
    }

    /**
     * 判断当前是否为历史消息，身份设定等系统消息不需要作为历史记录的一部分
     */
    private boolean isHistoryMessage(ChatMessage message) {
        return message != null
                //防御：防止将系统消息纳入历史记录（实际上并不会将其落库）
                && (message.getRole() == ChatMessage.Role.USER || message.getRole() == ChatMessage.Role.ASSISTANT)
                && StrUtil.isNotBlank(message.getContent());
    }

    /**
     * 解析最大历史消息数量
     * 最大消息数量是保存消息轮数的两倍（用户消息和大模型回答）
     */
    private int resolveMaxHistoryMessages() {
        int maxTurns = memoryProperties.getHistoryKeepTurns();
        return maxTurns * 2;
    }
}
