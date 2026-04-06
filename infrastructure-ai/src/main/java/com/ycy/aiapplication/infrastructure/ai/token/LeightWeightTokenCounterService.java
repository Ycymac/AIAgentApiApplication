package com.ycy.aiapplication.infrastructure.ai.token;

import cn.hutool.core.util.StrUtil;
import org.springframework.stereotype.Service;

/**
 * 轻量token计算服务
 */
@Service
public final class LeightWeightTokenCounterService implements TokenCounterService {
    @Override
    public Integer countTokens(String text) {
        //字符串为空，token量为0
        if (StrUtil.isBlank(text)) {
            return 0;
        }
        //ascii字符
        int asciiCount=0;
        //东亚-汉字体系字符
        int cjkCount=0;
        //其余字符
        int otherCount=0;
        for(int i=0;i<text.length();i++){
            char ch=text.charAt(i);
            //跳过空白字符（包含空格、tab、换行、回车、换页、cjk的全角空格）
            if(Character.isWhitespace(ch))continue;
            //0X7F:ascii字符最大值
            if(ch<0X7F)asciiCount++;
            else if (isCjk(ch))cjkCount++;
            else otherCount++;
        }
        //因为每种字符使用的token数量是不一样的，分别进行计算
        int asciiTokens = (asciiCount + 3) / 4; // 英文等按 4 字符约 1 token
        int otherTokens = (otherCount + 1) / 2; // 其他字符按 2 字符约 1 token
        int total = asciiTokens + cjkCount + otherTokens;
        return Math.max(total, 1);
    }

    /**
     * unicode将字符按照功能划分为不同的区块
     * 通过字符所在的unicode区块判断是否是cjk
     * @param ch 字符
     * @return 是否是cjk字符
     */
    private boolean isCjk(char ch) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(ch);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS//常用汉字
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_C
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_D
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_E
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_F
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS_SUPPLEMENT
                || block == Character.UnicodeBlock.CJK_RADICALS_SUPPLEMENT
                || block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
                || block == Character.UnicodeBlock.HIRAGANA//日语平假名
                || block == Character.UnicodeBlock.KATAKANA//日语片假名
                || block == Character.UnicodeBlock.KATAKANA_PHONETIC_EXTENSIONS
                || block == Character.UnicodeBlock.HANGUL_SYLLABLES//韩文音节
                || block == Character.UnicodeBlock.HANGUL_JAMO
                || block == Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO;
    }
}
