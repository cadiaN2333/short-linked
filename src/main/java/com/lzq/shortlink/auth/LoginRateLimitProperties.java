package com.lzq.shortlink.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** 登录失败限流参数。 */
@Component
@ConfigurationProperties(prefix = "app.security.login-rate-limit")
public class LoginRateLimitProperties {

    /** 单账号失败次数上限。 */
    private int accountFailureLimit = 5;

    /** 单来源地址失败次数上限。 */
    private int sourceFailureLimit = 20;

    /** 失败计数窗口。 */
    private Duration failureWindow = Duration.ofMinutes(15);

    public int getAccountFailureLimit() {
        return accountFailureLimit;
    }

    public void setAccountFailureLimit(int accountFailureLimit) {
        this.accountFailureLimit = accountFailureLimit;
    }

    public int getSourceFailureLimit() {
        return sourceFailureLimit;
    }

    public void setSourceFailureLimit(int sourceFailureLimit) {
        this.sourceFailureLimit = sourceFailureLimit;
    }

    public Duration getFailureWindow() {
        return failureWindow;
    }

    public void setFailureWindow(Duration failureWindow) {
        this.failureWindow = failureWindow;
    }
}
