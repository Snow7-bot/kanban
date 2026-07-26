package com.kangban.controller;

import com.kangban.common.Result;
import com.kangban.dto.request.AddFamilyRequest;
import com.kangban.dto.request.UpdateFamilyRequest;
import com.kangban.service.FamilyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Tag(name = "家庭成员管理")
@RestController
@RequestMapping("/family")
@RequiredArgsConstructor
public class FamilyController {

    private final FamilyService familyService;

    @Operation(summary = "获取家庭成员列表")
    @GetMapping
    public Result<List<Map<String, Object>>> list(@AuthenticationPrincipal UserDetails user) {
        Long userId = Long.parseLong(user.getUsername());
        return familyService.list(userId);
    }

    @Operation(summary = "获取家庭成员详情")
    @GetMapping("/{id}")
    public Result<Map<String, Object>> getById(@AuthenticationPrincipal UserDetails user,
                                               @PathVariable Long id) {
        Long userId = Long.parseLong(user.getUsername());
        return familyService.getById(userId, id);
    }

    @Operation(summary = "添加家庭成员")
    @PostMapping
    public Result<Map<String, Object>> add(@AuthenticationPrincipal UserDetails user,
                                           @Valid @RequestBody AddFamilyRequest req) {
        Long userId = Long.parseLong(user.getUsername());
        return familyService.add(userId, req);
    }

    @Operation(summary = "更新家庭成员")
    @PutMapping("/{id}")
    public Result<Void> update(@AuthenticationPrincipal UserDetails user,
                               @PathVariable Long id,
                               @Valid @RequestBody UpdateFamilyRequest req) {
        Long userId = Long.parseLong(user.getUsername());
        familyService.update(userId, id, req);
        return Result.success("更新成功", null);
    }

    @Operation(summary = "上传家庭成员头像")
    @PostMapping("/{id}/avatar")
    public Result<Map<String, Object>> uploadAvatar(@AuthenticationPrincipal UserDetails user,
                                                    @PathVariable Long id,
                                                    @RequestParam("file") MultipartFile file) {
        Long userId = Long.parseLong(user.getUsername());
        return Result.success(familyService.uploadAvatar(userId, id, file));
    }

    @Operation(summary = "删除家庭成员")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@AuthenticationPrincipal UserDetails user,
                               @PathVariable Long id) {
        Long userId = Long.parseLong(user.getUsername());
        familyService.delete(userId, id);
        return Result.success("删除成功", null);
    }
}
