package com.ycy.aiapplication.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 面试问题难度枚举类
 */
@Getter
@RequiredArgsConstructor
public enum QuestionLevelEnum {

    EASY(0,"简单"),
    MEDIUM(1,"中等"),
    HARD(2,"难");

    private final int level;
    private final String description;

    public static QuestionLevelEnum getByLevel(int level){
        for(QuestionLevelEnum questionLevelEnum:values()){
            if(questionLevelEnum.level==level){
                return questionLevelEnum;
            }

        }
        return null;
    }

}
