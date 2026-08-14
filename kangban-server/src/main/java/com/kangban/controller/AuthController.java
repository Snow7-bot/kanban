package com.kangban.controller;

import com.kangban.common.Result;
import com.kangban.dto.request.LoginRequest;
import com.kangban.dto.request.RegisterRequest;
import com.kangban.dto.response.AuthResponse;
import com.kangban.dto.response.CaptchaResponse;
import com.kangban.service.AuthService;
import com.kangban.service.CaptchaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

@Tag(name = "认证管理")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CaptchaService captchaService;

    @Operation(summary = "获取本地图片人机验证")
    @GetMapping("/captcha")
    public Result<CaptchaResponse> captcha(HttpServletRequest request) {
        return Result.success(captchaService.issue(request.getRemoteAddr()));
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

}
