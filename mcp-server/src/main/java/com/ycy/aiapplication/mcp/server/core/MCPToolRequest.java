package com.ycy.aiapplication.mcp.server.core;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * MCP工具调用请求
 * 系一层解析 tool/call参数之后构建，并传入具体执行器执行
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MCPToolRequest {

    private String toolId;

    private String userId;

    private String conversationId;

    private String userQuestion;

    @Builder.Default
    private Map<String,Object>parameters =new HashMap<>();

    /**
     * 按照指定类型读取参数
     * @param key 参数名
     * @return 参数值
     */
    //因为泛型擦除，编译器无法在编译期验证泛型，会有警告，使用SuppressWarnings告诉其不用生成警告信息
    @SuppressWarnings("unchecked")
    public <T> T getParameter(String key){
        Object value = parameters.get(key);
        return value!=null?(T)value:null;
    }

    /**
     * 读取字符串参数
     * @param key 参数名
     * @return 字符串参数，不存在时候返回null
     */
    public String getStringParameter(String key){
        Object value = parameters.get(key);
        return value!=null?String.valueOf(value):null;

    }

}
