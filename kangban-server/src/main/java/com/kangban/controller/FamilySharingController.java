package com.kangban.controller;

import com.kangban.common.Result;
import com.kangban.dto.request.CreateFamilyInvitationRequest;
import com.kangban.dto.request.FamilyPermissionRequest;
import com.kangban.service.FamilySharingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/family/sharing")
@RequiredArgsConstructor
public class FamilySharingController {

    private final FamilySharingService familySharingService;

    @GetMapping
    public Result<Map<String, Object>> overview(@AuthenticationPrincipal UserDetails user) {
        return Result.success(familySharingService.overview(userId(user)));
    }

    @PostMapping("/invitations")
    public Result<Map<String, Object>> invite(@AuthenticationPrincipal UserDetails user,
                                              @Valid @RequestBody CreateFamilyInvitationRequest request) {
        return Result.success("邀请已发送", familySharingService.invite(userId(user), request));
    }

    @PostMapping("/invitations/{id}/accept")
    public Result<Void> accept(@AuthenticationPrincipal UserDetails user, @PathVariable Long id) {
        familySharingService.accept(userId(user), id);
        return Result.success("已加入家庭并启用共享", null);
    }

    @PostMapping("/invitations/{id}/reject")
    public Result<Void> reject(@AuthenticationPrincipal UserDetails user, @PathVariable Long id) {
        familySharingService.reject(userId(user), id);
        return Result.success("已拒绝邀请", null);
    }

    @PutMapping("/permissions/{granteeUserId}")
    public Result<Void> updatePermission(@AuthenticationPrincipal UserDetails user,
                                         @PathVariable Long granteeUserId,
                                         @RequestBody FamilyPermissionRequest request) {
        familySharingService.updateGrantedPermission(userId(user), granteeUserId, request);
        return Result.success("权限已更新", null);
    }

    @DeleteMapping("/permissions/{granteeUserId}")
    public Result<Void> revoke(@AuthenticationPrincipal UserDetails user,
                               @PathVariable Long granteeUserId) {
        familySharingService.revoke(userId(user), granteeUserId);
        return Result.success("授权已撤销", null);
    }

    private Long userId(UserDetails user) {
        return Long.parseLong(user.getUsername());
    }
}
