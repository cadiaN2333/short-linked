package com.lzq.shortlink.exception;

/** 短链接状态不在允许范围内时抛出的异常。 */
public class InvalidShortLinkStatusException extends RuntimeException {

    public InvalidShortLinkStatusException(String message) {
        super(message);
    }
}
