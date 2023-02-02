--- KEYS[1] 是锁的key
--- ARGV[1] 是超时时间
--- ARGV[2] 线程唯一标识
local key = KEYS[1] --锁的key
local keepAliveTimeMillis = ARGV[1] -- 锁超时时间
local threadSign = ARGV[2]  -- 线程唯一标识

 -- 锁不存在执行的流程
if (redis.call('exists', key) == 0) then
   -- 不存在获取锁
   redis.call('hset', key, threadSign, 1)
   -- 设置超时时间
   redis.call('pexpire', key, keepAliveTimeMillis)
   -- 返回nil
   return nil
end

-- 锁存在执行的流程
if (redis.call('hexists', key, threadSign) == 1) then
  -- 锁重入 当前线程对应的value增加一
  redis.call('hincrby', key, threadSign, 1)
  -- 重新设置超时时间
  redis.call('pexpire', key, keepAliveTimeMillis)
  return nil
end

-- 返回超时时间
return redis.call('pttl', key)
