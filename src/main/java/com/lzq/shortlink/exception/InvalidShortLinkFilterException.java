package com.lzq.shortlink.exception;

/** 短链接筛选条件不合法时抛出的异常。 */
public class InvalidShortLinkFilterException extends RuntimeException {

    public InvalidShortLinkFilterException(String message) {
        super(message);
    }
}
