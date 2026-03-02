package com.ycy.aiapplication.framework.errorcode;

//错误码规范接口
//所有项目当中定义的错误码枚举都能实现该接口，从而同一错误结构
public interface IErrorCode {
    /**
     * 错误码
     */
    String code();
    /**
     * 错误信息
     */
    String message();

}
