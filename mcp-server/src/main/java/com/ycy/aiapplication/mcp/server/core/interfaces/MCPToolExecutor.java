package com.ycy.aiapplication.mcp.server.core.interfaces;

import com.ycy.aiapplication.mcp.server.core.MCPToolDefinition;
import com.ycy.aiapplication.mcp.server.core.MCPToolRequest;
import com.ycy.aiapplication.mcp.server.core.MCPToolResponse;

/**
 * MCP工具执行接口
 */
public interface MCPToolExecutor {

    /**
     * 获取工具定义信息
     * 返回工具元数据定义：工具id、名称、描述、参数定义等信息
     */
    MCPToolDefinition getToolDefinition();

    /**
     * 执行工具逻辑
     * @param request 工具执行请求，包含执行所需参数
     * @return 工具执行响应，包含执行结果和状态信息
     */
    MCPToolResponse execute(MCPToolRequest request);

    /**
     * 获取工具唯一标识符
     * @return 工具id
     */
    default String getToolId(){return getToolDefinition().getToolId();}
}
