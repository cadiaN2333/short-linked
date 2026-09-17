package com.lzq.shortlink.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 短链接公共访问地址配置。 */
@ConfigurationProperties(prefix = "app.short-link")
public class ShortLinkProperties {

    /** 对外暴露的短链接基地址，不包含末尾斜杠。 */
    private String publicBaseUrl = "http://localhost:8080";

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }
}
