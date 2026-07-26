package com.kangban.service;

import com.kangban.common.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;

@Service
@Profile("!test")
@RequiredArgsConstructor
public class VerificationCodeService {

    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final Duration SEND_COOLDOWN = Duration.ofSeconds(60);
    private static final int MAX_ATTEMPTS = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redisTemplate;

    public String issue(String purpose, String phone) {
        String cooldownKey = cooldownKey(purpose, phone);
        Boolean sent = redisTemplate.opsForValue().setIfAbsent(cooldownKey, "1", SEND_COOLDOWN);
        if (!Boolean.TRUE.equals(sent)) {
            throw BusinessException.tooManyRequests("验证码已发送，请稍后再试");
        }

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        redisTemplate.opsForValue().set(codeKey(purpose, phone), code, CODE_TTL);
        redisTemplate.delete(attemptKey(purpose, phone));
        return code;
    }

    public boolean verify(String purpose, String phone, String code) {
        String codeKey = codeKey(purpose, phone);
        String storedCode = redisTemplate.opsForValue().get(codeKey);
        if (storedCode == null || storedCode.isBlank()) {
            return false;
        }

        if (storedCode.equals(code)) {
            redisTemplate.delete(codeKey);
            redisTemplate.delete(attemptKey(purpose, phone));
            return true;
        }

        String attemptsKey = attemptKey(purpose, phone);
        Long attempts = redisTemplate.opsForValue().increment(attemptsKey);
        if (attempts != null && attempts == 1) {
            redisTemplate.expire(attemptsKey, CODE_TTL);
        }
        if (attempts != null && attempts >= MAX_ATTEMPTS) {
            redisTemplate.delete(codeKey);
            redisTemplate.delete(attemptsKey);
        }
        return false;
    }

    private String codeKey(String purpose, String phone) {
        return "kangban:verification:" + purpose + ":" + phone;
    }

    private String cooldownKey(String purpose, String phone) {
        return "kangban:verification:cooldown:" + purpose + ":" + phone;
    }

    private String attemptKey(String purpose, String phone) {
        return "kangban:verification:attempts:" + purpose + ":" + phone;
    }
}
