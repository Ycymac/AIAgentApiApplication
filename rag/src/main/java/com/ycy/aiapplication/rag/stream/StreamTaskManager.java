package com.ycy.aiapplication.rag.stream;

import cn.hutool.core.util.StrUtil;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.ycy.aiapplication.framework.web.SseEmitterSender;
import com.ycy.aiapplication.infrastructure.ai.chat.interfaces.StreamCancellationHandle;
import com.ycy.aiapplication.rag.enums.SSEEventType;
import com.ycy.aiapplication.rag.stream.common.CompletionPayload;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;


import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * 流式任务管理器
 * <p>
 * 负责一次流式聊天全流程的管理
 * 围绕taskId->StreamTaskInfo的映射进行管理
 *解决的核心问题：
 *1.某个taskId当前是否还在运行
 *2.前端点击停止之后，如何真正取消底层的LLM流
 *3.取消之前生成的内容，如何优雅收尾并通知前端
 */
@Slf4j
@Component
public class StreamTaskManager {
    //Redisson Topic 广播取消消息
    private static final String CANCEL_TOPIC = "ragent:stream:cancel";
    //redis取消前缀，用于持久化取消事实
    private static final String CANCEL_KEY_PREFIX = "ragent:stream:cancel:";
    private static final Duration CANCEL_TTL = Duration.ofMinutes(30);
    //本地内存缓存组件，缓存
    private final Cache<String, StreamTaskInfo> tasks = CacheBuilder.newBuilder()
            .expireAfterWrite(CANCEL_TTL)
            .maximumSize(10000)  // 限制最大数量，基本上不可能超出这个数量。如果觉得不稳妥，可以把值调大并在配置文件声明
            .build();
    private final RedissonClient redissonClient;
    //-1表示没有监听者
    private int listenerId =-1;
    public StreamTaskManager(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    @PostConstruct
    public void subscribe() {
        //启动后订阅topic，返回真实的监听者id
        RTopic topic = redissonClient.getTopic(CANCEL_TOPIC);
        listenerId = topic.addListener(String.class, (channel, taskId) -> {
            if (StrUtil.isBlank(taskId)) {
                return;
            }
            //真正持有对应的taskId才执行取消
            cancelLocal(taskId);
        });
    }

    @PreDestroy
    public void unsubscribe() {
        //没有监听者，直接返回
        if (listenerId == -1) {
            return;
        }
        //有监听者，移除对应的监听者，避免资源泄漏
        redissonClient.getTopic(CANCEL_TOPIC).removeListener(listenerId);
    }

    /**
     * 任务初测
     * @param taskId 任务id
     * @param sender SSE发送者
     * @param onCancelSupplier 使用延迟执行Supplier接口包裹的CompletionPayload
     */
    public void register(String taskId, SseEmitterSender sender, Supplier<CompletionPayload> onCancelSupplier) {
        StreamTaskInfo taskInfo = getOrCreate(taskId);
        //保存sender和onCancelSupplier
        taskInfo.sender = sender;
        taskInfo.onCancelSupplier = onCancelSupplier;
        //检查redis当中是否记载当前任务被取消，被取消则立即发送给前端任务取消并且已经完成，关闭SSE
        if (isTaskCancelledInRedis(taskId, taskInfo)) {
            //任务取消，立即获取任务载荷
            CompletionPayload payload = taskInfo.onCancelSupplier.get();
            sendCancelAndDone(sender, payload);
            //关闭sse
            sender.complete();
        }
    }

    /**
     * 绑定任务和取消句柄
     * @param taskId 任务id
     * @param handle 取消句柄
     */
    public void bindHandle(String taskId, StreamCancellationHandle handle) {
        StreamTaskInfo taskInfo = getOrCreate(taskId);
        taskInfo.handle = handle;
        //当前任务已经结束，直接执行取消句柄当中的结束，防止执行Chat得到句柄之后直接取消了执行
        if (taskInfo.cancelled.get() && handle != null) {
            handle.cancel();
        }
    }


    /**
     * 查询本地任务是否被取消
     */
    public boolean isCancelled(String taskId) {
        StreamTaskInfo info = tasks.getIfPresent(taskId);
        return info != null && info.cancelled.get();
    }

    /**
     * 对外暴露的取消任务方法
     */
    public void cancel(String taskId) {
        // 先设置 Redis 标记，再发布消息（使用Redisson对于String类型的封装Bucket）
        RBucket<Boolean> bucket = redissonClient.getBucket(cancelKey(taskId));
        //set原子操作
        bucket.set(Boolean.TRUE, CANCEL_TTL);

        // 发布消息通知所有节点（包括本地）
        // 本地节点也通过监听器统一处理，避免重复调用 cancelLocal
        redissonClient.getTopic(CANCEL_TOPIC).publish(taskId);
    }

    /**
     * 检查任务是否在 Redis 中被标记为已取消
     * 如果是，会同步状态到本地缓存
     */
    private boolean isTaskCancelledInRedis(String taskId, StreamTaskInfo taskInfo) {
        if (taskInfo.cancelled.get()) {
            return true;
        }

        RBucket<Boolean> bucket = redissonClient.getBucket(cancelKey(taskId));
        Boolean cancelled = bucket.get();
        if (Boolean.TRUE.equals(cancelled)) {
            taskInfo.cancelled.set(true);
            return true;
        }
        return false;
    }

    /**
     * 取消本地任务
     */
    private void cancelLocal(String taskId) {
        //本地缓存存储任务
        StreamTaskInfo taskInfo = tasks.getIfPresent(taskId);
        //任务不存在直接返回
        if (taskInfo == null) {
            return;
        }

        // 使用 CAS 确保只执行一次
        if (!taskInfo.cancelled.compareAndSet(false, true)) {
            return;
        }
        //带有句柄，句柄执行取消
        if (taskInfo.handle != null) {
            taskInfo.handle.cancel();
        }

        // 在取消时执行回调，保存已累积的内容
        if (taskInfo.sender != null) {
            CompletionPayload payload = taskInfo.onCancelSupplier.get();
            sendCancelAndDone(taskInfo.sender, payload);
            //终止SSE
            taskInfo.sender.complete();
        }
    }

    public void unregister(String taskId) {
        // 清理本地缓存
        tasks.invalidate(taskId);

        // 清理Redis
        redissonClient.getBucket(cancelKey(taskId)).deleteAsync();
    }

    /**
     * 拼接取消Key
     */
    private String cancelKey(String taskId) {
        return CANCEL_KEY_PREFIX + taskId;
    }

    /**
     *通过SSE发送取消和结束信息
     */
    private void sendCancelAndDone(SseEmitterSender sender, CompletionPayload payload) {
        CompletionPayload actualPayload = payload == null ? new CompletionPayload(null, null) : payload;
        sender.sendEvent(SSEEventType.CANCEL.value(), actualPayload);
        sender.sendEvent(SSEEventType.DONE.value(), "[DONE]");
    }

    /**
     * 当前任务信息缓存在本地的Cache当中，返回否则创建新任务信息
     */
    @SneakyThrows
    private StreamTaskInfo getOrCreate(String taskId) {
        return tasks.get(taskId, StreamTaskInfo::new);
    }

    /**
     * 流式任务关键状态保存
     */
    private static final class StreamTaskInfo {
        //但钱任务是否已经被取消，使用AtomicBoolean保证线程安全
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        //任务取消句柄
        private volatile StreamCancellationHandle handle;
        //SSE发射器，用于给前端发送cancel/done
        private volatile SseEmitterSender sender;
        //取消是如何构造收尾数据，例如将生成的内容落库持久化，之后生成CompletionPayload
        private volatile Supplier<CompletionPayload> onCancelSupplier;
    }
}
