--[[
]]
local key = KEYS[1] --锁的key
local keepAliveTimeMillis = ARGV[1] -- 锁超时时间
local threadSign = ARGV[2]  -- 线程唯一标识
-- 如果解锁线程和加锁线程不是一个线程时，直接返回null
if (redis.call('hexists', key, threadSign) == 0) then
    return nil
end
-- 使用hincrby使得字段值减1
local counter = redis.call('hincrby', key, threadSign, -1)
-- 如果剩余解锁次数大于0
if (counter > 0) then
    -- 刷新过期时间，返回0
    redis.call('pexpire', key, keepAliveTimeMillis)
    return 0
else
    -- 剩余次数为0，可以直接释放锁
    redis.call('del', key)
    -- 往指定channel中发布锁被释放的消息，并返回1
    -- redis.call('publish', KEYS[2], ARGV[1])
    return 1
end
return nil
