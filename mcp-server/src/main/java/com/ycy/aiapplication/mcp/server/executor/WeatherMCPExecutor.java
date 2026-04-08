package com.ycy.aiapplication.mcp.server.executor;

import com.alibaba.fastjson2.JSON;
import com.ycy.aiapplication.mcp.server.common.enums.CityCodeEnum;
import com.ycy.aiapplication.mcp.server.common.enums.WeekEnum;
import com.ycy.aiapplication.mcp.server.common.weather.WeatherForecastData;
import com.ycy.aiapplication.mcp.server.common.weather.WeatherLiveData;
import com.ycy.aiapplication.mcp.server.core.MCPToolDefinition;
import com.ycy.aiapplication.mcp.server.core.MCPToolRequest;
import com.ycy.aiapplication.mcp.server.core.MCPToolResponse;
import com.ycy.aiapplication.mcp.server.core.interfaces.MCPToolExecutor;
import com.ycy.aiapplication.mcp.server.dto.resp.AMapWeatherRespDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeatherMCPExecutor implements MCPToolExecutor {
    private final OkHttpClient httpClient;
    private static final String TOOL_ID="weather_query";

    @Value("${amap.weather.api.key:}")
    private String API_KEY;

    @Override
    public MCPToolDefinition getToolDefinition() {
        LinkedHashMap<String, MCPToolDefinition.ParameterDef> parameters = new LinkedHashMap<>();
        parameters.put("city", MCPToolDefinition.ParameterDef.builder()
                        .description("城市名称，支持主要中国城市，例如：上海、广州、无锡等")
                        .type("string")
                        .required(true)
                .build());

        parameters.put("extensions", MCPToolDefinition.ParameterDef.builder()
                        .description("气象查询类型：base（实况天气）、all(未来预报)")
                        .type("string")
                        .required(true)
                        .enumValues(List.of("base","all"))
                .build());
        return MCPToolDefinition.builder()
                .toolId(TOOL_ID)
                .description("查询中国主要城市天气信息，支持查看当前实时天气和未来多天天气预报，包含温度、风向、风力等信息")
                .parameters(parameters)
                .requireUserId(false)
                .build();

    }


    @Override
    public MCPToolResponse execute(MCPToolRequest request) {
        try {
            if(API_KEY==null||API_KEY.isBlank())
            {
                log.error("请检查密钥！");
                throw new RuntimeException("缺失天气预报MCP对应api 密钥！");
            }
            String city=request.getStringParameter("city");
            String extensions=request.getStringParameter("extensions");
            if(city==null||city.isBlank())
                return MCPToolResponse.error(TOOL_ID,"INVALID_PARAMS","请提供城市名称");
            if(extensions==null||extensions.isBlank())
                //没有指定，默认查询当前实时天气
                extensions="base";
            AMapWeatherRespDTO respDTO = queryWeather(city, extensions);
            String topResp = buildTopResponseText(respDTO);
            String liveResp=buildLiveWeatherText(respDTO);
            String forecastResp=buildForecastWeatherText(respDTO);
            log.info("\napi响应：\n{}",topResp);
            log.info("\n实时天气结果：\n{}",liveResp.isBlank()?"无":liveResp);
            log.info("\n天气预报结果：\n{}",forecastResp.isBlank()?"无\n":forecastResp);
            String strBuilder = liveResp +
                    forecastResp;
            String reusltString = strBuilder.trim();
            return MCPToolResponse.success(TOOL_ID,reusltString);
        } catch (Exception e) {
            log.error("天气数据查询失败");
            log.info("失败原因：{}",e.getMessage());
            return MCPToolResponse.error(TOOL_ID, "EXECUTION_ERROR", "查询失败: " + e.getMessage());
        }


    }


    /**
     * 对外统一天气查询入口
     */
    private AMapWeatherRespDTO queryWeather(String cityName, String extensions) {
        String cityCode= CityCodeEnum.getCodeByCityName(cityName);
        //枚举类寻找对应名称的时候已经进行异常处理了，这里无需再次检查
        Request request = buildWeatherRequest(cityCode, extensions);
        String responseJson = executeRequest(request);
        AMapWeatherRespDTO weatherRespDTO = parseResponse(responseJson);
        validateResponse(weatherRespDTO, responseJson);
        return weatherRespDTO;
    }

    /**
     * 构建高德天气请求
     */
    private Request buildWeatherRequest(String cityCode, String extensions) {
        HttpUrl url = new HttpUrl.Builder()
                .scheme("https")
                .host("restapi.amap.com")
                .addPathSegment("v3")
                .addPathSegment("weather")
                .addPathSegment("weatherInfo")
                .addQueryParameter("key", API_KEY)
                .addQueryParameter("city", cityCode)
                .addQueryParameter("extensions", extensions)
                .addQueryParameter("output", "JSON")
                .build();

        return new Request.Builder()
                .url(url)
                .get()
                .build();
    }

    /**
     * 执行 HTTP 请求，拿到原始 JSON
     */
    private String executeRequest(Request request) {
        try (Response response = httpClient.newCall(request).execute()) {
            if (response.body() == null) {
                throw new RuntimeException("高德天气接口响应体为空");
            }

            String json = new String(response.body().bytes(), StandardCharsets.UTF_8);
            log.info("输出结果:{}\n",json);
            return json;
        } catch (IOException e) {
            throw new RuntimeException("调用高德天气接口失败", e);
        }
    }

    /**
     * JSON 转 DTO
     */
    private AMapWeatherRespDTO parseResponse(String responseJson) {
        return JSON.parseObject(responseJson, AMapWeatherRespDTO.class);
    }

    /**
     * 统一响应校验
     */
    private void validateResponse(AMapWeatherRespDTO weatherRespDTO, String responseJson) {
        String status = Optional.ofNullable(weatherRespDTO)
                .map(AMapWeatherRespDTO::getStatus)
                .orElse("0");

        if (!"1".equals(status)) {
            throw new RuntimeException("高德天气接口返回失败，响应内容：" + responseJson);
        }
    }

    /**
     * 构建顶层响应文本
     */
    private String buildTopResponseText(AMapWeatherRespDTO weatherRespDTO) {
        StringBuilder sb = new StringBuilder();
        sb.append("========== 顶层响应 ==========\n");
        sb.append("返回状态 status = ").append(Optional.ofNullable(weatherRespDTO).map(AMapWeatherRespDTO::getStatus).orElse("")).append("\n");
        sb.append("返回结果总数 count = ").append(Optional.ofNullable(weatherRespDTO).map(AMapWeatherRespDTO::getCount).orElse("")).append("\n");
        sb.append("状态信息 info = ").append(Optional.ofNullable(weatherRespDTO).map(AMapWeatherRespDTO::getInfo).orElse("")).append("\n");
        sb.append("状态（10000为正确）infocode = ").append(Optional.ofNullable(weatherRespDTO).map(AMapWeatherRespDTO::getInfocode).orElse("")).append("\n");
        return sb.toString();
    }

    /**
     * 构建实时天气文本
     */
    private String buildLiveWeatherText(AMapWeatherRespDTO weatherRespDTO) {
        List<WeatherLiveData> liveDataList = Optional.ofNullable(weatherRespDTO)
                .map(AMapWeatherRespDTO::getLives)
                .orElse(Collections.emptyList());

        if (liveDataList.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("========== 实时天气 ==========\n");

        for (WeatherLiveData liveData : liveDataList) {
            sb.append("省份: ").append(Optional.ofNullable(liveData.getProvince()).orElse("")).append("\n");
            sb.append("城市: ").append(Optional.ofNullable(liveData.getCity()).orElse("")).append("\n");
            sb.append("区域编码: ").append(Optional.ofNullable(liveData.getAdcode()).orElse("")).append("\n");
            sb.append("天气: ").append(Optional.ofNullable(liveData.getWeather()).orElse("")).append("\n");
            sb.append("温度: ").append(Optional.ofNullable(liveData.getTemperature()).orElse("")).append("\n");
            sb.append("风向: ").append(Optional.ofNullable(liveData.getWindDirection()).orElse("")).append("\n");
            sb.append("风力: ").append(Optional.ofNullable(liveData.getWindPower()).orElse("")).append("级").append("\n");
            sb.append("湿度: ").append(Optional.ofNullable(liveData.getHumidity()).orElse("")).append("%").append("\n");
            sb.append("发布时间: ").append(Optional.ofNullable(liveData.getReportTime()).orElse("")).append("\n");
            sb.append("----------------------------\n");
        }

        return sb.toString();
    }

    /**
     * 构建天气预报文本
     */
    private String buildForecastWeatherText(AMapWeatherRespDTO weatherRespDTO) {
        List<WeatherForecastData> forecastDataList = Optional.ofNullable(weatherRespDTO)
                .map(AMapWeatherRespDTO::getForecasts)
                .orElse(Collections.emptyList());

        if (forecastDataList.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("========== 天气预报 ==========\n");

        for (WeatherForecastData forecastData : forecastDataList) {
            sb.append("城市: ").append(Optional.ofNullable(forecastData.getCity()).orElse("")).append("\n");
            sb.append("区域编码: ").append(Optional.ofNullable(forecastData.getAdcode()).orElse("")).append("\n");
            sb.append("省份: ").append(Optional.ofNullable(forecastData.getProvince()).orElse("")).append("\n");
            sb.append("预报发布时间: ").append(Optional.ofNullable(forecastData.getReportTime()).orElse("")).append("\n");

            List<WeatherForecastData.CastData> castDataList = Optional.ofNullable(forecastData.getCasts())
                    .orElse(Collections.emptyList());

            for (WeatherForecastData.CastData castData : castDataList) {
                sb.append("日期: ").append(Optional.ofNullable(castData.getDate()).orElse("")).append("\n");
                sb.append("星期").append(buildWeekText(castData.getWeek())).append("\n");
                sb.append("白天天气: ").append(Optional.ofNullable(castData.getDayWeather()).orElse("")).append("\n");
                sb.append("夜间天气: ").append(Optional.ofNullable(castData.getNightWeather()).orElse("")).append("\n");
                sb.append("白天温度: ").append(Optional.ofNullable(castData.getDayTemp()).orElse("")).append("\n");
                sb.append("夜间温度: ").append(Optional.ofNullable(castData.getNightTemp()).orElse("")).append("\n");
                sb.append("白天风向: ").append(Optional.ofNullable(castData.getDayWind()).orElse("")).append("\n");
                sb.append("夜间风向: ").append(Optional.ofNullable(castData.getNightWind()).orElse("")).append("\n");
                sb.append("白天风力: ").append(Optional.ofNullable(castData.getDayPower()).orElse("")).append("级").append("\n");
                sb.append("夜间风力: ").append(Optional.ofNullable(castData.getNightPower()).orElse("")).append("级").append("\n");
                sb.append("白天浮点温度: ").append(Optional.ofNullable(castData.getDayTempFloat()).orElse("")).append("\n");
                sb.append("夜间浮点温度: ").append(Optional.ofNullable(castData.getNightTempFloat()).orElse("")).append("\n");
                sb.append("----------------------------\n");
            }
        }

        return sb.toString();
    }

    /**
     * 构建星期文本，避免 week 非数字时抛异常
     */
    private String buildWeekText(String week) {
        if (week == null || week.trim().isEmpty()) {
            return "";
        }

        try {
            return WeekEnum.getChineseNameByNumber(Integer.parseInt(week));
        } catch (NumberFormatException e) {
            return "";
        }
    }
}
