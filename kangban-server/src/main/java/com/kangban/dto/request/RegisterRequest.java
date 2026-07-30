package com.kangban.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "注册请求")
public class RegisterRequest {

    @NotBlank(message = "用户名不能为空")
    @Pattern(
            regexp = "^[A-Za-z\\u4e00-\\u9fa5][A-Za-z0-9_\\u4e00-\\u9fa5]{3,19}$",
            message = "用户名需为4-20位，以字母或中文开头"
    )
    @Schema(description = "登录用户名")
    private String username;

    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    @Schema(description = "可选手机号，当前不作为已验证身份")
    private String phone;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 20, message = "密码长度需为6-20位")
    @Schema(description = "密码")
    private String password;

    @NotBlank(message = "人机验证标识不能为空")
    @Schema(description = "人机验证标识")
    private String captchaId;

    @NotBlank(message = "请输入人机验证码")
    @Size(min = 5, max = 5, message = "人机验证码应为5位")
    @Schema(description = "人机验证码答案")
    private String captchaAnswer;
}
