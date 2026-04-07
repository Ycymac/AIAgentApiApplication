package com.ycy.aiapplication.infrastructure.ai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class ChatExecutorConfig {

    @Bean("bailianChatStreamExecutor")
    public Executor bailianChatStreamExecutor(AIModelProperties properties) {
        return buildExecutor(properties, "bailian-chat-stream-");
    }

    @Bean("siliconFlowChatStreamExecutor")
    public Executor siliconFlowChatStreamExecutor(AIModelProperties properties) {
        return buildExecutor(properties, "siliconflow-chat-stream-");
    }

    private Executor buildExecutor(AIModelProperties properties, String prefix) {
        AIModelProperties.Stream stream = properties.getStream();
        int coreSize = stream.getExecutorCoreSize() == null || stream.getExecutorCoreSize() <= 0 ? 2 : stream.getExecutorCoreSize();
        int maxSize = stream.getExecutorMaxSize() == null || stream.getExecutorMaxSize() <= 0 ? Math.max(4, coreSize) : Math.max(coreSize, stream.getExecutorMaxSize());
        int queueCapacity = stream.getExecutorQueueCapacity() == null || stream.getExecutorQueueCapacity() <= 0 ? 128 : stream.getExecutorQueueCapacity();
        return new ThreadPoolExecutor(
                coreSize,
                maxSize,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueCapacity),
                new NamedThreadFactory(prefix),
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    private static final class NamedThreadFactory implements ThreadFactory {
        private final AtomicInteger sequence = new AtomicInteger(1);
        private final String prefix;

        private NamedThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable);
            thread.setName(prefix + sequence.getAndIncrement());
            return thread;
        }
    }
}
