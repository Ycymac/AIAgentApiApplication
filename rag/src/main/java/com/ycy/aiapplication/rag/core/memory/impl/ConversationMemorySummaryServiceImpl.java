package com.ycy.aiapplication.rag.core.memory.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.ycy.aiapplication.framework.convention.ChatMessage;
import com.ycy.aiapplication.framework.convention.ChatRequest;
import com.ycy.aiapplication.infrastructure.ai.chat.interfaces.LLMService;
import com.ycy.aiapplication.rag.config.MemoryProperties;
import com.ycy.aiapplication.rag.core.memory.ConversationMemorySummaryService;
import com.ycy.aiapplication.rag.core.prompt.PromptTemplateLoader;
import com.ycy.aiapplication.rag.dao.entity.ConversationMessageDO;
import com.ycy.aiapplication.rag.dao.entity.ConversationSummaryDO;
import com.ycy.aiapplication.rag.service.ConversationComplexQueryService;
import com.ycy.aiapplication.rag.service.ConversationMessageService;
import com.ycy.aiapplication.rag.service.bo.ConversationSummaryBO;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.ycy.aiapplication.rag.constant.RAGConstant.CONVERSATION_SUMMARY_PROMPT_PATH;

@Slf4j
@Service
public class ConversationMemorySummaryServiceImpl implements ConversationMemorySummaryService {

    private static final String SUMMARY_PREFIX = "对话摘要：";
    private static final String SUMMARY_LOCK_PREFIX = "ai:application:memory:summary:lock:";
    private static final Duration SUMMARY_LOCK_TTL = Duration.ofMinutes(5);

    private final ConversationComplexQueryService conversationGroupService;
    private final ConversationMessageService conversationMessageService;
    private final MemoryProperties memoryProperties;
    private final LLMService llmService;
    private final PromptTemplateLoader promptTemplateLoader;
    private final RedissonClient redissonClient;

    @Qualifier("memorySummaryThreadPoolExecutor")
    private final Executor memorySummaryExecutor;

    public ConversationMemorySummaryServiceImpl(ConversationComplexQueryService conversationGroupService,
                                                ConversationMessageService conversationMessageService,
                                                MemoryProperties memoryProperties, LLMService llmService,
                                                PromptTemplateLoader promptTemplateLoader,
                                                RedissonClient redissonClient,
                                                @Qualifier("memorySummaryThreadPoolExecutor") Executor memorySummaryExecutor) {
        this.conversationGroupService = conversationGroupService;
        this.conversationMessageService = conversationMessageService;
        this.memoryProperties = memoryProperties;
        this.llmService = llmService;
        this.promptTemplateLoader = promptTemplateLoader;
        this.redissonClient = redissonClient;
        this.memorySummaryExecutor = memorySummaryExecutor;
    }

    /**
     * 判断是否需要执行摘要压缩，需要的话使用CompletableFuture异步执行
     */
    @Override
    public void compressIfNeeded(String conversationId, String userId, ChatMessage message) {
        if (!memoryProperties.getSummaryEnabled()) {
            return;
        }
        if (message.getRole() != ChatMessage.Role.ASSISTANT) {
            return;
        }
        CompletableFuture.runAsync(() -> doCompressIfNeeded(conversationId, userId), memorySummaryExecutor)
                .exceptionally(ex -> {
                    log.error("对话记忆摘要异步任务失败 - conversationId: {}, userId: {}",
                            conversationId, userId, ex);
                    return null;
                });
    }

    /**
     * 加载最新总结
     */
    @Override
    public ChatMessage loadLatestSummary(String conversationId, String userId) {
        ConversationSummaryDO summary = conversationGroupService.findLatestSummary(conversationId, userId);
        return toChatMessage(summary);
    }

    /**
     * 为摘要总结添加开始前缀
     */
    @Override
    public ChatMessage decorateIfNeeded(ChatMessage summary) {
        if (summary == null || StrUtil.isBlank(summary.getContent())) {
            return summary;
        }

        String content = summary.getContent().trim();
        if (content.startsWith(SUMMARY_PREFIX) || content.startsWith("摘要：")) {
            return summary;
        }
        return ChatMessage.system(SUMMARY_PREFIX + content);
    }

