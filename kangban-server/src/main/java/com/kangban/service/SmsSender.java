package com.kangban.service;

public interface SmsSender {
    void sendVerificationCode(String phone, String code);
}
