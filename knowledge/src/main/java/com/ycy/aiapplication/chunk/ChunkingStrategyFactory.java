package com.ycy.aiapplication.chunk;

import com.ycy.aiapplication.chunk.strategy.ChunkingStrategy;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 文档分块策略工厂
 * 通过构造器注入所有 {@link com.ycy.aiapplication.chunk.strategy.ChunkingStrategy} 类型的Bean，初始化的时候自动注入
 */
@Component
public final class ChunkingStrategyFactory {

    private final Map<ChunkingMode,ChunkingStrategy>strategies;

   public ChunkingStrategyFactory(List<ChunkingStrategy>chunkingStrategies){

       this.strategies=chunkingStrategies.stream()
             .collect(Collectors.toMap(ChunkingStrategy::getType, Function.identity()
                     ,(existing,replacement)->existing));
    }

    /**
     * 根据策略枚举获取对应的切分策略实现
     *
     * @param type 切分策略类型
     * @return {@link ChunkingStrategy} 切分策略实现类
     * @throws IllegalArgumentException 如果指定的策略类型不存在
     */
    public Optional<ChunkingStrategy> findStrategy(ChunkingMode type) {
        if (type == null) return Optional.empty();
        return Optional.ofNullable(strategies.get(type));
    }

    /**
     * 获取指定类型的切分策略，如果不存在则抛出异常
     *
     * @param type 切分策略类型
     * @return {@link ChunkingStrategy} 切分策略实现类
     * @throws IllegalArgumentException 如果指定的策略类型不存在
     */
    public ChunkingStrategy requireStrategy(ChunkingMode type) {
        Objects.requireNonNull(type, "ChunkingMode type must not be null");
        return findStrategy(type)
                .orElseThrow(() -> new IllegalArgumentException("Unknown strategy: " + type));
    }




}
