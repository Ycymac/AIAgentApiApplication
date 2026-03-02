package com.ycy.aiapplication.framework.web;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.ycy.aiapplication.framework.errorcode.BaseErrorCode;
import com.ycy.aiapplication.framework.exception.AbstractException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Optional;

@Slf4j
//构建统一的RESTful API异常处理机制
//核心作用：捕获整个应用当中抛出的异常并返回结构化的json错误信息，避免暴露内部堆栈/返回500错误页面
//将后端抛出的异常（尤其业务异常，即业务执行过程当中业务规则不满足而主动抛出的异常）经过统一处理转化成前端易于理解、便于处理的结构化错误信息返回给前端
@RestControllerAdvice
//作用：构建一个去哪记得面向所有@RestController（我们的控制器类）的aop增强组件
//核心构成：@Controller（表明是一个spring控制层组件）Advice（aop术语，意味着代码不是处理具体的业务请求的，而是用来增强或者拦截其他组件的）
// +ResponseBody（这个类当中的所有返回值都不叫交给视图解析器当作页面路径进行处理而是被序列化为json格式（通常）写入http响应体当中）
/**
 * 主要目标：
 * 1.将参数校验异常转换为可读的前端提示
 * 2.处理应用当中的自定义异常，记录有用日志并返回
 * 3.捕获其余未处理异常，记录日志并返回通用失败响应（避免暴露内部实现细节给客户端）
 * 4.统一日志格式，便于链路追踪、定位
 */
public class GlobalExceptionHandler {
    //为什么处理所有类型的异常，都和HttpServletRequest相关：
    // 我们所有的异常都是在执行相关业务时抛出/产生的，而这些业务和前端发送过来的请求关系密切，所以需要HttpServletRequest，参数列表当中也必定会包含
    /**
     * 拦截参数验证异常
     * 触发场景：控制层方法参数使用@Valid @Validated 注解进行Bean参数校验
     * 参数校验不通过Spring自动抛出参数校验失败异常
     */
    @SneakyThrows
    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    public Result validExceptionHandler(HttpServletRequest request , MethodArgumentNotValidException ex){
        BindingResult bindingResult = ex.getBindingResult();
        //常见策略：只返回第一个错误给前端
        FieldError firstFieldError = CollectionUtil.getFirst(bindingResult.getFieldErrors());
        //map方法：不使用if-else语句安全访问ofNullable当中的属性、方法
        //map得到的返回值类型是Optional<String>
        //orElse方法是Optional当中的取值方法
        //作用：Optional当中右值返回这个值，否则返回orElse设定的默认值，将Optional<String>进行了解包（保证内部传输、链式调用时的安全）
        String exceptionStr = Optional.ofNullable(firstFieldError)
                .map(FieldError::getDefaultMessage)
                .orElse(StrUtil.EMPTY);
        log.error("[{}] {} [ex] {}", request.getMethod(), getUrl(request), exceptionStr);
        return Results.failure(BaseErrorCode.CLIENT_ERROR.code(), exceptionStr);
    }

    /**
     *拦截应用内抛出的异常（自定义异常）
     */
    @ExceptionHandler(value = AbstractException.class)
    public Result abstractException(HttpServletRequest request,AbstractException ex){
        //当前错误对应异常原因不为空，进行错误日志打印
        //返回对应结果
        if(ex.getCause()!=null){
            log.error("[{}] {} [ex] {}", request.getMethod(), request.getRequestURL().toString(), ex, ex.getCause());
            return Results.failure(ex);
        }
        //错误对应异常原因为空，使用StringBuilder安全拼接错误、错误堆栈轨迹（方法调用轨迹）（至多五个）进行打印
        StringBuilder stackTraceBuilder = new StringBuilder();
        stackTraceBuilder.append(ex.getClass().getName()).append(":").append(ex.getErrorMessage()).append("\n");
        StackTraceElement[] stackTrace = ex.getStackTrace();
        for(int i=0;i<Math.min(5,stackTrace.length);i++){
            stackTraceBuilder.append("\tat").append(stackTrace[i]).append("\n");
        }
        log.error("[{}] {} [ex] {} \n\n{}", request.getMethod(), request.getRequestURL().toString(), ex, stackTraceBuilder);
        return Results.failure(ex);
    }

    /**
     *拦截未捕获异常
     */
    @ExceptionHandler(value = Throwable.class)
    public Result defaultErrorHandler(HttpServletRequest request,Throwable throwable){
        log.error("[{}] {} ", request.getMethod(), getUrl(request), throwable);
        return Results.failure();
    }



    private String getUrl(HttpServletRequest request){
        if(StringUtils.isEmpty(request.getQueryString())){
            return request.getRequestURL().toString();
        }
        return request.getRequestURL().toString()+"?"+request.getQueryString();
    }
}