    /**
     * 摘要压缩核心逻辑，判断当前是否需要执行摘要、确定摘要范围、调用LLM生成摘要并保存
     * @param conversationId 对话id
     * @param userId 用户id
     */
    private void doCompressIfNeeded(String conversationId, String userId) {
        long startTime = System.currentTimeMillis();
        int triggerTurns = memoryProperties.getSummaryStartTurns();
        int maxTurns = memoryProperties.getHistoryKeepTurns();
        if (maxTurns <= 0 || triggerTurns <= 0) {
            return;
        }

        String lockKey = SUMMARY_LOCK_PREFIX + buildLockKey(conversationId, userId);
        RLock lock = redissonClient.getLock(lockKey);
        if (!tryLock(lock)) {
            return;
        }
        try {
            //统计消息检查是否需要触发总结
            long total = conversationGroupService.countUserMessages(conversationId, userId);
            if (total < triggerTurns) {
                return;
            }
            //获取最后一次摘要
            ConversationSummaryDO latestSummary = conversationGroupService.findLatestSummary(conversationId, userId);
            //获取限制的最大轮数的用户消息
            List<ConversationMessageDO> latestUserTurns = conversationGroupService.listLatestUserOnlyMessages(
                    conversationId,
                    userId,
                    maxTurns
            );
            if (latestUserTurns.isEmpty()) {
                return;
            }
            //确定摘要结束断电
            String cutoffId = resolveCutoffId(latestUserTurns);
            if (StrUtil.isBlank(cutoffId)) {
                return;
            }
            //确定摘要起始断电
            String afterId = resolveSummaryStartId(conversationId, userId, latestSummary);
            if (afterId != null && Long.parseLong(afterId) >= Long.parseLong(cutoffId)) {
                return;
            }

            List<ConversationMessageDO> toSummarize = conversationGroupService.listMessagesBetweenIds(
                    conversationId,
                    userId,
                    afterId,
                    cutoffId
            );
            if (CollUtil.isEmpty(toSummarize)) {
                return;
            }
            //找到摘要中最后一条消息id用于更新摘游标
            String lastMessageId = resolveLastMessageId(toSummarize);
            if (StrUtil.isBlank(lastMessageId)) {
                return;
            }
            //执行摘要总结
            String existingSummary = latestSummary == null ? "" : latestSummary.getContent();
            String summary = summarizeMessages(toSummarize, existingSummary);
            if (StrUtil.isBlank(summary)) {
                return;
            }

            createSummary(conversationId, userId, summary, lastMessageId);
            log.info("摘要成功 - conversationId：{}，userId：{}，消息数：{}，耗时：{}ms",
                    conversationId, userId, toSummarize.size(),
                    System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            log.error("摘要失败 - conversationId：{}，userId：{}", conversationId, userId, e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 5分钟内尝试获取redisson分布式锁,获取不到直接失败
     */
    private boolean tryLock(RLock lock) {
        try {
            //第一个参数：等待时间
            //第二个参数：锁的过期时间值，到期自动过期，过期并不开启看门狗过期机制
            return lock.tryLock(0, SUMMARY_LOCK_TTL.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * LLM生成消息总结
     * @param messages 数据库对话消息记录列表
     * @param existingSummary 存在的消息总结
     */
    private String summarizeMessages(List<ConversationMessageDO> messages, String existingSummary) {
        List<ChatMessage> histories = toHistoryMessages(messages);
        //历史记录缺失，使用当前存在的总结
        if (CollUtil.isEmpty(histories)) {
            return existingSummary;
        }
        //构建总结生成提示词
        int summaryMaxChars = memoryProperties.getSummaryMaxChars();
        List<ChatMessage> summaryMessages = new ArrayList<>();
        String summaryPrompt = promptTemplateLoader.render(
                CONVERSATION_SUMMARY_PROMPT_PATH,
                Map.of("summary_max_chars", String.valueOf(summaryMaxChars))
        );
        summaryMessages.add(ChatMessage.system(summaryPrompt));

        if (StrUtil.isNotBlank(existingSummary)) {
            summaryMessages.add(ChatMessage.assistant(
                    "历史摘要（仅用于合并去重，不得作为事实新增来源；若与本轮对话冲突，以本轮对话为准）：\n"
                            + existingSummary.trim()
            ));
        }
        summaryMessages.addAll(histories);
        summaryMessages.add(ChatMessage.user(
                "合并以上对话与历史摘要，去重后输出更新摘要。要求：严格≤" + summaryMaxChars + "字符；仅一行。"
        ));

        ChatRequest request = ChatRequest.builder()
                .messages(summaryMessages)
                .temperature(0.3D)
                .topP(0.9D)
                .thinking(false)
                .build();
        try {
            String result = llmService.chat(request);
            log.info("对话摘要生成 - resultChars: {}", result.length());

            return result;
        } catch (Exception e) {
            log.error("对话记忆摘要生成失败, conversationId相关消息数: {}", messages.size(), e);
            return existingSummary;
        }
    }


    /**
     * 将数据库消息实体转换为LLM可用的ChatMessage列表
     * @param messages 数据库消息实体列表
     */
    private List<ChatMessage> toHistoryMessages(List<ConversationMessageDO> messages) {
        if (CollUtil.isEmpty(messages)) {
            return List.of();
        }
        return messages.stream()
                .filter(item -> item != null
                        && StrUtil.isNotBlank(item.getContent())
                        && StrUtil.isNotBlank(item.getRole()))
                .map(item -> {
                    String role = item.getRole().toLowerCase();
                    if ("user".equals(role)) {
                        return ChatMessage.user(item.getContent());
                    } else if ("assistant".equals(role)) {
                        return ChatMessage.assistant(item.getContent());
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 将对话记录转换为系统的对话消息对象
     * @param record 对话摘要记录
     */
    private ChatMessage toChatMessage(ConversationSummaryDO record) {
        if (record == null || StrUtil.isBlank(record.getContent())) {
            return null;
        }
        return new ChatMessage(ChatMessage.Role.SYSTEM, record.getContent());
    }

    /**
     * 确定本次摘要开始id
     * @param conversationId 会话id
     * @param userId 用户id
     * @param summary 上次生成摘要记录
     */
    private String resolveSummaryStartId(String conversationId, String userId, ConversationSummaryDO summary) {
        //无历史记录，从头开始
        if (summary == null) {
            return null;
        }
        //摘要当中记录的last message id，优先使用
        if (summary.getLastMessageId() != null) {
            return summary.getLastMessageId();
        }
        //使用摘要更新时间/创建时间作为查询的记录游标
        Date after = summary.getUpdateTime();
        if (after == null) {
            after = summary.getCreateTime();
        }
        //查询这个事件之前的最大消息id
        return conversationGroupService.findMaxMessageIdAtOrBefore(conversationId, userId, after);
    }

    /**
     * 确定摘要截断点
     * @param latestUserTurns 倒序排列的最近N轮用户消息列表
     * @return 截断点对应的id
     */
    private String resolveCutoffId(List<ConversationMessageDO> latestUserTurns) {
        if (CollUtil.isEmpty(latestUserTurns)) {
            return null;
        }

        // 倒序列表的最后一个就是最早的
        ConversationMessageDO oldest = latestUserTurns.get(latestUserTurns.size() - 1);
        return oldest == null ? null : oldest.getId();
    }


    /**
     * 从待摘要消息当中找出最后一条消息id，用于更新摘要游标
     */
    private String resolveLastMessageId(List<ConversationMessageDO> toSummarize) {
        for (int i = toSummarize.size() - 1; i >= 0; i--) {
            ConversationMessageDO item = toSummarize.get(i);
            if (item != null && item.getId() != null) {
                return item.getId();
            }
        }
        return null;
    }

    /**
     * 创建并持久化摘要总结
     */
    private void createSummary(String conversationId,
                               String userId,
                               String content,
                               String lastMessageId) {
        ConversationSummaryBO summaryRecord = ConversationSummaryBO.builder()
                .conversationId(conversationId)
                .userId(userId)
                .content(content)
                .lastMessageId(lastMessageId)
                .build();
        conversationMessageService.addMessageSummary(summaryRecord);
    }

    /**
     * 构建锁密钥
     */
    private String buildLockKey(String conversationId, String userId) {
        return userId.trim() + ":" + conversationId.trim();
    }
}
