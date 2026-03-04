package com.ycy.aiapplication.agent.common.enums;


import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AIModelEnum {
    //使用flash模型回答更快
    //注意不能使用character生成问题，可能忽视我们生成15个问题的要求
    //但是使用character更人性化，给出的回答更像人
    //使用plus模型生成的报告更加精细详尽
    QUESTION_AI_MODEL("qwen-flash"),
    EVALUATION_AI_MODEL("qwen-flash-character"),
    SUMMARY_GENERATE_AI_MODEL("qwen-plus");



    private final String model;

}
