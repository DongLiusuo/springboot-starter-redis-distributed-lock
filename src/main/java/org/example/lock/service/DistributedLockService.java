package org.example.lock.service;

import org.example.lock.param.DistributedLockParam;

public interface DistributedLockService {

    boolean tryLock(DistributedLockParam param);
    void unLock(DistributedLockParam param);
}
