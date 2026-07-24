package com.ycy.aiapplication.rag.core.memory.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.ycy.aiapplication.framework.convention.ChatMessage;
import com.ycy.aiapplication.framework.convention.ChatRequest;
import com.ycy.aiapplication.infrastructure.ai.chat.interfaces.LLMService;
import com.ycy.aiapplication.infrastructure.ai.toolkit.LLMResponseCleaner;
import com.ycy.aiapplication.rag.config.MemoryProperties;
import com.ycy.aiapplication.rag.core.memory.ConversationMemorySummaryService;
import com.ycy.aiapplication.rag.core.memory.common.CompressionResult;
import com.ycy.aiapplication.rag.core.memory.common.PreferenceItem;
import com.ycy.aiapplication.rag.core.memory.support.ConversationPreferenceCodec;
import com.ycy.aiapplication.rag.core.memory.support.MemoryLockKeys;
import com.ycy.aiapplication.rag.core.prompt.PromptTemplateLoader;
import com.ycy.aiapplication.rag.dao.entity.ConversationDO;
import com.ycy.aiapplication.rag.dao.entity.ConversationMessageDO;
import com.ycy.aiapplication.rag.service.ConversationComplexQueryService;
import com.ycy.aiapplication.rag.service.ConversationService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static com.ycy.aiapplication.rag.constant.RAGConstant.CONVERSATION_SUMMARY_PROMPT_PATH;

@Slf4j
@Service
public class ConversationMemorySummaryServiceImpl implements ConversationMemorySummaryService {

    private static final Duration SUMMARY_LOCK_TTL = Duration.ofMinutes(5);

    private final ConversationComplexQueryService complexQueryService;
    private final ConversationService conversationService;
    private final MemoryProperties memoryProperties;
    private final LLMService llmService;
    private final PromptTemplateLoader promptTemplateLoader;
    private final RedissonClient redissonClient;
    private final Executor memorySummaryExecutor;

    public ConversationMemorySummaryServiceImpl(
            ConversationComplexQueryService complexQueryService,
            ConversationService conversationService,
            MemoryProperties memoryProperties,
            LLMService llmService,
            PromptTemplateLoader promptTemplateLoader,
            RedissonClient redissonClient,
            @Qualifier("memorySummaryThreadPoolExecutor") Executor memorySummaryExecutor) {
        this.complexQueryService = complexQueryService;
        this.conversationService = conversationService;
        this.memoryProperties = memoryProperties;
        this.llmService = llmService;
        this.promptTemplateLoader = promptTemplateLoader;
        this.redissonClient = redissonClient;
        this.memorySummaryExecutor = memorySummaryExecutor;
    }

    @Override
    public void compressIfNeeded(String conversationId,
                                 String userId,
                                 ChatMessage message,
                                 String messageId) {
        if (!Boolean.TRUE.equals(memoryProperties.getSummaryEnabled())
                || message == null
                || message.getRole() != ChatMessage.Role.ASSISTANT
                || StrUtil.isBlank(message.getContent())
                || StrUtil.isBlank(messageId)) {
            return;
        }
        CompletableFuture.runAsync(
                () -> doCompressIfNeeded(conversationId, userId, messageId),
                memorySummaryExecutor
        ).exceptionally(ex -> {
            log.error("对话记忆摘要异步任务失败 - conversationId: {}, userId: {}",
                    conversationId, userId, ex);
            return null;
        });
    }

    private void doCompressIfNeeded(String conversationId, String userId, String targetMessageId) {
        long startTime = System.currentTimeMillis();
        int triggerTurns = memoryProperties.getSummaryStartTurns();
        if (triggerTurns <= 0) {
            return;
        }

        RLock summaryLock = redissonClient.getLock(MemoryLockKeys.summary(conversationId, userId));
        if (!trySummaryLock(summaryLock)) {
            log.debug("摘要任务锁竞争失败 - conversationId: {}, userId: {}", conversationId, userId);
            return;
        }
        try {
            ConversationDO snapshot = complexQueryService.findConversation(conversationId, userId);
            if (snapshot == null) {
                return;
            }
            List<ConversationMessageDO> messages = complexQueryService.listMessagesAfterThroughId(
                    conversationId,
                    userId,
                    snapshot.getLastMessageId(),
                    targetMessageId
            );
            long userTurns = messages.stream()
                    .filter(item -> item != null && "user".equalsIgnoreCase(item.getRole()))
                    .count();
            if (userTurns < triggerTurns) {
                return;
            }

            List<PreferenceItem> snapshotPreferences = parsePreferences(snapshot);
            CompressionResult candidate = summarizeMessages(
                    messages,
                    snapshot.getSummary(),
                    snapshotPreferences
            );
            if (candidate == null || StrUtil.isBlank(candidate.summary())) {
                return;
            }

            publishCompression(
                    conversationId,
                    userId,
                    snapshot,
                    snapshotPreferences,
                    targetMessageId,
                    candidate
            );
            log.info("摘要更新成功 - conversationId: {}, userId: {}, 消息数: {}, 耗时: {}ms",
                    conversationId, userId, messages.size(), System.currentTimeMillis() - startTime);
        } catch (Exception ex) {
            log.error("摘要更新失败 - conversationId: {}, userId: {}", conversationId, userId, ex);
        } finally {
            if (summaryLock.isHeldByCurrentThread()) {
                summaryLock.unlock();
            }
        }
    }

