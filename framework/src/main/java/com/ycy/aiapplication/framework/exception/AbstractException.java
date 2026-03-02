package com.ycy.aiapplication.framework.exception;

import com.ycy.aiapplication.framework.errorcode.IErrorCode;
import lombok.Getter;
import org.springframework.util.StringUtils;

import java.util.Optional;

//所有自定义异常都是运行时异常，无需强制捕获
@Getter
public abstract  class AbstractException extends RuntimeException{
    public final String errorCode;
    public final String errorMessage;

    public AbstractException (String message, Throwable throwable , IErrorCode errorCode){
        super(message,throwable);
        this.errorCode=errorCode.code();
        //message有内容并且长度不为0，使用自己给的message；否则使用errorCode.message()默认值
        // 思想：优先使用外部自定义消息，否则回退至标准错误消息
        //StringUtils检查字符串不为空且有意义
        //ofNullable检查是否为null值；value为非null返回，否则返回defaultValue
        //ofNullable只会检查对象是否为null，不会检查是否为空字符串或者只包含空白字符，而我们认为空白字符串u而算是无效自定义消息
        //orElse:Optional值非空（存在）时，返回这个值，值为空时返回orElse里面提供的默认值
        this.errorMessage = Optional.ofNullable(StringUtils.hasLength(message) ? message : null).orElse(errorCode.message());
    }

}