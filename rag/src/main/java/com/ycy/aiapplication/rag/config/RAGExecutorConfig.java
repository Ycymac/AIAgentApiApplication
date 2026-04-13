package com.ycy.aiapplication.rag.config;

import com.alibaba.ttl.threadpool.TtlExecutors;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class RAGExecutorConfig {

    private static final int CPU_COUNT=Runtime.getRuntime().availableProcessors();

    @Bean("memorySummaryThreadPoolExecutor")
    public  Executor memorySummaryThreadPoolExecutor(){
        return new ThreadPoolExecutor(
                1,
                Math.max(2,CPU_COUNT>>1),
                60,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(200),
                new NameThreadFactory("memory_summary_threadPool_executor"),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    @Bean("ragInnerRetrievalThreadPoolExecutor")
    public Executor ragInnerRetrievalThreadPoolExecutor(){
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                CPU_COUNT << 1,
                CPU_COUNT << 2,
                60,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(100),
                new NameThreadFactory("rag_inner_retrieval_executor"),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        return TtlExecutors.getTtlExecutor(executor);
    }



    private static final class NameThreadFactory implements ThreadFactory{
        private final AtomicInteger sequence=new AtomicInteger(1);
        private final String prefix;

        private NameThreadFactory(String prefix){this.prefix=prefix;}
        @Override
        public Thread newThread(@NotNull Runnable runnable) {
            Thread thread = new Thread(runnable);
            thread.setName(prefix+"_"+sequence.getAndIncrement());
            return thread;
        }
    }

}
