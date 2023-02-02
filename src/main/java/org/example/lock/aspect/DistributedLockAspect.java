package org.example.lock.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.example.lock.annotation.DistributedLock;
import org.example.lock.annotation.DistributedLocks;
import org.example.lock.exception.DistributeLockException;
import org.example.lock.param.DistributedLockParam;
import org.example.lock.service.DistributedLockService;
import org.springframework.context.expression.MapAccessor;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author v_ECD963
 */
@Aspect
@Slf4j
public class DistributedLockAspect {

    private final DistributedLockService distributedLockService;


    /**
     * 用于SpEL表达式解析.
     */
    private static final SpelExpressionParser SPEL_EXPRESSION_PARSER = new SpelExpressionParser();

    /**
     * 用于获取方法参数定义名字.
     */
    private static final DefaultParameterNameDiscoverer NAME_DISCOVERER = new DefaultParameterNameDiscoverer();

    private static String parseKey(JoinPoint jp, String spelString) {
        // 通过joinPoint获取被注解方法
        MethodSignature methodSignature = (MethodSignature) jp.getSignature();
        Method method = methodSignature.getMethod();
        // 使用spring的DefaultParameterNameDiscoverer获取方法形参名数组
        String[] paramNames = NAME_DISCOVERER.getParameterNames(method);
        // 解析过后的Spring表达式对象
        Expression expression = SPEL_EXPRESSION_PARSER.parseExpression(spelString);
        // spring的表达式上下文对象
        StandardEvaluationContext context = new StandardEvaluationContext();
        context.addPropertyAccessor(new MapAccessor());
        // 通过joinPoint获取被注解方法的形参
        Object[] args = jp.getArgs();
        // 给上下文赋值
        for (int i = 0; i < args.length; i++) {
            context.setVariable(paramNames[i], args[i]);
        }
        // 表达式从上下文中计算出实际参数值
        return expression.getValue(context).toString();
    }

    public DistributedLockAspect(DistributedLockService distributedLockService) {
        this.distributedLockService = distributedLockService;
    }

    /**
     * 定义切点
     */
    @Pointcut("@annotation(org.example.lock.annotation.DistributedLock) && @annotation(distributedLock)")
    public void redisDistributedLockAnnotationPointcut(DistributedLock distributedLock) {
    }

    /**
     * 定义切点
     */
    @Pointcut("@annotation(org.example.lock.annotation.DistributedLocks) && @annotation(distributedLocks)")
    public void redisDistributedLocksAnnotationPointcut(DistributedLocks distributedLocks) {
    }

    @Around(value = "redisDistributedLockAnnotationPointcut(distributedLock)")
    public Object tryLockAndProcess(ProceedingJoinPoint pjp, DistributedLock distributedLock) throws Throwable {;
        return doTryLockAndProcess(pjp, new DistributedLock[]{distributedLock});
    }

    @Around(value = "redisDistributedLocksAnnotationPointcut(distributedLocks)")
    public Object tryLockAndProcess(ProceedingJoinPoint pjp, DistributedLocks distributedLocks) throws Throwable {
        return doTryLockAndProcess(pjp, distributedLocks.value());
    }

    private Object doTryLockAndProcess(ProceedingJoinPoint pjp, DistributedLock[] distributedLocks) throws Throwable {
        List<DistributedLockParam> distributedLockParams = Arrays.stream(distributedLocks)
                .map(distributedLock -> parseDistributeLockParam(pjp, distributedLock))
                .collect(Collectors.toList());
        try {
            for (DistributedLockParam param : distributedLockParams) {
                if (!distributedLockService.tryLock(param)) {
                    throw new DistributeLockException(param.getMessage());
                }
            }
            return pjp.proceed();
        } finally {
            distributedLockParams.forEach(distributedLockService::unLock);
        }
    }

    private DistributedLockParam parseDistributeLockParam(ProceedingJoinPoint pjp, DistributedLock distributedLock) {
        return new DistributedLockParam(
                parseKey(pjp, distributedLock.spelKey()),
                distributedLock.waitingTime(),
                distributedLock.waitingTimeUnit(),
                distributedLock.message(),
                distributedLock.keepAliveTime(),
                distributedLock.keepAliveTimeUnit(),
                distributedLock.reentrant(),
                distributedLock.autoRenew());
    }

}
