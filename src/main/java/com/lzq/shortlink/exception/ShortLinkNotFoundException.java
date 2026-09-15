package com.lzq.shortlink.exception;

/**
 * 短链接不存在或已过期。
 */
public class ShortLinkNotFoundException extends RuntimeException {

    public ShortLinkNotFoundException() {
        super("短链接不存在或已过期");
    }
}