package com.ycy.aiapplication.mcp.server.endpoint;

import com.ycy.aiapplication.mcp.server.protocol.JsonRpcRequest;
import com.ycy.aiapplication.mcp.server.protocol.JsonRpcResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * MCP Streamable Http端点
 * 提供/mcp断电接收JSON-RPC请求、通知
 */
@RestController
@RequiredArgsConstructor
public class MCPEndPoint {

    private final MCPDispatcher dispatcher;

    /**
     * mcp访问端口
     * @param request 请求
     * @return ResponseEntity——Spring MVC专用Http响应对象
     */
    @PostMapping("/mcp")
    public ResponseEntity<?> handle(@RequestBody JsonRpcRequest request){
        JsonRpcResponse response = dispatcher.dispatch(request);
        if(response==null)
            return ResponseEntity.noContent().build();
        return ResponseEntity.ok(response);

    }
}
