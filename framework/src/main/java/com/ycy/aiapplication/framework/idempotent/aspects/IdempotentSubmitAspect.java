package com.ycy.aiapplication.framework.idempotent.aspects;

import cn.hutool.crypto.digest.DigestUtil;
import com.alibaba.fastjson2.JSON;

import com.ycy.aiapplication.framework.context.UserContext;
import com.ycy.aiapplication.framework.exception.ClientException;
import com.ycy.aiapplication.framework.idempotent.annotations.IdempotentSubmit;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 防止用户重复提交表单信息切面控制类
 */
@Aspect
@Component
@RequiredArgsConstructor
public class IdempotentSubmitAspect {

    public final RedissonClient redissonClient;

    /**
     * 增强方法标记{@link IdempotentSubmit}注解逻辑
     */
    //join point:执行过程的当中的连接点（方法调用、异常抛出等）aop当中通常代表被代理的方法调用点（这里是调用注解的地方）
    //服务模块使用注解时，aop拦截器介入
    @Around("@annotation(com.ycy.aiapplication.framework.idempotent.annotations.IdempotentSubmit)")
    public Object noDuplicateSubmit(ProceedingJoinPoint joinPoint)throws Throwable{
        //拿到注解对应实例
        IdempotentSubmit noDuplicateSubmit = getNoDuplicateSubmitAnnotation(joinPoint);
        //获取分布式标识
        String lockKey = String.format("no-duplicate-submit:path:%s:currentUserId:%s:md5:%s", getServletPath(), getCurrentUserId(), calArgsMD5(joinPoint));
        RLock lock = redissonClient.getLock(lockKey);
        if (!lock.tryLock()) {
            throw new ClientException(noDuplicateSubmit.message());
        }
        Object result;
        try{
            //执行标记了防重复注解的方法的处理逻辑
            result=joinPoint.proceed();
        }finally {
            lock.unlock();
        }
        //Spring自动转换为声明的返回类型（因为使用了代理对象）
        return result;

    }

    /**
     * @return 返回自定义防重复提交注解
     */
    public static IdempotentSubmit getNoDuplicateSubmitAnnotation(ProceedingJoinPoint joinPoint)throws NoSuchMethodException{
        //返回被拦截方法的签名信息（方法名称、参数类型、返回类型）
        //aop当中通常是MethodSignature类型
        MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
        //直接getMethod得到的是代理类当中的方法，注解是写在原始目标类的方法上面的，直接从代理类上面拿可能拿不到
        //获取目标队对象（target）之后获取实际的类，使用方法名+参数类型通过反射获取原始方法
        //通过这个原始方法拿到注解
        Method targetMethod = joinPoint.getTarget().getClass().getDeclaredMethod(methodSignature.getName(), methodSignature.getMethod().getParameterTypes());
        return  targetMethod.getAnnotation(IdempotentSubmit.class);
    }

    /**
     * @return 获取当前线程上下文ServletPath
     */
    private String getServletPath(){
        ServletRequestAttributes sra = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return sra.getRequest().getServletPath();
    }

    /**
     * @return 获取当前操作用户的id
     */
    private String getCurrentUserId(){
        // 用户属于非核心功能，这里先通过模拟的形式代替。后续如果需要后管展示，会重构该代码
        //正常应该是通过threalocal拿到存储的userid
        return UserContext.getNickName();
    }

    /**
     * 通过fastjson生成对应的joinPoint的md5值
     */
    private String calArgsMD5(ProceedingJoinPoint joinPoint){
        //通过JoinPoint.getArgs能够拿到调用传入的具体参数，这样才能防止同一个用户相同参数短时间内多次调用 ， 而不会拦截相同用户相同接口不同参数的请求
        Object[] args = joinPoint.getArgs();
        Object[] lightweightArgs = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            lightweightArgs[i] = lightweightArg(args[i]);
        }
        return DigestUtil.md5Hex(JSON.toJSONBytes(lightweightArgs));
    }

    private Object lightweightArg(Object arg) {
        // MultipartFile 只提取可用于识别文件的轻量元数据，避免读取文件字节或输入流。
        if (arg instanceof MultipartFile multipartFile) {
            return lightweightMultipartFile(multipartFile);
        }
        // 集合参数逐项处理，应对 List<MultipartFile> 等批量上传场景。
        if (arg instanceof Collection<?> collection) {
            ArrayList<Object> result = new ArrayList<>(collection.size());
            for (Object item : collection) {
                result.add(lightweightArg(item));
            }
            return result;
        }
        // 数组参数通过反射读取，兼容对象数组和基本类型数组。
        if (arg != null && arg.getClass().isArray()) {
            int length = Array.getLength(arg);
            ArrayList<Object> result = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                result.add(lightweightArg(Array.get(arg, i)));
            }
            return result;
        }
        return arg;
    }

    private Map<String, Object> lightweightMultipartFile(MultipartFile file) {
        // 使用 LinkedHashMap 保持字段顺序稳定，确保相同文件元数据得到一致的 MD5 输入。
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", file.getName());
        result.put("originalFilename", file.getOriginalFilename());
        result.put("contentType", file.getContentType());
        result.put("size", file.getSize());
        result.put("empty", file.isEmpty());
        return result;
    }

}
