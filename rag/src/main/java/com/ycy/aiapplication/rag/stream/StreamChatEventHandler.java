package com.ycy.aiapplication.rag.stream;

import cn.hutool.core.util.StrUtil;
import com.ycy.aiapplication.framework.context.UserContext;
import com.ycy.aiapplication.framework.convention.ChatMessage;
import com.ycy.aiapplication.framework.web.SseEmitterSender;
import com.ycy.aiapplication.infrastructure.ai.chat.interfaces.StreamCallback;
import com.ycy.aiapplication.infrastructure.ai.config.AIModelProperties;
import com.ycy.aiapplication.rag.core.memory.ConversationMemoryService;
import com.ycy.aiapplication.rag.dao.entity.ConversationDO;
import com.ycy.aiapplication.rag.enums.SSEEventType;
import com.ycy.aiapplication.rag.service.ConversationComplexQueryService;
import com.ycy.aiapplication.rag.stream.common.CompletionPayload;
import com.ycy.aiapplication.rag.stream.common.MessageDelta;
import com.ycy.aiapplication.rag.stream.common.MetaPayload;

import java.util.Optional;

/**
 * 流式响应回调函数具体实现类
 * <p>
 * 将底层模型回调的时间转换为前端可消费的SSE事件，并在合适时间执行消息落库、会话标题处理、任务注册和流结束收尾
 */
public class StreamChatEventHandler implements StreamCallback {


    private static final String TYPE_THINK = "think";
    private static final String TYPE_RESPONSE = "response";

    private final String conversationId;
    private final String taskId;
    private final String userId;
    private final int messageChunkSize;
    private final boolean sendTitleOnComplete;
    //用于存储完整
    private final StringBuilder answer=new StringBuilder();

    private final SseEmitterSender sender;
    private final ConversationMemoryService memoryService;
    private final ConversationComplexQueryService complexQueryService;
    private final StreamTaskManager taskManager;

    /**
     * 使用参数对象构造（推荐）
     *
     * @param params 构建参数
     */
    public StreamChatEventHandler(StreamChatHandlerParams params) {
        this.sender = new SseEmitterSender(params.getEmitter());
        this.conversationId = params.getConversationId();
        this.taskId = params.getTaskId();
        this.memoryService = params.getMemoryService();
        this.complexQueryService = params.getComplexQueryService();
        this.taskManager = params.getTaskManager();
        this.userId = String.valueOf(UserContext.getId());

        // 计算配置
        this.messageChunkSize = resolveMessageChunkSize(params.getModelProperties());
        this.sendTitleOnComplete = shouldSendTitle();

        // 初始化（发送初始事件、注册任务）
        initialize();
    }

    /**
     * 初始化，发送元数据事件并注册任务
     */
    private void initialize(){
        sender.sendEvent(SSEEventType.META.value(), new MetaPayload(conversationId, taskId));
        taskManager.register(taskId, sender, this::buildCompletionPayloadOnCancel);
    }

    /**
     * 解析信息块大小
     */
    private int resolveMessageChunkSize(AIModelProperties modelProperties){
        return Math.max(1,
                Optional.ofNullable(modelProperties.getStream())
                        .map(AIModelProperties.Stream::getMessageChunkSize)
                        .orElse(5));
    }

    /**
     * 解析当前对话标题
     * 当前为新对话，返回“新对话”作为标题
     */
    private String resolveTitleForEvent(){
        if(!sendTitleOnComplete)
            return null;
        ConversationDO conversation = complexQueryService.findConversation(conversationId, userId);
        if(conversation!=null&& StrUtil.isNotBlank(conversation.getTitle()))
            return conversation.getTitle();
        return "新对话";
    }

    /**
     * 构建取消时完成载荷（有内容优先执行落库）
     * @return
     */
    private CompletionPayload buildCompletionPayloadOnCancel(){
        String content = answer.toString();
        String messageId=null;
        if(StrUtil.isNotBlank(content))
            messageId=memoryService.append(conversationId,userId, ChatMessage.assistant(content));
        String title=resolveTitleForEvent();
        return new CompletionPayload(String.valueOf(messageId),title);
    }

    /**
     * 判断是否需要发送标题
     */
    private boolean shouldSendTitle(){
        ConversationDO existingConversation = complexQueryService.findConversation(
                conversationId,
                userId
        );
        return existingConversation == null || StrUtil.isBlank(existingConversation.getTitle());
    }

    /**
     * 分块发送不同类型的增量内容
     * <p>
     * 按照messageChunkSize将文本进一步切成小块发送
     * 每块包装成MessageDelta发送
     * 按照code point切分文本，对Unicode友好
     * 切分之后返回，让前端渲染打字效果更加稳定
     */
    private void sendChunked(String type, String content) {
        int length = content.length();
        int idx = 0;
        int count = 0;
        //通过StringBuilder缓存字符
        StringBuilder buffer = new StringBuilder();
        while (idx < length) {
            //字符串内容当中提取完整的一个字符，放入buffer当中
            int codePoint = content.codePointAt(idx);
            buffer.appendCodePoint(codePoint);
            //跳过整个字符（前进自身大小）
            idx += Character.charCount(codePoint);
            //字符数++
            count++;
            //达到单个分块返回标准，执行发送
            if (count >= messageChunkSize) {
                sender.sendEvent(SSEEventType.MESSAGE.value(), new MessageDelta(type, buffer.toString()));
                //清空缓存&计数器
                buffer.setLength(0);
                count = 0;
            }
        }
        //剩余不足chunkSize的部分整体打包发送
        if (!buffer.isEmpty()) {
            sender.sendEvent(SSEEventType.MESSAGE.value(), new MessageDelta(type, buffer.toString()));
        }
    }

    @Override
    public void onContent(String content) {
        //检查是否被取消
        if(taskManager.isCancelled(taskId))
            return;
        if(StrUtil.isBlank(content))
            return;
        answer.append(content);
        sendChunked(TYPE_RESPONSE,content);
    }

    @Override
    public void onThinking(String content) {
        if (taskManager.isCancelled(taskId)) {
            return;
        }
        if (StrUtil.isBlank(content)) {
            return;
        }
        sendChunked(TYPE_THINK, content);
    }

    @Override
    public void onComplete() {
        if(taskManager.isCancelled(taskId))
            return;
        // The callback runs on an async streaming thread, so ThreadLocal user context
        // may no longer be available here. Persist with the userId captured at start.
        String messageId = memoryService.append(conversationId, userId, ChatMessage.assistant(answer.toString()));
        String title=resolveTitleForEvent();
        String messageIdText=StrUtil.isBlank(messageId)?null:messageId;
        sender.sendEvent(SSEEventType.FINISH.value(), new CompletionPayload(messageIdText, title));
        sender.sendEvent(SSEEventType.DONE.value(), "[DONE]");
        taskManager.unregister(taskId);
        sender.complete();
    }



    @Override
    public void onError(Throwable t) {
        if (taskManager.isCancelled(taskId)) {
            return;
        }
        taskManager.unregister(taskId);
        sender.fail(t);
    }





}
