package com.kangban.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@Profile("dev")
public class DevSmsSender implements SmsSender {

    @Override
    public void sendVerificationCode(String phone, String code) {
        log.info("本地开发模拟短信已投递至 {}", maskPhone(phone));
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return "***";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
