package com.lzq.shortlink.auth.exception;

/** 登录限流存储不可用时拒绝登录，避免限流保护静默失效。 */
public class LoginRateLimiterUnavailableException extends RuntimeException {

    public LoginRateLimiterUnavailableException() {
        super("登录安全校验暂不可用，请稍后重试");
    }
}
