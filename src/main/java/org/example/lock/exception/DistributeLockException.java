package org.example.lock.exception;

public class DistributeLockException extends RuntimeException {

    public DistributeLockException(){
        super();
    }

    public DistributeLockException(String message) {
        super(message);
    }


}
