package com.ycy.aiapplication.rag.core.rewrite.common;

/**
 * 查询语句词语归一化工具类
 * <p>
 *     用于将用户输入的非标准术语词汇替换为标准术语
 */
public class QueryTermMappingUtil {

    /**
     * 安全归一化替换
     * -只替换sourceTerm
     * -当前位置已经之标准术语开头则不进行替换
     * @param text 需要进行替换的文本
     * @param sourceTerm 文本当中存在的不标准表达
     * @param targetTerm 用于替换的标准术语
     * @return 归一化之后的文本
     * <p>
     *    例如将：平安保
     *    替换为平安保险
     */
    public static String applyMapping(String text, String sourceTerm,String targetTerm){

        if (text == null || text.isEmpty() || sourceTerm == null || sourceTerm.isEmpty()) {
            return text;
        }
        StringBuilder sb = new StringBuilder();
        int idx = 0;
        int len = text.length();
        int sourceLen = sourceTerm.length();
        int targetLen = targetTerm.length();
        //遍历文本
        while(idx<len){
            //从idx开始匹配，返回第一次位置
            int hit=text.indexOf(sourceTerm,idx);
            //后续没有命中，整体拷贝
            if(hit<0){
                sb.append(text,idx,len);
                break;
            }
            //前面未命中位置的部分拷贝
            sb.append(text,idx,hit);
            //术语和不规范文字开头部分可能重叠，先判断当前是否规范术语
            //防止二次替换
            boolean alreadyTarget=
                    targetTerm!=null&&
                    hit+targetLen<=len&&
                    //hit开始开头存在术语
                    text.startsWith(targetTerm,hit);
            if(alreadyTarget){
                //原文拷贝，一次性跳过
                sb.append(text,hit,hit+targetLen);
                idx=hit+targetLen;
            }else{
                sb.append(targetTerm);
                idx=hit+sourceLen;
            }

        }
        return sb.toString();

    }
}
