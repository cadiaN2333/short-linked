package com.lzq.shortlink.exception;

/** 自定义短码命中系统保留路径时抛出的异常。 */
public class ReservedShortCodeException extends RuntimeException {

    public ReservedShortCodeException(String message) {
        super(message);
    }
}
