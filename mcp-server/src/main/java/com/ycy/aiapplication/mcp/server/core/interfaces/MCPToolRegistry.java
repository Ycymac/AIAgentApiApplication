package com.ycy.aiapplication.mcp.server.core.interfaces;

import com.ycy.aiapplication.mcp.server.core.MCPToolDefinition;

import java.util.List;
import java.util.Optional;

/**
 * 工具注册表接口
 * 管理服务器已注册工具执行器，并按照toolId查询能力
 */
public interface MCPToolRegistry {

    /**
     * 注册工具执行器
     *
     * @param executor 工具执行器实例
     */
    void register(MCPToolExecutor executor);

    /**
     * 按工具 ID 获取执行器
     *
     * @param toolId 工具 ID，对应 tools/call 的 name
     * @return 执行器，不存在时返回空
     */
    Optional<MCPToolExecutor> getExecutor(String toolId);

    /**
     * 获取所有已注册工具定义
     *
     * @return 工具定义列表
     */
    List<MCPToolDefinition> listAllTools();

    /**
     * 获取所有已注册执行器
     *
     * @return 执行器列表
     */
    List<MCPToolExecutor> listAllExecutors();
}