    private CompressionResult summarizeMessages(List<ConversationMessageDO> messages,
                                                String existingSummary,
                                                List<PreferenceItem> preferences) {
        List<ChatMessage> histories = toHistoryMessages(messages);
        if (CollUtil.isEmpty(histories)) {
            return null;
        }

        int summaryMaxChars = memoryProperties.getSummaryMaxChars();
        List<ChatMessage> summaryMessages = new ArrayList<>();
        summaryMessages.add(ChatMessage.system(promptTemplateLoader.render(
                CONVERSATION_SUMMARY_PROMPT_PATH,
                java.util.Map.of("summary_max_chars", String.valueOf(summaryMaxChars))
        )));
        if (StrUtil.isNotBlank(existingSummary)) {
            summaryMessages.add(ChatMessage.assistant(
                    "历史摘要（只用于合并，不得新增事实）：\n" + existingSummary.trim()
            ));
        }
        summaryMessages.add(ChatMessage.system(
                "当前会话偏好 JSON（与摘要分开处理，整理时保留稳定 id）：\n"
                        + ConversationPreferenceCodec.toJson(preferences)
        ));
        summaryMessages.addAll(histories);
        summaryMessages.add(ChatMessage.user("输出更新后的摘要与整理后的会话偏好 JSON。"));

        ChatRequest request = ChatRequest.builder()
                .messages(summaryMessages)
                .temperature(0.3D)
                .topP(0.9D)
                .thinking(false)
                .build();
        try {
            return parseCompressionResult(llmService.chat(request), summaryMaxChars);
        } catch (Exception ex) {
            log.error("对话记忆摘要生成或解析失败 - messages: {}", messages.size(), ex);
            return null;
        }
    }

    private CompressionResult parseCompressionResult(String raw, int summaryMaxChars) {
        String cleaned = LLMResponseCleaner.stripMarkdownCodeFence(raw);
        JSONObject root = JSON.parseObject(cleaned);
        String summary = root.getString("summary");
        if (StrUtil.isBlank(summary) || summary.length() > summaryMaxChars) {
            throw new IllegalArgumentException("摘要为空或超过长度限制");
        }
        if (!root.containsKey("preferences")) {
            throw new IllegalArgumentException("摘要结果缺少 preferences 字段");
        }
        JSONArray preferenceArray = root.getJSONArray("preferences");
        List<PreferenceItem> preferences = preferenceArray == null
                ? List.of()
                : preferenceArray.toJavaList(PreferenceItem.class);
        return new CompressionResult(
                summary.trim(),
                ConversationPreferenceCodec.normalize(preferences)
        );
    }

    private void publishCompression(String conversationId,
                                    String userId,
                                    ConversationDO snapshot,
                                    List<PreferenceItem> snapshotPreferences,
                                    String targetMessageId,
                                    CompressionResult candidate) {
        RLock updateLock = redissonClient.getLock(MemoryLockKeys.update(conversationId, userId));
        boolean locked = false;
        try {
            locked = updateLock.tryLock(1, 10, TimeUnit.SECONDS);
            if (!locked) {
                log.warn("摘要发布未获取记忆更新锁 - conversationId: {}", conversationId);
                return;
            }
            for (int attempt = 0; attempt < 2; attempt++) {
                ConversationDO latest = complexQueryService.findConversation(conversationId, userId);
                if (latest == null
                        || !Objects.equals(latest.getLastMessageId(), snapshot.getLastMessageId())) {
                    log.info("摘要水位线已变化，丢弃旧候选 - conversationId: {}", conversationId);
                    return;
                }

                List<PreferenceItem> latestPreferences = parsePreferences(latest);
                List<PreferenceItem> finalPreferences = candidate.preferences();
                long snapshotVersion = resolvePreferenceVersion(snapshot);
                long latestVersion = resolvePreferenceVersion(latest);
                if (latestVersion != snapshotVersion) {
                    finalPreferences = ConversationPreferenceCodec.mergeChangesAfterSnapshot(
                            candidate.preferences(),
                            snapshotPreferences,
                            latestPreferences
                    );
                    log.info("摘要发布合并压缩期间新增偏好 - conversationId: {}, merged: {}",
                            conversationId,
                            Math.max(0, finalPreferences.size() - candidate.preferences().size()));
                }

                int updated = conversationService.publishMemory(
                        conversationId,
                        userId,
                        snapshot.getLastMessageId(),
                        latestVersion,
                        candidate.summary(),
                        ConversationPreferenceCodec.toJson(finalPreferences),
                        targetMessageId
                );
                if (updated == 1) {
                    return;
                }
            }
            log.warn("摘要发布 CAS 连续失败 - conversationId: {}, userId: {}", conversationId, userId);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } finally {
            if (locked && updateLock.isHeldByCurrentThread()) {
                updateLock.unlock();
            }
        }
    }

    private List<PreferenceItem> parsePreferences(ConversationDO conversation) {
        return ConversationPreferenceCodec.parse(conversation.getConversationPreferences());
    }

    private long resolvePreferenceVersion(ConversationDO conversation) {
        return conversation.getPreferenceVersion() == null ? 0L : conversation.getPreferenceVersion();
    }

    private List<ChatMessage> toHistoryMessages(List<ConversationMessageDO> messages) {
        if (CollUtil.isEmpty(messages)) {
            return List.of();
        }
        return messages.stream()
                .filter(item -> item != null
                        && StrUtil.isNotBlank(item.getContent())
                        && StrUtil.isNotBlank(item.getRole()))
                .map(item -> {
                    if ("user".equalsIgnoreCase(item.getRole())) {
                        return ChatMessage.user(item.getContent());
                    }
                    if ("assistant".equalsIgnoreCase(item.getRole())) {
                        return ChatMessage.assistant(item.getContent());
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private boolean trySummaryLock(RLock lock) {
        try {
            return lock.tryLock(0, SUMMARY_LOCK_TTL.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
