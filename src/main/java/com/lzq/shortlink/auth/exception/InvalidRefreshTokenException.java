package com.lzq.shortlink.auth.exception;

public class InvalidRefreshTokenException
        extends RuntimeException {

    public InvalidRefreshTokenException() {
        super("刷新令牌无效或已过期");
    }
}