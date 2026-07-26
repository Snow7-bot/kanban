package com.kangban.controller;

import com.kangban.common.Result;
import com.kangban.dto.request.LoginRequest;
import com.kangban.dto.request.RegisterRequest;
import com.kangban.dto.request.ResetPasswordRequest;
import com.kangban.dto.response.AuthResponse;
import com.kangban.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "认证管理")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "发送验证码")
    @PostMapping("/code")
    public Result<Void> sendCode(@RequestBody Map<String, String> body) {
        authService.sendVerifyCode(body.get("phone"));
        return Result.success("验证码已发送", null);
    }

    @Operation(summary = "用户注册")
    @PostMapping("/register")
    public Result<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        return authService.register(req);
    }

    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public Result<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        return authService.login(req);
    }

    @Operation(summary = "退出登录")
    @DeleteMapping("/logout")
    public Result<Void> logout(@AuthenticationPrincipal UserDetails user) {
        if (user == null) {
            return Result.unauthorized("未登录");
        }
        authService.logout(Long.parseLong(user.getUsername()));
        return Result.success();
    }

    @Operation(summary = "获取当前用户")
    @GetMapping("/me")
    public Result<Map<String, Object>> me(@AuthenticationPrincipal UserDetails user) {
        if (user == null) {
            return Result.unauthorized("未登录");
        }
        return authService.getCurrentUser(Long.parseLong(user.getUsername()));
    }

    @Operation(summary = "刷新Token")
    @PostMapping("/refresh")
    public Result<Map<String, String>> refresh(@RequestBody Map<String, String> body) {
        return authService.refreshToken(body.get("refreshToken"));
    }

    @Operation(summary = "忘记密码")
    @PostMapping("/forgot")
    public Result<Void> forgot(@RequestBody Map<String, String> body) {
        authService.forgotPassword(body.get("phone"));
        return Result.success("重置验证码已发送", null);
    }

    @Operation(summary = "重置密码")
    @PostMapping("/reset")
    public Result<Void> reset(@Valid @RequestBody ResetPasswordRequest req) {
        authService.resetPassword(req);
        return Result.success("密码已重置", null);
    }
}
