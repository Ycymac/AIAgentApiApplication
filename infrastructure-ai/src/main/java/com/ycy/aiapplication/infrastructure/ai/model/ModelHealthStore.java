package com.ycy.aiapplication.infrastructure.ai.model;

import com.ycy.aiapplication.infrastructure.ai.config.AIModelProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模型监测工具类
 * 当前健康状态检测用于常用的大模型对话当中
 */
@Component
@RequiredArgsConstructor
public class ModelHealthStore {

    private final AIModelProperties properties;

    private final Map<String, ModelHealth> healthById = new ConcurrentHashMap<>();

    /**
     * 判断当前模型服务是否熔断
     */
    public boolean isOpen(String id) {
        ModelHealth health = healthById.get(id);
        if (health == null) {
            return false;
        }
        return health.state == State.OPEN && health.openUntil > System.currentTimeMillis();
    }

    /**
     * 熔断器方法，用于判断当前大模型是否可用，检测大模型状态是否恢复
     */
    public boolean allowCall(String id) {
        if (id == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        //Lambda表达式设置只能访问有效最终的局部变量，而且若使用普通的boolean对象，传入的只是变量的引用副本，方法中修改无效
        //使用数组进行包装
        final boolean[] allowed = {false};
        //使用compute方法执行对应修改
        healthById.compute(id, (key, value) -> {
            ModelHealth health = value == null ? new ModelHealth() : value;
            //熔断状态
            if (health.state == State.OPEN) {
                //熔断期内，保持熔断
                if (health.openUntil > now) {
                    return health;
                }
                //熔断期结束，转为尝试执行状态，尝试执行一次
                health.state = State.HALF_OPEN;
                health.halfOpenInFlight = true;
                allowed[0] = true;
                return health;
            }
            //尝试期，只允许单个线程尝试执行
            if (health.state == State.HALF_OPEN) {
                //当前有线程尝试执行，禁止其他线程执行
                if (health.halfOpenInFlight) {
                    return health;
                }
                //当前没有线程尝试，允许当前线程尝试
                health.halfOpenInFlight = true;
                allowed[0] = true;
                return health;
            }
            //健康状态，允许执行
            allowed[0] = true;
            return health;
        });
        return allowed[0];
    }

    public void markSuccess(String id) {
        if (id == null) {
            return;
        }
        healthById.compute(id, (key, value) -> {
            ModelHealth health = value == null ? new ModelHealth() : value;
            health.state = State.CLOSED;
            health.consecutiveFailures = 0;
            health.openUntil = 0L;
            health.halfOpenInFlight = false;
            return health;
        });
    }

    public void markFailure(String id) {
        if (id == null) {
            return;
        }
        long now = System.currentTimeMillis();
        healthById.compute(id, (key, value) -> {
            ModelHealth health = value == null ? new ModelHealth() : value;
            if (health.state == State.HALF_OPEN) {
                health.state = State.OPEN;
                health.openUntil = now + properties.getSelection().getOpenDurationMs();
                health.consecutiveFailures = 0;
                health.halfOpenInFlight = false;
                return health;
            }
            health.consecutiveFailures++;
            if (health.consecutiveFailures >= properties.getSelection().getFailureThreshold()) {
                health.state = State.OPEN;
                health.openUntil = now + properties.getSelection().getOpenDurationMs();
                health.consecutiveFailures = 0;
            }
            return health;
        });
    }

    private static final class ModelHealth {
        private int consecutiveFailures;
        private long openUntil;
        private boolean halfOpenInFlight;
        private State state = State.CLOSED;
    }

    private enum State {
        CLOSED,
        OPEN,
        HALF_OPEN
    }
}
