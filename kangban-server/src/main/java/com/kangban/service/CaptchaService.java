package com.kangban.service;

import com.kangban.common.BusinessException;
import com.kangban.dto.response.CaptchaResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@Profile("!test")
@RequiredArgsConstructor
public class CaptchaService {

    private static final Duration CAPTCHA_TTL = Duration.ofMinutes(2);
    private static final Duration RATE_LIMIT_TTL = Duration.ofMinutes(1);
    private static final int MAX_ATTEMPTS = 5;
    private static final int MAX_ISSUES_PER_MINUTE = 10;
    private static final int IMAGE_WIDTH = 160;
    private static final int IMAGE_HEIGHT = 52;
    private static final char[] CHARACTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DefaultRedisScript<Long> VERIFY_SCRIPT = new DefaultRedisScript<>("""
            local expected = redis.call('GET', KEYS[1])
            if not expected then
                return 0
            end
            if expected == ARGV[1] then
                redis.call('DEL', KEYS[1], KEYS[2])
                return 1
            end
            local attempts = redis.call('INCR', KEYS[2])
            if attempts == 1 then
                local ttl = redis.call('TTL', KEYS[1])
                if ttl > 0 then
                    redis.call('EXPIRE', KEYS[2], ttl)
                end
            end
            if attempts >= tonumber(ARGV[2]) then
                redis.call('DEL', KEYS[1], KEYS[2])
            end
            return -1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    @Value("${app.captcha.secret}")
    private String secret;

    public CaptchaResponse issue(String clientAddress) {
        enforceRateLimit(clientAddress);

        String captchaId = UUID.randomUUID().toString();
        String answer = randomAnswer();
        redisTemplate.opsForValue().set(
                codeKey(captchaId),
                digest(captchaId, answer),
                CAPTCHA_TTL
        );

        return new CaptchaResponse(
                captchaId,
                "data:image/png;base64," + Base64.getEncoder().encodeToString(render(answer)),
                CAPTCHA_TTL.toSeconds()
        );
    }

    public boolean verify(String captchaId, String answer) {
        if (captchaId == null || captchaId.isBlank() || answer == null || answer.isBlank()) {
            return false;
        }
        Long result = redisTemplate.execute(
                VERIFY_SCRIPT,
                List.of(codeKey(captchaId), attemptKey(captchaId)),
                digest(captchaId, answer),
                String.valueOf(MAX_ATTEMPTS)
        );
        return Long.valueOf(1L).equals(result);
    }

    private void enforceRateLimit(String clientAddress) {
        String rateKey = rateKey(clientAddress);
        Long count = redisTemplate.opsForValue().increment(rateKey);
        if (count != null && count == 1) {
            redisTemplate.expire(rateKey, RATE_LIMIT_TTL);
        }
        if (count != null && count > MAX_ISSUES_PER_MINUTE) {
            throw BusinessException.tooManyRequests("人机验证请求过于频繁，请稍后再试");
        }
    }

    private String randomAnswer() {
        StringBuilder answer = new StringBuilder(5);
        for (int i = 0; i < 5; i++) {
            answer.append(CHARACTERS[RANDOM.nextInt(CHARACTERS.length)]);
        }
        return answer.toString();
    }

    private byte[] render(String answer) {
        BufferedImage image = new BufferedImage(IMAGE_WIDTH, IMAGE_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(new Color(248, 246, 250));
            graphics.fillRect(0, 0, IMAGE_WIDTH, IMAGE_HEIGHT);

            for (int i = 0; i < 7; i++) {
                graphics.setColor(new Color(
                        120 + RANDOM.nextInt(100),
                        120 + RANDOM.nextInt(100),
                        120 + RANDOM.nextInt(100)
                ));
                graphics.drawLine(
                        RANDOM.nextInt(IMAGE_WIDTH),
                        RANDOM.nextInt(IMAGE_HEIGHT),
                        RANDOM.nextInt(IMAGE_WIDTH),
                        RANDOM.nextInt(IMAGE_HEIGHT)
                );
            }

            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 29));
            for (int i = 0; i < answer.length(); i++) {
                AffineTransform original = graphics.getTransform();
                int x = 14 + i * 28;
                int y = 36 + RANDOM.nextInt(7) - 3;
                graphics.rotate(Math.toRadians(RANDOM.nextInt(25) - 12), x + 10, y - 10);
                graphics.setColor(new Color(
                        35 + RANDOM.nextInt(95),
                        35 + RANDOM.nextInt(80),
                        55 + RANDOM.nextInt(95)
                ));
                graphics.drawString(String.valueOf(answer.charAt(i)), x, y);
                graphics.setTransform(original);
            }

            try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                ImageIO.write(image, "png", output);
                return output.toByteArray();
            }
        } catch (Exception e) {
            throw new IllegalStateException("人机验证图片生成失败", e);
        } finally {
            graphics.dispose();
        }
    }

    private String digest(String captchaId, String answer) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] value = (captchaId + ":" + answer.trim().toUpperCase(Locale.ROOT))
                    .getBytes(StandardCharsets.UTF_8);
            return Base64.getEncoder().encodeToString(mac.doFinal(value));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("人机验证摘要生成失败", e);
        }
    }

    private String codeKey(String captchaId) {
        return "kangban:captcha:code:" + captchaId;
    }

    private String attemptKey(String captchaId) {
        return "kangban:captcha:attempts:" + captchaId;
    }

    private String rateKey(String clientAddress) {
        String address = clientAddress == null || clientAddress.isBlank() ? "unknown" : clientAddress;
        return "kangban:captcha:rate:" + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(address.getBytes(StandardCharsets.UTF_8));
    }
}
