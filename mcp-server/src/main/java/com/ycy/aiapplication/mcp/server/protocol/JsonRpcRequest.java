package com.ycy.aiapplication.mcp.server.protocol;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * JSON-RPC2.0请求
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JsonRpcRequest {
    /**
     * 使用协议；JSON-RPC 2.0
     */
    private String jsonrpc="2.0";

    /**
     *请求id，通知请求可为空
     */
    private Object id;

    /**
     * 调用的方法名，EX:initialize ,tools\list.tools\call
     */
    private String method;

    /**
     * 方法参数
     */
    private Map<String,Object>params;

}
