package com.ycy.aiapplication.mcp.server.common.enums;


import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Getter
@RequiredArgsConstructor
public enum WeekEnum {
    SUNDAY("天", 7),
    MONDAY("一", 1),
    TUESDAY("二", 2),
    WEDNESDAY("三", 3),
    THURSDAY("四", 4),
    FRIDAY("五", 5),
    SATURDAY("六", 6);

    private final String chineseName;
    private final int number;
    private static final Map<Integer,WeekEnum>WEEK_NUMBER_MAP=Arrays.stream(values()).collect(Collectors.toMap(WeekEnum::getNumber, Function.identity()));

    public static String getChineseNameByNumber(int num) {
        if (num < 1 || num > 7)
            return "";
        WeekEnum weekEnum = WEEK_NUMBER_MAP.get(num);
        if(weekEnum==null)
            return "";

        return weekEnum.chineseName;
    }
}
