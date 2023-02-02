local key = KEYS[1] --锁的key
local keepAliveTimeMillis = ARGV[1] -- 锁超时时间
local threadSign = ARGV[2]  -- 线程唯一标识
if (redis.call('hexists', key, threadSign) == 1) then
    redis.call('pexpire', key, keepAliveTimeMillis)
    return true
end
return false

