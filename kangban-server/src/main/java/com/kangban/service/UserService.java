package com.kangban.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kangban.common.BusinessException;
import com.kangban.common.Result;
import com.kangban.dto.request.UpdateProfileRequest;
import com.kangban.entity.User;
import com.kangban.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final MinioService minioService;

    public Result<Map<String, Object>> getProfile(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw BusinessException.notFound("用户不存在");
        }
        Map<String, Object> profile = new HashMap<>();
        profile.put("id", user.getId());
        profile.put("username", user.getUsername());
        profile.put("phone", user.getPhone());
        profile.put("email", user.getEmail());
        profile.put("name", user.getName());
        profile.put("gender", user.getGender());
        profile.put("birthday", user.getBirthday());
        profile.put("bloodType", user.getBloodType());
        profile.put("height", user.getHeight());
        profile.put("weight", user.getWeight());
        profile.put("avatarUrl", minioService.resolveFileUrl(user.getAvatarUrl()));
        profile.put("emergencyContact", user.getEmergencyContact());
        profile.put("status", user.getStatus());
        profile.put("createdAt", user.getCreatedAt());
        return Result.success(profile);
    }

    public void updateProfile(Long userId, UpdateProfileRequest request) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw BusinessException.notFound("用户不存在");
        }
        if (request.getName() != null) {
            user.setName(request.getName());
        }
        if (request.getGender() != null) {
            user.setGender(request.getGender());
        }
        if (request.getBirthday() != null) {
            user.setBirthday(request.getBirthday());
        }
        if (request.getBloodType() != null) {
            user.setBloodType(request.getBloodType());
        }
        if (request.getHeight() != null) {
            user.setHeight(request.getHeight());
        }
        if (request.getWeight() != null) {
            user.setWeight(request.getWeight());
        }
        if (request.getEmergencyContact() != null) {
            user.setEmergencyContact(request.getEmergencyContact());
        }
        if (request.getEmail() != null) {
            user.setEmail(request.getEmail());
        }
        if (request.getUsername() != null) {
            user.setUsername(request.getUsername());
        }
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
    }

    public Map<String, Object> uploadAvatar(Long userId, MultipartFile file) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw BusinessException.notFound("用户不存在");
        }
        String avatarObject = minioService.uploadObject(file, userId);
        user.setAvatarUrl(avatarObject);
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);

        Map<String, Object> result = new HashMap<>();
        result.put("url", minioService.getFileUrl(avatarObject));
        return result;
    }
}
