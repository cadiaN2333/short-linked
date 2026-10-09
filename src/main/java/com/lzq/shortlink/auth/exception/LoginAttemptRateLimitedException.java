package com.lzq.shortlink.auth.exception;

/** 登录失败次数达到限制时抛出。 */
public class LoginAttemptRateLimitedException extends RuntimeException {

    private final long retryAfterSeconds;

    public LoginAttemptRateLimitedException(long retryAfterSeconds) {
        super("登录尝试次数过多，请稍后重试");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
