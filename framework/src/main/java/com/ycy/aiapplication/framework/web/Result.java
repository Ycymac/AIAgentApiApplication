package com.ycy.aiapplication.framework.web;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;

@Data
//用于统一控制类中的getter/setter方法生成策略和访问风格，影响对对应注生成的访问器的方法行为
//chain属性设置为true之后支持链式调用getter/setter方法
//fluent属性：作用：setter方法不带set前缀，方法名等于字段名（fluent为true时自动隐含）
//prefix属性指定字段名称前缀，默认为空字符串，（原因：Lombok在生成getter/setter方法时自动去除前缀）常用于字段以m/m_/_等开头
@Accessors(chain = true)
public class Result<T> implements Serializable {
    //标记/序列化方法相关私有方法、字段
    @Serial
    //用于表示一个可序列化类的版本号
    //对象被反序列化时，将当前类和序列化时存储的ID进行比较，一致表示序列化成功，不一致则抛出异常，反序列化失败
    private static final long serialVersionUID = 5679018624309023727L;

    /**
     * 正确返回码
     */
    public static final String SUCCESS_CODE = "0";

    /**
     * 返回码
     */
    private String code;

    /**
     * 返回消息
     */
    private String message;

    /**
     * 响应数据
     */
    private T data;

    /**
     * 请求ID
     */
    private String requestId;

    public boolean isSuccess() {
        return SUCCESS_CODE.equals(code);
    }
}
