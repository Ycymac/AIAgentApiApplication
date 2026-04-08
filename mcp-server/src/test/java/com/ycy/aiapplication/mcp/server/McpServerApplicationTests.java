package com.ycy.aiapplication.mcp.server;


import com.ycy.aiapplication.mcp.server.core.MCPToolRequest;
import com.ycy.aiapplication.mcp.server.executor.WeatherMCPExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.LinkedHashMap;

@SpringBootTest
public class McpServerApplicationTests {

    @Autowired
    WeatherMCPExecutor mcpExecutor;
    @Test
    void weatherTest(){
        LinkedHashMap<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("city","无锡");
        parameters.put("extensions","base");
        MCPToolRequest toolRequest = MCPToolRequest
                .builder()
                .toolId("weather_query")
                .parameters(parameters)
                .build();
        System.out.println(mcpExecutor.execute(toolRequest));
    }

}
