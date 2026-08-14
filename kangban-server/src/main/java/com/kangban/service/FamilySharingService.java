package com.kangban.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kangban.common.BusinessException;
import com.kangban.dto.request.CreateFamilyInvitationRequest;
import com.kangban.dto.request.FamilyPermissionRequest;
import com.kangban.entity.*;
import com.kangban.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FamilySharingService {

    private final FamilyGroupMapper groupMapper;
    private final FamilyGroupMemberMapper groupMemberMapper;
    private final FamilyInvitationMapper invitationMapper;
    private final FamilyPermissionMapper permissionMapper;
    private final UserMapper userMapper;
    private final MinioService minioService;
    private final AuditService auditService;

    public Map<String, Object> overview(Long userId) {
        List<Map<String, Object>> subjects = permissionMapper.selectList(
                        new LambdaQueryWrapper<FamilyPermission>()
                                .eq(FamilyPermission::getGranteeUserId, userId)
                                .eq(FamilyPermission::getStatus, "active")
                                .isNull(FamilyPermission::getRevokedAt))
                .stream().map(permission -> toSharedUser(permission.getSubjectUserId(), permission, true))
                .toList();

        List<Map<String, Object>> granted = permissionMapper.selectList(
                        new LambdaQueryWrapper<FamilyPermission>()
                                .eq(FamilyPermission::getSubjectUserId, userId)
                                .eq(FamilyPermission::getStatus, "active")
                                .isNull(FamilyPermission::getRevokedAt))
                .stream().map(permission -> toSharedUser(permission.getGranteeUserId(), permission, false))
                .toList();

        List<Map<String, Object>> incoming = invitationMapper.selectList(
                        new LambdaQueryWrapper<FamilyInvitation>()
                                .eq(FamilyInvitation::getInviteeUserId, userId)
                                .eq(FamilyInvitation::getStatus, "pending")
                                .orderByDesc(FamilyInvitation::getCreatedAt))
                .stream().map(invitation -> toInvitation(invitation, true)).toList();

        List<Map<String, Object>> sent = invitationMapper.selectList(
                        new LambdaQueryWrapper<FamilyInvitation>()
                                .eq(FamilyInvitation::getInviterUserId, userId)
                                .eq(FamilyInvitation::getStatus, "pending")
                                .orderByDesc(FamilyInvitation::getCreatedAt))
                .stream().map(invitation -> toInvitation(invitation, false)).toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sharedSubjects", subjects);
        result.put("grantedAccess", granted);
        result.put("incomingInvitations", incoming);
        result.put("sentInvitations", sent);
        return result;
    }

    @Transactional
    public Map<String, Object> invite(Long inviterUserId, CreateFamilyInvitationRequest request) {
        User invitee = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getUsername())
                .eq(User::getStatus, 1)
                .isNull(User::getDeletedAt)
                .last("LIMIT 1"));
        if (invitee == null) {
            throw BusinessException.notFound("该用户名尚未注册康伴账号");
        }
        if (inviterUserId.equals(invitee.getId())) {
            throw BusinessException.paramsError("不能邀请自己");
        }

        FamilyGroup group = getOrCreateOwnedGroup(inviterUserId);
        boolean alreadyMember = groupMemberMapper.selectCount(new LambdaQueryWrapper<FamilyGroupMember>()
                .eq(FamilyGroupMember::getFamilyId, group.getId())
                .eq(FamilyGroupMember::getUserId, invitee.getId())
                .eq(FamilyGroupMember::getStatus, "active")) > 0;
        if (alreadyMember) {
            throw BusinessException.conflict("该用户已在家庭中");
        }
        boolean pending = invitationMapper.selectCount(new LambdaQueryWrapper<FamilyInvitation>()
                .eq(FamilyInvitation::getFamilyId, group.getId())
                .eq(FamilyInvitation::getInviteeUserId, invitee.getId())
                .eq(FamilyInvitation::getStatus, "pending")) > 0;
        if (pending) {
            throw BusinessException.conflict("邀请已发送，请等待对方处理");
        }

        FamilyInvitation invitation = new FamilyInvitation();
        invitation.setFamilyId(group.getId());
        invitation.setInviterUserId(inviterUserId);
        invitation.setInviteeUserId(invitee.getId());
        invitation.setRelation(request.getRelation());
        copyPermissions(request.getPermissions(), invitation);
        invitation.setStatus("pending");
        invitation.setExpiresAt(LocalDateTime.now().plusDays(7));
        invitation.setCreatedAt(LocalDateTime.now());
        invitation.setUpdatedAt(LocalDateTime.now());
        invitationMapper.insert(invitation);
        auditService.record(inviterUserId, "FAMILY_INVITE", "family_invitation",
                invitation.getId(), "邀请已发送");
        return toInvitation(invitation, false);
    }

    @Transactional
    public void accept(Long inviteeUserId, Long invitationId) {
        FamilyInvitation invitation = requirePendingInvitation(inviteeUserId, invitationId);
        if (invitation.getExpiresAt().isBefore(LocalDateTime.now())) {
            invitation.setStatus("expired");
            invitation.setRespondedAt(LocalDateTime.now());
            invitationMapper.updateById(invitation);
            throw BusinessException.conflict("邀请已过期");
        }

        FamilyGroupMember member = groupMemberMapper.selectOne(
                new LambdaQueryWrapper<FamilyGroupMember>()
                        .eq(FamilyGroupMember::getFamilyId, invitation.getFamilyId())
                        .eq(FamilyGroupMember::getUserId, inviteeUserId)
                        .last("LIMIT 1"));
        if (member == null) {
            member = new FamilyGroupMember();
            member.setFamilyId(invitation.getFamilyId());
            member.setUserId(inviteeUserId);
            member.setRelation(invitation.getRelation());
            member.setRole("member");
            member.setStatus("active");
            member.setJoinedAt(LocalDateTime.now());
            member.setCreatedAt(LocalDateTime.now());
            member.setUpdatedAt(LocalDateTime.now());
            groupMemberMapper.insert(member);
        } else {
            member.setStatus("active");
            member.setRelation(invitation.getRelation());
            member.setUpdatedAt(LocalDateTime.now());
            groupMemberMapper.updateById(member);
        }

        FamilyPermission permission = permissionMapper.selectOne(
                new LambdaQueryWrapper<FamilyPermission>()
                        .eq(FamilyPermission::getFamilyId, invitation.getFamilyId())
                        .eq(FamilyPermission::getSubjectUserId, inviteeUserId)
                        .eq(FamilyPermission::getGranteeUserId, invitation.getInviterUserId())
                        .last("LIMIT 1"));
        if (permission == null) {
            permission = new FamilyPermission();
            permission.setFamilyId(invitation.getFamilyId());
            permission.setSubjectUserId(inviteeUserId);
            permission.setGranteeUserId(invitation.getInviterUserId());
            permission.setCreatedAt(LocalDateTime.now());
        }
        copyPermissions(invitation, permission);
        permission.setStatus("active");
        permission.setRevokedAt(null);
        permission.setUpdatedAt(LocalDateTime.now());
        if (permission.getId() == null) {
            permissionMapper.insert(permission);
        } else {
            permissionMapper.updateById(permission);
        }

        invitation.setStatus("accepted");
        invitation.setRespondedAt(LocalDateTime.now());
        invitation.setUpdatedAt(LocalDateTime.now());
        invitationMapper.updateById(invitation);
        auditService.record(inviteeUserId, "FAMILY_INVITE_ACCEPT", "family_invitation",
                invitationId, "已同意共享所选健康数据");
    }

    @Transactional
    public void reject(Long inviteeUserId, Long invitationId) {
        FamilyInvitation invitation = requirePendingInvitation(inviteeUserId, invitationId);
        invitation.setStatus("rejected");
        invitation.setRespondedAt(LocalDateTime.now());
        invitation.setUpdatedAt(LocalDateTime.now());
        invitationMapper.updateById(invitation);
        auditService.record(inviteeUserId, "FAMILY_INVITE_REJECT", "family_invitation",
                invitationId, "已拒绝邀请");
    }

    @Transactional
    public void updateGrantedPermission(Long subjectUserId, Long granteeUserId,
                                        FamilyPermissionRequest request) {
        FamilyPermission permission = permissionMapper.selectOne(
                new LambdaQueryWrapper<FamilyPermission>()
                        .eq(FamilyPermission::getSubjectUserId, subjectUserId)
                        .eq(FamilyPermission::getGranteeUserId, granteeUserId)
                        .eq(FamilyPermission::getStatus, "active")
                        .isNull(FamilyPermission::getRevokedAt)
                        .last("LIMIT 1"));
        if (permission == null) {
            throw BusinessException.notFound("未找到可管理的家庭授权");
        }
        copyPermissions(request, permission);
        permission.setUpdatedAt(LocalDateTime.now());
        permissionMapper.updateById(permission);
        auditService.record(subjectUserId, "FAMILY_PERMISSION_UPDATE", "family_permission",
                permission.getId(), "共享权限已更新");
    }

    @Transactional
    public void revoke(Long subjectUserId, Long granteeUserId) {
        FamilyPermission permission = permissionMapper.selectOne(
                new LambdaQueryWrapper<FamilyPermission>()
                        .eq(FamilyPermission::getSubjectUserId, subjectUserId)
                        .eq(FamilyPermission::getGranteeUserId, granteeUserId)
                        .eq(FamilyPermission::getStatus, "active")
                        .isNull(FamilyPermission::getRevokedAt)
                        .last("LIMIT 1"));
        if (permission == null) {
            throw BusinessException.notFound("未找到可撤销的家庭授权");
        }
        permission.setStatus("revoked");
        permission.setRevokedAt(LocalDateTime.now());
        permission.setUpdatedAt(LocalDateTime.now());
        permissionMapper.updateById(permission);
        auditService.record(subjectUserId, "FAMILY_PERMISSION_REVOKE", "family_permission",
                permission.getId(), "共享权限已撤销");
    }

    private FamilyGroup getOrCreateOwnedGroup(Long ownerUserId) {
        FamilyGroup group = groupMapper.selectOne(new LambdaQueryWrapper<FamilyGroup>()
                .eq(FamilyGroup::getOwnerUserId, ownerUserId)
                .isNull(FamilyGroup::getDeletedAt)
                .last("LIMIT 1"));
        if (group != null) {
            return group;
        }
        group = new FamilyGroup();
        group.setName("我的家庭");
        group.setOwnerUserId(ownerUserId);
        group.setCreatedAt(LocalDateTime.now());
        group.setUpdatedAt(LocalDateTime.now());
        groupMapper.insert(group);

        FamilyGroupMember owner = new FamilyGroupMember();
        owner.setFamilyId(group.getId());
        owner.setUserId(ownerUserId);
        owner.setRelation("本人");
        owner.setRole("owner");
        owner.setStatus("active");
        owner.setJoinedAt(LocalDateTime.now());
        owner.setCreatedAt(LocalDateTime.now());
        owner.setUpdatedAt(LocalDateTime.now());
        groupMemberMapper.insert(owner);
        return group;
    }

    private FamilyInvitation requirePendingInvitation(Long inviteeUserId, Long invitationId) {
        FamilyInvitation invitation = invitationMapper.selectOne(
                new LambdaQueryWrapper<FamilyInvitation>()
                        .eq(FamilyInvitation::getId, invitationId)
                        .eq(FamilyInvitation::getInviteeUserId, inviteeUserId)
                        .eq(FamilyInvitation::getStatus, "pending"));
        if (invitation == null) {
            throw BusinessException.notFound("待处理邀请不存在");
        }
        return invitation;
    }

    private Map<String, Object> toSharedUser(Long userId, FamilyPermission permission,
                                             boolean subject) {
        User user = userMapper.selectById(userId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", userId);
        result.put("name", displayName(user));
        result.put("avatarUrl", user == null ? null : minioService.resolveFileUrl(user.getAvatarUrl()));
        result.put("relation", sharedRelation(permission.getFamilyId(), userId));
        result.put("permissions", permissionMap(permission));
        result.put("direction", subject ? "canView" : "canViewMe");
        return result;
    }

    private Map<String, Object> toInvitation(FamilyInvitation invitation, boolean incoming) {
        User other = userMapper.selectById(
                incoming ? invitation.getInviterUserId() : invitation.getInviteeUserId());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", invitation.getId());
        result.put("name", displayName(other));
        result.put("relation", invitation.getRelation());
        result.put("permissions", permissionMap(invitation));
        result.put("expiresAt", invitation.getExpiresAt());
        result.put("createdAt", invitation.getCreatedAt());
        return result;
    }

    private String relation(Long familyId, Long userId) {
        FamilyGroupMember member = groupMemberMapper.selectOne(
                new LambdaQueryWrapper<FamilyGroupMember>()
                        .eq(FamilyGroupMember::getFamilyId, familyId)
                        .eq(FamilyGroupMember::getUserId, userId)
                        .eq(FamilyGroupMember::getStatus, "active")
                        .last("LIMIT 1"));
        return member == null ? "家庭成员" : member.getRelation();
    }

    private String sharedRelation(Long familyId, Long userId) {
        String value = relation(familyId, userId);
        return value == null || value.isBlank() || "本人".equals(value) ? "家庭成员" : value;
    }

    private String displayName(User user) {
        if (user == null) {
            return "家庭成员";
        }
        if (user.getName() != null && !user.getName().isBlank()) {
            return user.getName();
        }
        return user.getUsername() == null ? "家庭成员" : user.getUsername();
    }

    private Map<String, Boolean> permissionMap(FamilyPermission permission) {
        Map<String, Boolean> map = new LinkedHashMap<>();
        map.put("canViewHealth", Boolean.TRUE.equals(permission.getCanViewHealth()));
        map.put("canAddHealth", Boolean.TRUE.equals(permission.getCanAddHealth()));
        map.put("canViewRecords", Boolean.TRUE.equals(permission.getCanViewRecords()));
        map.put("canViewMedications", Boolean.TRUE.equals(permission.getCanViewMedications()));
        map.put("canViewReports", Boolean.TRUE.equals(permission.getCanViewReports()));
        map.put("canUseAi", Boolean.TRUE.equals(permission.getCanUseAi()));
        map.put("canModify", Boolean.TRUE.equals(permission.getCanModify()));
        map.put("canDelete", Boolean.TRUE.equals(permission.getCanDelete()));
        return map;
    }

    private Map<String, Boolean> permissionMap(FamilyInvitation invitation) {
        Map<String, Boolean> map = new LinkedHashMap<>();
        map.put("canViewHealth", Boolean.TRUE.equals(invitation.getCanViewHealth()));
        map.put("canAddHealth", Boolean.TRUE.equals(invitation.getCanAddHealth()));
        map.put("canViewRecords", Boolean.TRUE.equals(invitation.getCanViewRecords()));
        map.put("canViewMedications", Boolean.TRUE.equals(invitation.getCanViewMedications()));
        map.put("canViewReports", Boolean.TRUE.equals(invitation.getCanViewReports()));
        map.put("canUseAi", Boolean.TRUE.equals(invitation.getCanUseAi()));
        map.put("canModify", Boolean.TRUE.equals(invitation.getCanModify()));
        map.put("canDelete", Boolean.TRUE.equals(invitation.getCanDelete()));
        return map;
    }

    private void copyPermissions(FamilyPermissionRequest source, FamilyInvitation target) {
        target.setCanViewHealth(source.isCanViewHealth());
        target.setCanAddHealth(source.isCanAddHealth());
        target.setCanViewRecords(source.isCanViewRecords());
        target.setCanViewMedications(source.isCanViewMedications());
        target.setCanViewReports(source.isCanViewReports());
        target.setCanUseAi(source.isCanUseAi());
        target.setCanModify(source.isCanModify());
        target.setCanDelete(source.isCanDelete());
    }

    private void copyPermissions(FamilyPermissionRequest source, FamilyPermission target) {
        target.setCanViewHealth(source.isCanViewHealth());
        target.setCanAddHealth(source.isCanAddHealth());
        target.setCanViewRecords(source.isCanViewRecords());
        target.setCanViewMedications(source.isCanViewMedications());
        target.setCanViewReports(source.isCanViewReports());
        target.setCanUseAi(source.isCanUseAi());
        target.setCanModify(source.isCanModify());
        target.setCanDelete(source.isCanDelete());
    }

    private void copyPermissions(FamilyInvitation source, FamilyPermission target) {
        target.setCanViewHealth(source.getCanViewHealth());
        target.setCanAddHealth(source.getCanAddHealth());
        target.setCanViewRecords(source.getCanViewRecords());
        target.setCanViewMedications(source.getCanViewMedications());
        target.setCanViewReports(source.getCanViewReports());
        target.setCanUseAi(source.getCanUseAi());
        target.setCanModify(source.getCanModify());
        target.setCanDelete(source.getCanDelete());
    }
}
