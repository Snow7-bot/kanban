package com.kangban.controller;

import com.kangban.common.Result;
import com.kangban.dto.request.UpdateProfileRequest;
import com.kangban.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Tag(name = "用户管理")
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "获取个人信息")
    @GetMapping("/profile")
    public Result<Map<String, Object>> getProfile(@AuthenticationPrincipal UserDetails user) {
        Long userId = Long.parseLong(user.getUsername());
        return userService.getProfile(userId);
    }

    @Operation(summary = "更新个人信息")
    @PutMapping("/profile")
    public Result<Map<String, Object>> updateProfile(@AuthenticationPrincipal UserDetails user,
                                                     @Valid @RequestBody UpdateProfileRequest req) {
        Long userId = Long.parseLong(user.getUsername());
        userService.updateProfile(userId, req);
        return userService.getProfile(userId);
    }

    @Operation(summary = "上传头像")
    @PostMapping("/avatar")
    public Result<Map<String, Object>> uploadAvatar(@AuthenticationPrincipal UserDetails user,
                                                    @RequestParam("file") MultipartFile file) {
        Long userId = Long.parseLong(user.getUsername());
        return Result.success(userService.uploadAvatar(userId, file));
    }
}
