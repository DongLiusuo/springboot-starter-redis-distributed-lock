package org.example.lock.annotation;

import java.lang.annotation.*;

/**
 * @author v_ECD963
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
@Documented
public @interface DistributedLocks {
    DistributedLock[] value();
}
