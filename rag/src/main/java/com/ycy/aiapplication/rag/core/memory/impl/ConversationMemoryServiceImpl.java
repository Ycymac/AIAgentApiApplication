package com.ycy.aiapplication.rag.core.memory.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.ycy.aiapplication.framework.convention.ChatMessage;
import com.ycy.aiapplication.rag.core.memory.ConversationMemoryService;
import com.ycy.aiapplication.rag.core.memory.ConversationMemoryStoreService;
import com.ycy.aiapplication.rag.core.memory.ConversationMemorySummaryService;
import com.ycy.aiapplication.rag.core.memory.common.ConversationMemoryContext;
import com.ycy.aiapplication.rag.core.memory.common.PreferenceItem;
import com.ycy.aiapplication.rag.core.memory.support.ConversationPreferenceCodec;
import com.ycy.aiapplication.rag.core.memory.support.MemoryLockKeys;
import com.ycy.aiapplication.rag.dao.entity.ConversationDO;
import com.ycy.aiapplication.rag.service.ConversationComplexQueryService;
import com.ycy.aiapplication.rag.service.ConversationService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class ConversationMemoryServiceImpl implements ConversationMemoryService {

    private static final String SUMMARY_PREFIX = "对话摘要：";
    private static final String PREFERENCE_PREFIX =
            "会话偏好（只约束回答方式，不是知识事实）：";

    private final ConversationMemoryStoreService memoryStore;
    private final ConversationMemorySummaryService summaryService;
    private final ConversationComplexQueryService complexQueryService;
    private final ConversationService conversationService;
    private final RedissonClient redissonClient;
    private final Executor memoryExecutor;

    public ConversationMemoryServiceImpl(
            ConversationMemoryStoreService memoryStore,
            ConversationMemorySummaryService summaryService,
            ConversationComplexQueryService complexQueryService,
            ConversationService conversationService,
            RedissonClient redissonClient,
            @Qualifier("memorySummaryThreadPoolExecutor") Executor memoryExecutor) {
        this.memoryStore = memoryStore;
        this.summaryService = summaryService;
        this.complexQueryService = complexQueryService;
        this.conversationService = conversationService;
        this.redissonClient = redissonClient;
        this.memoryExecutor = memoryExecutor;
    }

    @Override
    public List<ChatMessage> load(String conversationId, String userId) {
        if (StrUtil.isBlank(conversationId) || StrUtil.isBlank(userId)) {
            return List.of();
        }
        try {
            ConversationDO state = complexQueryService.findConversation(conversationId, userId);
            return buildHistory(state, memoryStore.loadHistory(conversationId, userId));
        } catch (Exception ex) {
            log.error("加载对话记忆失败 - conversationId: {}, userId: {}", conversationId, userId, ex);
            return List.of();
        }
    }

    @Override
    public String append(String conversationId, String userId, ChatMessage message) {
        String messageId = appendWithoutCompression(conversationId, userId, message);
        summaryService.compressIfNeeded(conversationId, userId, message, messageId);
        return messageId;
    }

    @Override
    public String appendWithoutCompression(String conversationId, String userId, ChatMessage message) {
        if (StrUtil.isBlank(conversationId) || StrUtil.isBlank(userId) || message == null) {
            return null;
        }
        return memoryStore.append(conversationId, userId, message);
    }

    @Override
    public ConversationMemoryContext appendUserAndLoad(String conversationId, String userId, String question) {
        String currentMessageId = appendWithoutCompression(
                conversationId,
                userId,
                ChatMessage.user(question)
        );
        ConversationDO state = complexQueryService.findConversation(conversationId, userId);
        List<ChatMessage> rawHistory = memoryStore.loadHistoryBefore(
                conversationId,
                userId,
                currentMessageId
        );
        List<PreferenceItem> preferences = parsePreferences(state);
        return ConversationMemoryContext.builder()
                .summary(state == null ? null : state.getSummary())
                .preferences(preferences)
                .lastMessageId(state == null ? null : state.getLastMessageId())
                .preferenceVersion(resolvePreferenceVersion(state))
                .history(buildHistory(state, rawHistory))
                .currentMessageId(currentMessageId)
                .build();
    }

    @Override
    public List<ChatMessage> loadExpandedHistory(String conversationId,
                                                 String userId,
                                                 ConversationMemoryContext context) {
        if (context == null || StrUtil.isBlank(context.getCurrentMessageId())) {
            return List.of();
        }
        List<ChatMessage> rawHistory = memoryStore.loadHistoryBetween(
                conversationId,
                userId,
                context.getLastMessageId(),
                context.getCurrentMessageId()
        );
        ConversationDO state = complexQueryService.findConversation(conversationId, userId);
        return buildHistory(state, rawHistory);
    }

    @Override
    public void appendPreferencesAsync(String conversationId,
                                       String userId,
                                       String sourceMessageId,
                                       List<String> preferences) {
        if (StrUtil.isBlank(sourceMessageId) || CollUtil.isEmpty(preferences)) {
            return;
        }
        CompletableFuture.runAsync(
                () -> appendPreferences(conversationId, userId, sourceMessageId, preferences),
                memoryExecutor
        ).exceptionally(ex -> {
            log.error("异步追加会话偏好失败 - conversationId: {}, userId: {}",
                    conversationId, userId, ex);
            return null;
        });
    }

    private void appendPreferences(String conversationId,
                                   String userId,
                                   String sourceMessageId,
                                   List<String> preferences) {
        RLock lock = redissonClient.getLock(MemoryLockKeys.update(conversationId, userId));
        boolean locked = false;
        try {
            locked = lock.tryLock(0, 10, TimeUnit.SECONDS);
            if (!locked) {
                log.warn("会话偏好更新锁竞争失败 - conversationId: {}, userId: {}", conversationId, userId);
                return;
            }
            for (int attempt = 0; attempt < 2; attempt++) {
                ConversationDO latest = complexQueryService.findConversation(conversationId, userId);
                if (latest == null) {
                    return;
                }
                List<PreferenceItem> current = parsePreferences(latest);
                List<PreferenceItem> appended = ConversationPreferenceCodec.append(
                        current,
                        sourceMessageId,
                        preferences
                );
                if (appended.equals(current)) {
                    return;
                }
                int updated = conversationService.compareAndSetPreferences(
                        conversationId,
                        userId,
                        resolvePreferenceVersion(latest),
                        ConversationPreferenceCodec.toJson(appended)
                );
                if (updated == 1) {
                    log.info("会话偏好追加成功 - conversationId: {}, added: {}",
                            conversationId, appended.size() - current.size());
                    return;
                }
            }
            log.warn("会话偏好 CAS 连续失败 - conversationId: {}, userId: {}", conversationId, userId);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            log.error("会话偏好追加失败 - conversationId: {}, userId: {}", conversationId, userId, ex);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private List<ChatMessage> buildHistory(ConversationDO state, List<ChatMessage> rawHistory) {
        List<ChatMessage> result = new ArrayList<>();
        if (state != null && StrUtil.isNotBlank(state.getSummary())) {
            result.add(ChatMessage.system(SUMMARY_PREFIX + state.getSummary().trim()));
        }
        List<PreferenceItem> preferences = parsePreferences(state);
        if (!preferences.isEmpty()) {
            String content = preferences.stream()
                    .map(PreferenceItem::getContent)
                    .map(item -> "- " + item)
                    .reduce((left, right) -> left + "\n" + right)
                    .orElse("");
            result.add(ChatMessage.system(PREFERENCE_PREFIX + "\n" + content));
        }
        if (CollUtil.isNotEmpty(rawHistory)) {
            result.addAll(rawHistory);
        }
        return List.copyOf(result);
    }

    private List<PreferenceItem> parsePreferences(ConversationDO state) {
        if (state == null || StrUtil.isBlank(state.getConversationPreferences())) {
            return List.of();
        }
        try {
            return ConversationPreferenceCodec.parse(state.getConversationPreferences());
        } catch (Exception ex) {
            log.warn("会话偏好 JSON 无法解析，跳过本次读取 - conversationId: {}",
                    state.getConversationId(), ex);
            return List.of();
        }
    }

    private long resolvePreferenceVersion(ConversationDO state) {
        return state == null || state.getPreferenceVersion() == null ? 0L : state.getPreferenceVersion();
    }
}
