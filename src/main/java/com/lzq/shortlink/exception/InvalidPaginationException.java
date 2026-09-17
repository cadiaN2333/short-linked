package com.lzq.shortlink.exception;

/** 分页参数不符合接口约束时抛出的异常。 */
public class InvalidPaginationException extends RuntimeException {

    public InvalidPaginationException(String message) {
        super(message);
    }
}
