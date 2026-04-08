package com.ycy.aiapplication.mcp.server.common.weather;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class WeatherLiveData {

    /**
     * 省份名
     */
    private String province;

    /**
     * 城市名
     */
    private String city;

    /**
     * 区域编码
     */
    private String adcode;

    /**
     * 天气现象
     */
    private String weather;

    /**
     * 温度
     */
    private String temperature;

    /**
     * 风向
     */
    @JSONField(name = "winddirection")
    private String windDirection;

    /**
     * 风力
     */
    @JSONField(name = "windpower")
    private String windPower;

    /**
     * 湿度
     */
    private String humidity;

    /**
     * 数据发布时间
     */
    @JSONField(name = "reporttime")
    private String reportTime;
}