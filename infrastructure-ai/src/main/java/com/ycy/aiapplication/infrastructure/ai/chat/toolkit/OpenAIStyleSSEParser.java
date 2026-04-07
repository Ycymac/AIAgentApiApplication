package com.ycy.aiapplication.infrastructure.ai.chat.toolkit;

import cn.hutool.json.JSONNull;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * OpenAI协议风格SSE解析器
 * 支持从delta当中提取content，以及可选的reasoning_content
 */
public final class OpenAIStyleSSEParser {

    private static final String DATA_PREFIX="data:";
    private static final String DONE_MARKER="[DONE]";

    private OpenAIStyleSSEParser(){}

    /**
     * 解析一行流式返回数据
     * reasoning_content:大模型深度思考过程内容（深度思考的时候一定是流式的）
     * @return ParsedEvent 返回完整解析事件
     */
    public static ParsedEvent parseLine(String line,boolean reasoningEnabled){
        if(line==null||line.isBlank())
            return ParsedEvent.empty();
        //去除首尾空格
        String payload=line.trim();
        //是中间data数据，去除“data：”以及空格
        if (payload.startsWith(DATA_PREFIX))
            payload=payload.substring(DATA_PREFIX.length()).trim();
        //不管大小写和“done”内容相等,说明已经结束
        if(DONE_MARKER.equalsIgnoreCase(payload))
            return ParsedEvent.done();

        JSONObject obj = JSONObject.parseObject(payload);
        JSONArray choices = obj.getJSONArray("choices");
        if(choices==null||choices.isEmpty())
            return ParsedEvent.empty();

        JSONObject choice0 = choices.getJSONObject(0);
        String content = extractText(choice0, "content");
        String reasoning = reasoningEnabled ? extractReasoning(choice0) : null;
        boolean completed=hasFinishReason(choice0);
        return new ParsedEvent(content,reasoning,completed);

    }

    private static boolean hasFinishReason(JSONObject choice){
        if(choice==null||choice.isEmpty()||!choice.containsKey("finish_reason"))
            return false;
        Object finishReason = choice.get("finish_reason");
        //既不是null，json形式的内容也不为空
        return finishReason!=null&&!finishReason.equals(JSONNull.NULL);

    }

    //提取增量内容
    private static String  extractText(JSONObject choice,String fieldName){
        if(choice==null)
            return null;
        //如果是流式响应，增量内容在delta当中
        if(choice.containsKey("delta")&&choice.get("delta") instanceof JSONObject){
            JSONObject delta = choice.getJSONObject("delta");
            if(delta.containsKey(fieldName)){
                Object value = delta.get(fieldName);
                if(value!=null&&!value.equals(JSONNull.NULL))
                    return String.valueOf(value);
            }
        }
        //兜底策略：流式响应格式当中没有，检查是否有message且message当中有我们想要的field
        if(choice.containsKey("message")&& choice.get("message") instanceof JSONObject){
            JSONObject message = choice.getJSONObject("message");
            if(message.containsKey(fieldName)){
                Object value = message.get(fieldName);
                if(value!=null&&!value.equals(JSONNull.NULL)){
                    return String.valueOf(value);
                }
            }
        }
        return null;
    }

    private static String extractReasoning(JSONObject choice) {
        String reasoning = extractText(choice, "reasoning_content");
        if (reasoning != null) {
            return reasoning;
        }
        return extractText(choice, "reasoning_context");
    }

    /**
     * record纪录类（用于声明不可变的数据载体类）
     * 以下为纪录类组件
     * 自定义的每一个组件，Java编译器会自动生成以下内容：
     * 1.私有的final字段：对应组建的私有且不可变字段
     * 2.公共访问器方法：生成和组件同名的发方法，获取字段值
     * 3.构造函数：一个包含所有组件参数的公共构造函数
     * 4.equals()和hashCode()：基于所有组件值自动生成
     * 5.toString():自动生成包含组件名、组件值的字符串表示
     */
    /*原代码实现
        record ParsedEvent(String content, String reasoning, boolean completed) {

        static ParsedEvent empty() {
            return new ParsedEvent(null, null, false);
        }

        static ParsedEvent done() {
            return new ParsedEvent(null, null, true);
        }

        boolean hasContent() {
            return content != null && !content.isEmpty();
        }

        boolean hasReasoning() {
            return reasoning != null && !reasoning.isEmpty();
        }
    }
     */
    @Getter//为了接近record的不可变性，不使用@Data注解而是@Getter
    @NoArgsConstructor
    @AllArgsConstructor
   public static class ParsedEvent{
       private String content;
       private String reasoning;
       private boolean completed;
       static  ParsedEvent empty() {
            return new ParsedEvent(null, null, false);
        }

       static  ParsedEvent done() {
            return new ParsedEvent(null, null, true);
        }

        public boolean hasContent() {
            return content != null && !content.isEmpty();
        }

        public boolean hasReasoning() {
            return reasoning != null && !reasoning.isEmpty();
        }
    }


}
