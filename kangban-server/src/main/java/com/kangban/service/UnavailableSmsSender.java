package com.kangban.service;

import com.kangban.common.BusinessException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("prod")
public class UnavailableSmsSender implements SmsSender {

    @Override
    public void sendVerificationCode(String phone, String code) {
        throw new BusinessException("短信服务尚未配置，请联系管理员");
    }
}
