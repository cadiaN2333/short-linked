package com.lzq.shortlink.exception;

/** 目标地址不满足短链接的输入要求。 */
public class InvalidTargetUrlException extends RuntimeException{

    public InvalidTargetUrlException(String message) {
        super(message);
    }
}
