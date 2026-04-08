package com.ycy.aiapplication.mcp.server.core;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * MCP工具响应
 * 执行器返回，协议层转换为标准响应结构
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MCPToolResponse {
    /**
     * 执行状态，是否成功
     */
    @Builder.Default
    private boolean success=true;
    /**
     * 工具id
     */
    private String toolId;
    /**
     * 结构化数据，可选项
     */
    @Builder.Default
    private Map<String,Object>data=new HashMap<>();
    /**
     * 文本化结果
     */
    private String textResult;
    /**
     *错误消息，异常时使用
     */
    private String errorMessage;
    /**
     * 错误码，异常时使用
     */
    private String errorCode;
    /**
     * 执行耗时
     * 单位：毫秒
     */
    private long costMs;



    /**
     * 构建成功响应，仅包含文本结果
     *
     * @param toolId 工具 ID
     * @param textResult 文本结果
     * @return 成功响应
     */
    public static MCPToolResponse success(String toolId, String textResult) {
        return MCPToolResponse.builder()
                .success(true)
                .toolId(toolId)
                .textResult(textResult)
                .build();
    }

    /**
     * 构建成功响应，包含文本和结构化数据
     *
     * @param toolId 工具 ID
     * @param textResult 文本结果
     * @param data 结构化数据
     * @return 成功响应
     */
    public static MCPToolResponse success(String toolId, String textResult, Map<String, Object> data) {
        return MCPToolResponse.builder()
                .success(true)
                .toolId(toolId)
                .textResult(textResult)
                .data(data)
                .build();
    }

    /**
     * 构建失败响应
     *
     * @param toolId 工具 ID
     * @param errorCode 错误码
     * @param errorMessage 错误消息
     * @return 失败响应
     */
    public static MCPToolResponse error(String toolId, String errorCode, String errorMessage) {
        return MCPToolResponse.builder()
                .success(false)
                .toolId(toolId)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .build();
    }


}
