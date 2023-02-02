package org.example.lock.annotation;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * @author v_ECD963
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
@Documented
@Repeatable(value = DistributedLocks.class)
public @interface DistributedLock {

    /**
     *
     * @return 标识
     */
    String spelKey();

    /**
     * @return 锁等待时间
     */
    long waitingTime() default 0L;

    /**
     * 默认毫秒
     * @return 锁等待时间的单位
     */
    TimeUnit waitingTimeUnit() default TimeUnit.MILLISECONDS;

    /**
     *
     * @return 超过等待时间后，获取锁失败的提示信息
     */
    String message() default "系统繁忙，请稍后重试";

    /**
     * 默认60s
     * @return 锁存活时间
     */
    long keepAliveTime() default 60L;

    /**
     * 默认秒
     * @return 锁存活时间的单位
     */
    TimeUnit keepAliveTimeUnit() default TimeUnit.SECONDS;

    /**
     * 默认可重入
     * @return 是否可重入
     */
    boolean reentrant() default true;

    /**
     * 默认自动续期
     * @return 是否自动续期
     */
    boolean autoRenew() default true;

}
