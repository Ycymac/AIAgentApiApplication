package com.ycy.aiapplication.mcp.server.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;


@Getter
@RequiredArgsConstructor
public enum CityCodeEnum {

    BEIJING("北京", "110000"),
    TIANJIN("天津", "120000"),
    SHANGHAI("上海", "310000"),
    CHONGQING("重庆", "500000"),

    GUANGZHOU("广州", "440100"),
    SHENZHEN("深圳", "440300"),
    ZHUHAI("珠海", "440400"),
    FOSHAN("佛山", "440600"),
    DONGGUAN("东莞", "441900"),
    ZHONGSHAN("中山", "442000"),
    HUIZHOU("惠州", "441300"),
    JIANGMEN("江门", "440700"),
    ZHANJIANG("湛江", "440800"),
    MAOMING("茂名", "440900"),
    ZHAOQING("肇庆", "441200"),
    SHANTOU("汕头", "440500"),
    JIEYANG("揭阳", "445200"),
    CHAOZHOU("潮州", "445100"),
    QINGYUAN("清远", "441800"),
    SHAOGUAN("韶关", "440200"),

    HANGZHOU("杭州", "330100"),
    NINGBO("宁波", "330200"),
    WENZHOU("温州", "330300"),
    JIAXING("嘉兴", "330400"),
    HUZHOU("湖州", "330500"),
    SHAOXING("绍兴", "330600"),
    JINHUA("金华", "330700"),
    TAIZHOU_ZJ("台州", "331000"),

    NANJING("南京", "320100"),
    SUZHOU("苏州", "320500"),
    WUXI("无锡", "320200"),
    CHANGZHOU("常州", "320400"),
    NANTONG("南通", "320600"),
    XUZHOU("徐州", "320300"),
    YANCHENG("盐城", "320900"),
    YANGZHOU("扬州", "321000"),
    ZHENJIANG("镇江", "321100"),
    TAIZHOU_JS("泰州", "321200"),
    LIANYUNGANG("连云港", "320700"),
    HUAIAN("淮安", "320800"),

    HEFEI("合肥", "340100"),
    WUHU("芜湖", "340200"),
    BENGPU("蚌埠", "340300"),
    FUYANG("阜阳", "341200"),
    ANQING("安庆", "340800"),

    FUZHOU("福州", "350100"),
    XIAMEN("厦门", "350200"),
    QUANZHOU("泉州", "350500"),
    ZHANGZHOU("漳州", "350600"),
    PUTIAN("莆田", "350300"),

    JINAN("济南", "370100"),
    QINGDAO("青岛", "370200"),
    YANTAI("烟台", "370600"),
    WEIFANG("潍坊", "370700"),
    LINYI("临沂", "371300"),
    ZIBO("淄博", "370300"),
    JINING("济宁", "370800"),
    WEIHAI("威海", "371000"),

    ZHENGZHOU("郑州", "410100"),
    LUOYANG("洛阳", "410300"),
    XINXIANG("新乡", "410700"),
    NANYANG("南阳", "411300"),

    WUHAN("武汉", "420100"),
    YICHANG("宜昌", "420500"),
    XIANGYANG("襄阳", "420600"),
    JINGZHOU("荆州", "421000"),

    CHANGSHA("长沙", "430100"),
    ZHUZHOU("株洲", "430200"),
    XIANGTAN("湘潭", "430300"),
    HENGYANG("衡阳", "430400"),
    YUEYANG("岳阳", "430600"),
    CHANGDE("常德", "430700"),

    NANNING("南宁", "450100"),
    LIUZHOU("柳州", "450200"),
    GUILIN("桂林", "450300"),
    BEIHAI("北海", "450500"),

    HAIKOU("海口", "460100"),
    SANYA("三亚", "460200"),

    CHENGDU("成都", "510100"),
    MIANYANG("绵阳", "510700"),
    DEYANG("德阳", "510600"),
    YIBIN("宜宾", "511500"),
    NANCHONG("南充", "511300"),
    LUZHOU("泸州", "510500"),

    GUIYANG("贵阳", "520100"),
    ZUNYI("遵义", "520300"),

    KUNMING("昆明", "530100"),
    DALI("大理", "532900"),

    LHASA("拉萨", "540100"),

    XIAN("西安", "610100"),
    XIANYANG("咸阳", "610400"),
    BAOJI("宝鸡", "610300"),
    YANAN("延安", "610600"),

    LANZHOU("兰州", "620100"),
    XINING("西宁", "630100"),

    YINCHUAN("银川", "640100"),

    URUMQI("乌鲁木齐", "650100"),
    KASHGAR("喀什", "653100"),

    SHIJIAZHUANG("石家庄", "130100"),
    TANGSHAN("唐山", "130200"),
    QINHUANGDAO("秦皇岛", "130300"),
    BAODING("保定", "130600"),

    TAIYUAN("太原", "140100"),
    DATONG("大同", "140200"),
    YUNCHENG("运城", "140800"),

    HOHHOT("呼和浩特", "150100"),
    BAOTOU("包头", "150200"),
    CHIFENG("赤峰", "150400"),

    SHENYANG("沈阳", "210100"),
    DALIAN("大连", "210200"),
    ANSHAN("鞍山", "210300"),

    CHANGCHUN("长春", "220100"),
    JILIN("吉林", "220200"),

    HARBIN("哈尔滨", "230100"),
    QIQIHAR("齐齐哈尔", "230200"),
    MUDANJIANG("牡丹江", "231000"),

    NANCHANG("南昌", "360100"),
    GANZHOU("赣州", "360700"),
    JIUJIANG("九江", "360400");

    private final String cityName;
    private final String cityCode;



    private static final Map<String, CityCodeEnum> CITY_NAME_MAP =
            Arrays.stream(values())
                    .collect(Collectors.toMap(CityCodeEnum::getCityName, Function.identity()));

    /**
     * 通过中文城市名查询城市编码
     * 示例：
     * 北京 -> 110000
     * 北京市 -> 110000
     * 广州 -> 440100
     */
    public static String getCodeByCityName(String cityName) {
        if (cityName == null || cityName.trim().isEmpty()) {
            throw new IllegalArgumentException("城市名不能为空");
        }

        String normalizedName = normalizeCityName(cityName);
        CityCodeEnum cityCodeEnum = CITY_NAME_MAP.get(normalizedName);

        if (cityCodeEnum == null) {
            throw new IllegalArgumentException("暂时不支持查询当前天气，当前支持" + String.join(",",CITY_NAME_MAP.keySet()));
        }

        return cityCodeEnum.getCityCode();
    }

    /**
     * 去除带有的市 用于枚举查询
     */
    private static String normalizeCityName(String cityName) {
        String normalized = cityName.trim();
        if (normalized.endsWith("市")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
