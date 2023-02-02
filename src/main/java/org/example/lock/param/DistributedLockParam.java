package org.example.lock.param;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.concurrent.TimeUnit;

@Data
@AllArgsConstructor
public class DistributedLockParam {

    /**
     * 标识
     */
    private String key;

    /**
     * 锁等待时间
     */
    private long waitingTime;

    /**
     * 锁等待时间的单位
     */
    private TimeUnit waitingTimeUnit;

    /**
     * 超过等待时间后，获取锁失败的提示信息
     */
    private String message;

    /**
     * 锁存活时间
     */
    private long keepAliveTime;

    /**
     * 锁存活时间的单位
     */
    private TimeUnit keepAliveTimeUnit;

    /**
     * 是否可重入
     */
    private boolean reentrant;

    /**
     * 是否自动续期
     */
    private boolean autoRenew;

    public DistributedLockParam(String key) {
        this(key,
                0L,
                TimeUnit.MICROSECONDS,
                "系统繁忙，请稍后重试",
                60,
                TimeUnit.SECONDS,
                true,
                true);
    }

    public DistributedLockParam(String key, long keepAliveTimeMillis) {
        this(
                key,
                0L,
                TimeUnit.MICROSECONDS,
                "系统繁忙，请稍后重试",
                keepAliveTimeMillis,
                TimeUnit.MILLISECONDS,
                true,
                true);
    }


}
