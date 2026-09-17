package com.lzq.shortlink.exception;

/** 自定义短码已被占用时抛出的异常。 */
public class ShortCodeAlreadyExistsException extends RuntimeException {

    public ShortCodeAlreadyExistsException(String message) {
        super(message);
    }
}
