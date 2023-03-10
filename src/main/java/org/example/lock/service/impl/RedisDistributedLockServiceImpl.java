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

        // ????????????
        String threadSign = getThreadSign(param);

        // ?????????????????????
        long keepAliveTime = param.getKeepAliveTime();
        TimeUnit keepAliveTimeUnit = param.getKeepAliveTimeUnit();
        long keepAliveTimeMillis = keepAliveTimeUnit.toMillis(keepAliveTime);

        // ?????????????????????
        long waitingTime = param.getWaitingTime();
        TimeUnit waitingTimeUnit = param.getWaitingTimeUnit();
        long waitingTimeMillis = waitingTimeUnit.toMillis(waitingTime);

        for (; ; ) {
            Long pttl = redisTemplate.execute(tryLockScript, Collections.singletonList(key), keepAliveTimeMillis, threadSign);
            if (Objects.isNull(pttl)) {
                log.debug("????????????[{}]????????????,????????????[{}]", key, threadSign);
                if (autoRenew) {
                    // ??????????????????
                    createAutoRenewalTimer(key, threadSign, keepAliveTimeMillis);
                }
                return Boolean.TRUE;
            }
            if (System.currentTimeMillis() - start > waitingTimeMillis) {
                // ??????????????????????????????????????????????????????
                log.debug("????????????[{}][{}]????????????", key, threadSign);
                return Boolean.FALSE;
            }
            // ???50????????????????????????
            try {
                TimeUnit.MILLISECONDS.sleep(50);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            log.debug("????????????[{}][{}]????????????,??????????????????", key, threadSign);
        }
    }


    @Override
    public void unLock(DistributedLockParam param) {
        String key = REDIS_DISTRIBUTED_LOCK_KEY_PREFIX + param.getKey();
        // ?????????????????????
        long keepAliveTime = param.getKeepAliveTime();
        TimeUnit keepAliveTimeUnit = param.getKeepAliveTimeUnit();
        long keepAliveTimeMillis = keepAliveTimeUnit.toMillis(keepAliveTime);
        String threadSign = getThreadSign(param);
        Long result = redisTemplate.execute(unLockScript, Collections.singletonList(key), keepAliveTimeMillis, threadSign);
        if (result == null) {
            log.debug("??????????????????:[{}]?????????????????????????????????[{}]", key, threadSign);
            DISTRIBUTED_LOCK_ID_THREAD_LOCAL.remove();
        } else if (result == 1) {
            log.debug("??????????????????:[{}][{}]????????????", key, threadSign);
            DISTRIBUTED_LOCK_ID_THREAD_LOCAL.remove();
        } else if (result == 0) {
            log.debug("??????????????????:[{}][{}]???????????????1", key, threadSign);
        }
    }

    private String getThreadSign(DistributedLockParam param) {
        // ????????????
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
                log.debug("????????????[{}][{}]????????????", key, threadSign);
                try {
                    Boolean success = redisTemplate.execute(renewalScript, Collections.singletonList(key), keepAliveTimeMillis, threadSign);
                    if (Boolean.TRUE.equals(success)) {
                        log.debug("????????????[{}][{}]????????????", key, threadSign);
                    } else {
                        log.debug("????????????[{}]?????????????????????????????????[{}]???Timer??????", key, threadSign);
                        DISTRIBUTED_LOCK_ID_THREAD_LOCAL.remove();
                        cancel();
                    }
                } catch (Exception e) {
                    log.error("????????????[" + key + "][" + threadSign + "]???????????????Timer?????????????????????[" + e.getMessage() + "]", e);
                    DISTRIBUTED_LOCK_ID_THREAD_LOCAL.remove();
                    cancel();
                }
            }
        };
        new Timer(false).schedule(timerTask, keepAliveTimeMillis >> 1, keepAliveTimeMillis >> 1);
    }
}
