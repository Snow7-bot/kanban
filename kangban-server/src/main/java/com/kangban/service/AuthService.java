package com.kangban.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kangban.common.Result;
import com.kangban.dto.request.LoginRequest;
import com.kangban.dto.request.RegisterRequest;
import com.kangban.dto.response.AuthResponse;
import com.kangban.entity.RefreshToken;
import com.kangban.entity.User;
import com.kangban.mapper.RefreshTokenMapper;
import com.kangban.mapper.UserMapper;
import com.kangban.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final RefreshTokenMapper refreshTokenMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final CaptchaService captchaService;
    private final MinioService minioService;

    /**
     * 用户注册
     */
    @Transactional
    public Result<AuthResponse> register(RegisterRequest req) {
        if (!captchaService.verify(req.getCaptchaId(), req.getCaptchaAnswer())) {
            return Result.error("人机验证错误或已过期");
        }

        String username = req.getUsername().trim();
        String phone = req.getPhone() == null || req.getPhone().isBlank()
                ? null
                : req.getPhone().trim();

        Long usernameCount = userMapper.selectCount(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, username)
                        .isNull(User::getDeletedAt)
        );
        if (usernameCount > 0) {
            return Result.error("该用户名已被使用");
        }

        if (phone != null && userMapper.selectCount(
                new LambdaQueryWrapper<User>()
                        .eq(User::getPhone, phone)
                        .isNull(User::getDeletedAt)
        ) > 0) {
            return Result.error("该手机号已注册");
        }

        // Create user
        User user = new User();
        user.setPhone(phone);
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setUsername(username);
        user.setStatus(1);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(user);

        // Generate tokens
        String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());
        saveRefreshToken(user.getId(), refreshToken);

        // Build response
        Map<String, Object> userMap = buildUserMap(user);
        AuthResponse authResponse = AuthResponse.builder()
                .token(token)
                .refreshToken(refreshToken)
                .user(userMap)
                .build();

        return Result.success("注册成功", authResponse);
    }

    /**
     * 用户登录
     */
    public Result<AuthResponse> login(LoginRequest req) {
        // Find user by phone or email
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .and(w -> w.eq(User::getUsername, req.getAccount())
                                .or()
                                .eq(User::getPhone, req.getAccount())
                                .or()
                                .eq(User::getEmail, req.getAccount()))
                        .eq(User::getStatus, 1)
                        .isNull(User::getDeletedAt)
        );

        if (user == null) {
            return Result.error("账号或密码错误");
        }

        // Verify password
        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            return Result.error("账号或密码错误");
        }

        // Generate tokens
        String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());
        saveRefreshToken(user.getId(), refreshToken);

        // Build response
        Map<String, Object> userMap = buildUserMap(user);
        AuthResponse authResponse = AuthResponse.builder()
                .token(token)
                .refreshToken(refreshToken)
                .user(userMap)
                .build();

        return Result.success(authResponse);
    }

    /**
     * 用户登出（撤销refresh token）
     */
    @Transactional
    public void logout(Long userId) {
        refreshTokenMapper.delete(
                new LambdaQueryWrapper<RefreshToken>()
                        .eq(RefreshToken::getUserId, userId)
                        .eq(RefreshToken::getRevoked, false)
        );
    }

    /**
     * 获取当前用户信息
     */
    public Result<Map<String, Object>> getCurrentUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null || user.getDeletedAt() != null) {
            return Result.error("用户不存在");
        }
        return Result.success(buildUserMap(user));
    }

    /**
     * 刷新访问令牌
     */
    public Result<Map<String, String>> refreshToken(String refreshTokenStr) {
        // Validate refresh token
        if (!jwtTokenProvider.validateToken(refreshTokenStr) || !jwtTokenProvider.isRefreshToken(refreshTokenStr)) {
            return Result.error("刷新令牌无效或已过期");
        }

        Long userId = jwtTokenProvider.getUserIdFromToken(refreshTokenStr);

        // Check if refresh token exists in DB and not revoked
        RefreshToken storedToken = refreshTokenMapper.selectOne(
                new LambdaQueryWrapper<RefreshToken>()
                        .eq(RefreshToken::getToken, refreshTokenStr)
                        .eq(RefreshToken::getRevoked, false)
        );

        if (storedToken == null) {
            return Result.error("刷新令牌已被撤销");
        }

        // Generate new access token
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }

        String newToken = jwtTokenProvider.generateToken(userId, user.getUsername());

        Map<String, String> result = new HashMap<>();
        result.put("token", newToken);
        return Result.success(result);
    }

    /**
     * 保存刷新令牌
     */
    private void saveRefreshToken(Long userId, String token) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUserId(userId);
        refreshToken.setToken(token);
        refreshToken.setRevoked(false);
        refreshToken.setExpiresAt(LocalDateTime.now().plusDays(30));
        refreshToken.setCreatedAt(LocalDateTime.now());
        refreshTokenMapper.insert(refreshToken);
    }

    /**
     * 构建用户信息Map（不含密码）
     */
    private Map<String, Object> buildUserMap(User user) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", user.getId());
        map.put("username", user.getUsername());
        map.put("phone", user.getPhone());
        map.put("email", user.getEmail());
        map.put("name", user.getName());
        map.put("gender", user.getGender());
        map.put("birthday", user.getBirthday());
        map.put("bloodType", user.getBloodType());
        map.put("height", user.getHeight());
        map.put("weight", user.getWeight());
        map.put("avatarUrl", minioService.resolveFileUrl(user.getAvatarUrl()));
        map.put("emergencyContact", user.getEmergencyContact());
        map.put("status", user.getStatus());
        return map;
    }
}
