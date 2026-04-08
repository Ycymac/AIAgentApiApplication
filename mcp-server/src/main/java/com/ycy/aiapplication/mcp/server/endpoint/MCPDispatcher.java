package com.ycy.aiapplication.mcp.server.endpoint;

import com.ycy.aiapplication.mcp.server.core.MCPToolDefinition;
import com.ycy.aiapplication.mcp.server.core.MCPToolRequest;
import com.ycy.aiapplication.mcp.server.core.MCPToolResponse;
import com.ycy.aiapplication.mcp.server.core.interfaces.MCPToolExecutor;
import com.ycy.aiapplication.mcp.server.core.interfaces.MCPToolRegistry;
import com.ycy.aiapplication.mcp.server.protocol.JsonRpcError;
import com.ycy.aiapplication.mcp.server.protocol.JsonRpcRequest;
import com.ycy.aiapplication.mcp.server.protocol.JsonRpcResponse;
import com.ycy.aiapplication.mcp.server.protocol.MCPToolSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * MCP方法分发器
 * 处理 initialize 、tools/list、tools/call三个核心方法
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MCPDispatcher {
    private final MCPToolRegistry toolRegistry;

    public JsonRpcResponse dispatch(JsonRpcRequest request) {
        String method = request.getMethod();
        Object id = request.getId();
        //Notification（无id）：仅记录日志，不返回响应体
        if (id == null) {
            log.debug("MCP notification received:{}", method);
            return null;
        }
        return switch (method) {
            case "initialize" -> handleInitialize(id);
            case "tools/list" -> handleToolsList(id);
            case "tools/call" -> handleToolsCall(id, request.getParams());
            default -> JsonRpcResponse.error(id, JsonRpcError.METHOD_NOT_FOUND, "Unknown method: " + method);
        };
    }

    /**
     * 协议向握手响应
     * 客户端连接到mcp-server时候的初始化信息
     * listChanged:false 意味着工具表是静态的，无需监听工具监听变更
     * 为什么使用LinkedHashMap？因为能够保障输出顺序和插入先后一致
     * hashmap并不保障任何遍历顺序，实际顺序由哈希分布+桶结构决定
     */
    private JsonRpcResponse handleInitialize(Object id) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("protocolVersion", "20206-03-18");

        LinkedHashMap<String, Object> capabilities = new LinkedHashMap<>();
        capabilities.put("tools", Map.of("listChanged", false));
        result.put("capabilities", capabilities);

        LinkedHashMap<String, Object> serverInfo = new LinkedHashMap<>();
        serverInfo.put("name", "javis-mcp-server");
        serverInfo.put("version", "0.0.1");
        result.put("serverInfo", serverInfo);

        return JsonRpcResponse.success(id, result);
    }

    private JsonRpcResponse handleToolsList(Object id) {
        List<MCPToolDefinition> tools = toolRegistry.listAllTools();
        List<MCPToolSchema> schemas = tools.stream().map(this::toSchema).toList();
        return JsonRpcResponse.success(id, Map.of("tools", schemas));


    }

    /**
     *工具调用通用方法
     * @param id id值
     * @param params JSON-RPC协议参数
     * @return
     *
     * // JSON-RPC 请求的标准完整结构
     * {
     *   "jsonrpc": "2.0",
     *   "id": 1,
     *   "method": "tools/call",
     *   "params": {                    // ← JSON-RPC 协议层参数（第一层）
     *     "name": "sales_query",       // ← 工具名称
     *     "arguments": {               // ← MCP 协议层参数（第二层，工具的参数列表）
     *       "region": "华东",
     *       "period": "2024-01",
     *       "limit": 10
     *     }
     *   }
     * }
     * params当中只存储arguments的键值对
     */
    private JsonRpcResponse handleToolsCall(Object id, Map<String, Object> params) {
        //name-arguments键值对：工具名称&参数列表
        //参数列表为空/没有调用函数的名称，视为异常
        if (params == null || !params.containsKey("name") || params.get("name") == null)
            return JsonRpcResponse.error(id, JsonRpcError.INVALID_PARAMS, "Missing 'name' in params");

        String toolName = String.valueOf(params.get("name"));
        //通过name查询对应工具
        Optional<MCPToolExecutor> executorOpt = toolRegistry.getExecutor(toolName);
        if (executorOpt.isEmpty())
            return JsonRpcResponse.error(id, JsonRpcError.METHOD_NOT_FOUND, "Tool not found" + toolName);

        //解析参数
        HashMap<String, Object> arguments = new HashMap<>();
        //arguments：工具参数列表
        Object rawArguments = params.get("arguments");
        if(rawArguments instanceof Map<?, ?>argMap){
            for(Map.Entry<?,?> entry:argMap.entrySet()){
                arguments.put(String.valueOf(entry.getKey()),entry.getValue());
            }
        }
        MCPToolRequest toolRequest = MCPToolRequest.builder()
                .toolId(toolName)
                .parameters(arguments)
                .build();
        //执行业务逻辑
        try{
            //执行
            MCPToolResponse toolResponse = executorOpt.get().execute(toolRequest);

            ArrayList<Map<String,Object>> content = new ArrayList<>();
            Map<String,Object>textContent=new LinkedHashMap<>();
            textContent.put("type","text");
            textContent.put("text",Optional.ofNullable(toolResponse.getTextResult()).orElse(""));
            content.add(textContent);

            Map<String,Object>result=new LinkedHashMap<>();
            result.put("content",content);
            result.put("isError",!toolResponse.isSuccess());

            return JsonRpcResponse.success(id,result);
        }catch (Exception e){
            //工具执行异常，返回相关信息
            log.error("Tool execution failed: {}", toolName, e);
            List<Map<String, Object>> content = new ArrayList<>();
            //文本内容
            Map<String, Object> textContent = new LinkedHashMap<>();
            textContent.put("type", "text");
            textContent.put("text", "工具调用异常: " + e.getMessage());
            content.add(textContent);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("content", content);
            result.put("isError", true);

            return JsonRpcResponse.success(id, result);
        }

    }

    private MCPToolSchema toSchema(MCPToolDefinition def) {
        //所有参数定义
        LinkedHashMap<String, MCPToolSchema.PropertyDef> properties = new LinkedHashMap<>();
        //所有必填参数名
        ArrayList<String> required = new ArrayList<>();

        if (def.getParameters() != null) {
            def.getParameters().forEach((name, paramDef) -> {
                properties.put(name, MCPToolSchema.PropertyDef.builder()
                        .type(paramDef.getType())
                        .description(paramDef.getDescription())
                        .enumValues(paramDef.getEnumValues())
                        .build());
                //必填参数，加入列表
                if (paramDef.isRequired())
                    required.add(name);
            });

        }
        //返回工具定义
        return MCPToolSchema.builder()
                .name(def.getToolId())
                .description(def.getDescription())
                //工具输入定义
                .inputSchema(MCPToolSchema.InputSchema.builder()
                        .type("object")
                        .properties(properties)
                        .required(required.isEmpty() ? null : required)
                        .build())
                .build();

    }
}
