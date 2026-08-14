package com.kangban.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "本地图片人机验证")
public record CaptchaResponse(
        String captchaId,
        String imageData,
        long expiresInSeconds
) {
}
