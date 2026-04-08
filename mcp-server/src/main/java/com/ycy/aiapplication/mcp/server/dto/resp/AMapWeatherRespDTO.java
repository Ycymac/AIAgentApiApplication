package com.ycy.aiapplication.mcp.server.dto.resp;


import com.ycy.aiapplication.mcp.server.common.weather.WeatherForecastData;
import com.ycy.aiapplication.mcp.server.common.weather.WeatherLiveData;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class AMapWeatherRespDTO {

    /**
     * 返回状态，1:成功 0:失败
     */
    private String status;

    /**
     * 返回结果总数目
     */
    private String count;

    /**
     * 返回状态说明
     */
    private String info;

    /**
     * 返回状态码，10000 代表正确
     */
    private String infocode;

    /**
     * 实时天气数据，extensions=base 时返回
     */
    private List<WeatherLiveData> lives;

    /**
     * 预报天气数据，extensions=all 时返回
     */
    private List<WeatherForecastData> forecasts;
}
