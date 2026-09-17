package com.lzq.shortlink.auth.exception;

public class EmailAlreadyRegisteredException
        extends RuntimeException {

    public EmailAlreadyRegisteredException() {
        super("邮箱已注册");
    }
}