package com.kangban.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "登录请求")
public class LoginRequest {

    @NotBlank(message = "账号不能为空")
    @Schema(description = "用户名、手机号或邮箱")
    private String account;

    @NotBlank(message = "密码不能为空")
    @Size(max = 20, message = "密码长度不能超过20位")
    @Schema(description = "密码")
    private String password;
}
