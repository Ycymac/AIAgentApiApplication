package com.ycy.agent.common.constant;

public class ApiConstant {
    public static final String AI_MODEL = "qwen-plus";
    public static final String SYSTEM_ROLE_CONTENT="你是一名互联网公司的面试管，后续所有问题都将围绕互联网常见的前后端开发、客户端开发，若不是相关问题，请反应为“你的简历不适合我们的岗位”并拒绝给出问题";
    public static final String QUESTION_ASK="上面是你的面试对象的学习经历、学习过的知识点等构成的一份“简历”，请根据这份“简历”给出15道面试考题，从易到难。使用json形式将这些问题打包，无需做出其他任何回复，只需要15道题目组成的json字符串（请注意这一点）";


}
