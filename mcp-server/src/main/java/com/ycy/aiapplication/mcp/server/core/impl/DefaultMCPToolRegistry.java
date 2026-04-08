package com.ycy.aiapplication.mcp.server.core.impl;

import com.ycy.aiapplication.mcp.server.core.MCPToolDefinition;
import com.ycy.aiapplication.mcp.server.core.interfaces.MCPToolExecutor;
import com.ycy.aiapplication.mcp.server.core.interfaces.MCPToolRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP工具注册表默认实现
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultMCPToolRegistry implements MCPToolRegistry {

    /**
     * 所有的工具执行器
     */
    private final Map<String, MCPToolExecutor>executorMap=new ConcurrentHashMap<>();
    /**
     * 通过Spring组件自动注入
     * 收集所有MCPToolExecutor Bean
     * 注入List当中
     */
    private final List<MCPToolExecutor>autoDiscoveredExecutors;

    //构造器执行、自动注入完成之后，运行之前 该方法执行
    @PostConstruct
    public void init(){
        if(autoDiscoveredExecutors==null||autoDiscoveredExecutors.isEmpty()) {
            log.info("MCP 工具注册跳过，未发现任何工具执行器");
            return;
        }
        for(MCPToolExecutor executor:autoDiscoveredExecutors)
            register(executor);

        log.info("MCP 工具自动注册完成, 共注册 {} 个工具", autoDiscoveredExecutors.size());

    }

    @Override
    public void register(MCPToolExecutor executor) {
        String toolId = executor.getToolId();
        executorMap.put(toolId,executor);
        log.info("MCP 工具注册成功, toolId: {}", toolId);
    }

    @Override
    public Optional<MCPToolExecutor> getExecutor(String toolId) {
        return Optional.ofNullable(executorMap.get(toolId));
    }

    @Override
    public List<MCPToolDefinition> listAllTools() {
        return executorMap.values().stream()
                .map(MCPToolExecutor::getToolDefinition).toList();
    }

    @Override
    public List<MCPToolExecutor> listAllExecutors() {
        return new ArrayList<>(executorMap.values());
    }
}
