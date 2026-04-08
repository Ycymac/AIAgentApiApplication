package com.ycy.aiapplication.mcp.server.core;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * MCP工具定义
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MCPToolDefinition {
    /**
     * 工具指定唯一id
     */
    private String toolId;
    /**
     * 工具详细描述
     */
    private String description;
    /**
     * 工具参数定义映射，key：参数名，value:参数定义
     */
    private Map<String,ParameterDef> parameters;

    @Builder.Default
    private boolean requireUserId = true;


    /**
     * 参数定义静态类
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParameterDef{
        /**
         * 参数描述
         */
        private String description;
        /**
         * 参数对应类型，默认为“string”
         */
        @Builder.Default
        private String type="string";
        /**
         * 是否为必填，默认false
         */
        @Builder.Default
        private boolean required=false;
        /**
         * 参数默认值
         */
        private Object defaultValue;
        /**
         * 枚举列表
         */
        private List<String> enumValues;
    }
}
