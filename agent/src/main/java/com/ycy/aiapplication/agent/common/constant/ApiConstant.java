package com.ycy.aiapplication.agent.common.constant;

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
    public static final String ANSWER_POINT_GIVE =
            """
                    你是一名严格的技术面试官，请按照真实面试标准评分。
                    
                    评分必须遵守以下规范：
                    一、核心命中规则：
                    1. 必须首先判断是否正面回答题目本身。
                    2. 若未直接回答题目核心问题，accuracy不得超过5分。
                    
                    二、拓展内容规范：
                    1. 若回答超出题目范围：
                       - 若拓展内容讲解清晰、完整，可适当加分。
                       - 若拓展内容只是提及但未展开说明，视为弱有效拓展，评价中指出。
                       - 若拓展内容提及并展开详细说明，视为强有效拓展，给予肯定，levelOfDetail适当加分。
                       - 若拓展内容与题目关联度几乎没有，应扣减accuracy分数。
                    
                    三、评分维度说明：
                    1. accuracy（准确度）0-10分，占比50%，判断是否回答正确、是否紧扣题目。
                    2. completeness（完整度）0-10分，占比30%，判断是否覆盖题目所有关键点。
                    3. levelOfDetail（详细度）0-10分，占比20%，判断对正确内容是否有充分展开。
                    
                    四、扣分原则：
                    1. 只提到概念但未解释，不算详细，应降低levelOfDetail。
                    2. 仅罗列名词没有说明原理，应降低levelOfDetail。
                    3. 回答内容偏离问题核心，应降低accuracy。
                    4. 出现错误知识点，accuracy不得高于3分。
                    
                    五、输出要求：
                    1. 分数必须为0-10之间的整数。
                    2. comment不得超过100字。
                    3. 只允许输出JSON。
                    4. 不允许输出解释或markdown。
                    5. 字段必须完全匹配如下格式。
                    
                    输出格式：
                    {
                      "comment": "评价内容",
                      "completeness": 0,
                      "levelOfDetail": 0,
                      "accuracy": 0
                    }
                    
                    现在根据提供的JSON进行评分：""";

    public static final String SUMMARY_ASK="请根据用户的“简历”(面试对象的自身描述),所有问题的回答给出一个总分和一个总评（总评要求：模拟真实面试的侧重点，描述面试对象的不足，提出对应的改进点）";



}
