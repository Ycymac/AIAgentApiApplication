package com.ycy.aiapplication.framework.idempotent.utils;

import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.util.ArrayUtil;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;


/**
 * SpEL表达式解析工具
 * 用于将字符串形式的SpEL解析为实际值，常用于缓存Key、幂等Key等场景
 */
public final class SpELUtil {

    /**
     * 校验并返回实际使用的SpEL表达式
     * 如果过SpEL包含SpEL标识符，调用parse方法解析实际值并返回
     * 否则直接返回原始字符串
     *
     * @param spEL SpEL表达式
     * @param method 方法
     * @param contextObj 上下文对象
     * @return 实际使用的SpEL表达式
     */
    public static Object parseKey(String spEL, Method method,Object[] contextObj){
        //定义需要识别的SpEL标记符（#：变量引用：T(java.lang.Math)，"T(" ：类型引用开头）
        List<String> spELFlag = ListUtil.of("#", "T(");

        //Optional：处理null出现的空指针
        //findFirst可能找到匹配的标识符——非空
        //          找不到——null空指针，异常

        Optional<String> optional = spELFlag.stream().
                filter(spEL::contains).//对每个元素检查SpEL表达式是否包含它
                        findFirst();//拿到第一个标识符

        if(optional.isPresent())//optional当中是否有值
            return parse(spEL,method,contextObj);
        //不包含SpEL标记符，原样返回
        return spEL;

    }

    /**
     * 解析SpEL表达式并返回表达式执行结果
     * @param spEL SpEL表达式
     * @param contextObj 上下文对象
     * @return 解析的字符串值
     */
    public static Object parse(String spEL, Method method ,Object[]contextObj){
        //默认参数名解析工具类，通过反射和字节码信息解析方法参数名
        DefaultParameterNameDiscoverer discoverer = new DefaultParameterNameDiscoverer();
        //SpEL语句解析类
        SpelExpressionParser parser = new SpelExpressionParser();
        //得到SpEL对应的表达式对象
        Expression exp = parser.parseExpression(spEL);
        //得到当前方法参数名
        String[] params = discoverer.getParameterNames(method);
        //SpEL执行所需的上下文，用于存储表达式可访问的变量
        StandardEvaluationContext context = new StandardEvaluationContext();
        //参数不为空
        if(ArrayUtil.isNotEmpty(params)){
            //将对象存到上下文当中（将方法参数名和对应的实参参数绑定为SpEl上下文变量，例如context.setVariable("id", 1001)）
            for(int len=0;len<params.length;len++){
                context.setVariable(params[len],contextObj[len]);
            }
        }
        //返回SpEL表达式解析执行出来的对象值
        return exp.getValue(context);
    }
}
