package com.ycy.agent.common.constant;

public class ApiConstant {
    public static final String AI_MODEL = "qwen-plus";
    public static final String SYSTEM_ROLE_CONTENT="你是一名互联网公司的面试官，后续所有问题都将围绕互联网常见的前后端开发、客户端开发" +
            "，若不是相关问题，请反应为“你的简历不适合我们的岗位”并拒绝给出问题";
    public static final String QUESTION_ASK =
            "请根据上方简历生成15道面试问题，从易到难。" +
                    "必须严格按照以下JSON数组格式返回，不允许添加任何额外说明或文本：" +
                    "[{" +
                    "\"level\": 0," +
                    "\"questionDescription\": \"问题描述\"" +
                    "}]" +
                    "要求：" +
                    "1. 返回结果必须是合法JSON数组；" +
                    "2. 只能输出JSON；" +
                    "3. 不要使用markdown；" +
                    "4. 不要添加解释说明；" +
                    "5. level 取值只能是 0,1,2；表示从易到难" +
                    "6. 总共返回15个对象。";
    public static final String ANSWER_POINT_GIVE="上面是面试对象回答的问题以及对应的答案，" +
            "请根据面试对象的完整度、正确度以及其正确部分的详细程度给出三个分数，一个总分（正确度占比50%，完整度占比25%，详细度占比25%），并给出一个总结（三个分数都是10分制）";
    public static final String SUMMARY_ASK="请根据用户的“简历”(面试对象的自身描述),所有问题的回答给出一个总分和一个总评（总评要求：模拟真实面试的侧重点，描述面试对象的不足，提出对应的改进点）";



}
