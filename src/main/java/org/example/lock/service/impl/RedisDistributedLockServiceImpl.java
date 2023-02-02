package org.example.lock.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.example.lock.param.DistributedLockParam;
import org.example.lock.service.DistributedLockService;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
public class RedisDistributedLockServiceImpl implements DistributedLockService {

    private static final String REDIS_DISTRIBUTED_LOCK_KEY_PREFIX = "LK_";

    private static final ThreadLocal<String> DISTRIBUTED_LOCK_ID_THREAD_LOCAL = new ThreadLocal<>();

    private final RedisTemplate<String, Object> redisTemplate;

    private final DefaultRedisScript<Long> tryLockScript;

    private final DefaultRedisScript<Boolean> renewalScript;

    private final DefaultRedisScript<Long> unLockScript;

    public RedisDistributedLockServiceImpl(RedisTemplate<String, Object> redisTemplate, ResourceLoader resourceLoader) {
        tryLockScript = new DefaultRedisScript<>();
        tryLockScript.setResultType(Long.class);
        tryLockScript.setScriptSource(new ResourceScriptSource(resourceLoader.getResource("classpath:/lock/redis_lock_script.lua")));
        log.trace("redis distributed lock script [{}] load success", "tryLock");
        renewalScript = new DefaultRedisScript<>();
        renewalScript.setResultType(Boolean.class);
        renewalScript.setScriptSource(new ResourceScriptSource(resourceLoader.getResource("classpath:/lock/redis_renewal_script.lua")));
        log.trace("redis distributed lock script [{}] load success", "renewalLock");
        unLockScript = new DefaultRedisScript<>();
        unLockScript.setResultType(Long.class);
        unLockScript.setLocation(resourceLoader.getResource("classpath:/lock/redis_unlock_script.lua"));
        log.trace("redis distributed lock script [{}] load success", "unLock");
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean tryLock(DistributedLockParam param) {
        long start = System.currentTimeMillis();

        String key = REDIS_DISTRIBUTED_LOCK_KEY_PREFIX + param.getKey();

        boolean autoRenew = param.isAutoRenew();

        // 线程签名
        String threadSign = getThreadSign(param);

        // 超时时间毫秒数
        long keepAliveTime = param.getKeepAliveTime();
        TimeUnit keepAliveTimeUnit = param.getKeepAliveTimeUnit();
        long keepAliveTimeMillis = keepAliveTimeUnit.toMillis(keepAliveTime);

        // 等待时间毫秒数
        long waitingTime = param.getWaitingTime();
        TimeUnit waitingTimeUnit = param.getWaitingTimeUnit();
        long waitingTimeMillis = waitingTimeUnit.toMillis(waitingTime);

        for (; ; ) {
            Long pttl = redisTemplate.execute(tryLockScript, Collections.singletonList(key), keepAliveTimeMillis, threadSign);
            if (Objects.isNull(pttl)) {
                log.debug("分布式锁[{}]加锁成功,线程签名[{}]", key, threadSign);
                if (autoRenew) {
                    // 创建续期任务
                    createAutoRenewalTimer(key, threadSign, keepAliveTimeMillis);
                }
                return Boolean.TRUE;
            }
            if (System.currentTimeMillis() - start > waitingTimeMillis) {
                // 到了超时时间，还未加锁成功，返回失败
                log.debug("分布式锁[{}][{}]加锁失败", key, threadSign);
                return Boolean.FALSE;
            }
            // 每50毫秒重新尝试加锁
            try {
                TimeUnit.MILLISECONDS.sleep(50);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            log.debug("分布式锁[{}][{}]加锁失败,尝试重新加锁", key, threadSign);
        }
    }


    @Override
    public void unLock(DistributedLockParam param) {
        String key = REDIS_DISTRIBUTED_LOCK_KEY_PREFIX + param.getKey();
        // 超时时间毫秒数
        long keepAliveTime = param.getKeepAliveTime();
        TimeUnit keepAliveTimeUnit = param.getKeepAliveTimeUnit();
        long keepAliveTimeMillis = keepAliveTimeUnit.toMillis(keepAliveTime);
        String threadSign = getThreadSign(param);
        Long result = redisTemplate.execute(unLockScript, Collections.singletonList(key), keepAliveTimeMillis, threadSign);
        if (result == null) {
            log.debug("分布式锁释放:[{}]不存在或不属于当前线程[{}]", key, threadSign);
            DISTRIBUTED_LOCK_ID_THREAD_LOCAL.remove();
        } else if (result == 1) {
            log.debug("分布式锁释放:[{}][{}]释放成功", key, threadSign);
            DISTRIBUTED_LOCK_ID_THREAD_LOCAL.remove();
        } else if (result == 0) {
            log.debug("分布式锁释放:[{}][{}]引用计数减1", key, threadSign);
        }
    }

    private String getThreadSign(DistributedLockParam param) {
        // 线程签名
        long threadId = Thread.currentThread().getId();
        String threadName = Thread.currentThread().getName();
        String threadSign;
        if (DISTRIBUTED_LOCK_ID_THREAD_LOCAL.get() == null)
            threadSign = UUID.randomUUID().toString().replace("-", "") + ":" + threadId + "@" + threadName + "";
        else threadSign = DISTRIBUTED_LOCK_ID_THREAD_LOCAL.get();
        if (param.isReentrant()) DISTRIBUTED_LOCK_ID_THREAD_LOCAL.set(threadSign);
        return threadSign;
    }

    private void createAutoRenewalTimer(String key, String threadSign, long keepAliveTimeMillis) {
        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                log.debug("分布式锁[{}][{}]准备续期", key, threadSign);
                try {
                    Boolean success = redisTemplate.execute(renewalScript, Collections.singletonList(key), keepAliveTimeMillis, threadSign);
                    if (Boolean.TRUE.equals(success)) {
                        log.debug("分布式锁[{}][{}]续期成功", key, threadSign);
                    } else {
                        log.debug("分布式锁[{}]不存在或不属于当前线程[{}]，Timer取消", key, threadSign);
                        DISTRIBUTED_LOCK_ID_THREAD_LOCAL.remove();
                        cancel();
                    }
                } catch (Exception e) {
                    log.error("分布式锁[" + key + "][" + threadSign + "]续期异常，Timer取消，异常信息[" + e.getMessage() + "]", e);
                    DISTRIBUTED_LOCK_ID_THREAD_LOCAL.remove();
                    cancel();
                }
            }
        };
        new Timer(false).schedule(timerTask, keepAliveTimeMillis >> 1, keepAliveTimeMillis >> 1);
    }
}
