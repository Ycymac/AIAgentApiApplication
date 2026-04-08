package com.ycy.aiapplication.mcp.server.common.weather;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class WeatherForecastData {

    /**
     * 城市名称
     */
    private String city;

    /**
     * 区域编码
     */
    private String adcode;

    /**
     * 省份名称
     */
    private String province;

    /**
     * 预报发布时间
     */
    @JSONField(name = "reporttime")
    private String reportTime;

    /**
     * 未来天气列表
     */
    private List<CastData> casts;

    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Data
    public static class CastData {

        /**
         * 日期
         */
        private String date;

        /**
         * 星期
         */
        private String week;

        /**
         * 白天天气
         */
        @JSONField(name = "dayweather")
        private String dayWeather;

        /**
         * 夜间天气
         */
        @JSONField(name = "nightweather")
        private String nightWeather;

        /**
         * 白天温度
         */
        @JSONField(name = "daytemp")
        private String dayTemp;

        /**
         * 夜间温度
         */
        @JSONField(name = "nighttemp")
        private String nightTemp;

        /**
         * 白天风向
         */
        @JSONField(name = "daywind")
        private String dayWind;

        /**
         * 夜间风向
         */
        @JSONField(name = "nightwind")
        private String nightWind;

        /**
         * 白天风力
         */
        @JSONField(name = "daypower")
        private String dayPower;

        /**
         * 夜间风力
         */
        @JSONField(name = "nightpower")
        private String nightPower;

        /**
         * 白天浮点温度
         */
        @JSONField(name = "daytemp_float")
        private String dayTempFloat;

        /**
         * 夜间浮点温度
         */
        @JSONField(name = "nighttemp_float")
        private String nightTempFloat;
    }
}
