package com.kangban.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kangban.common.BusinessException;
import com.kangban.entity.FamilyGroupMember;
import com.kangban.entity.FamilyPermission;
import com.kangban.mapper.FamilyGroupMemberMapper;
import com.kangban.mapper.FamilyPermissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class FamilyAccessService {

    public enum Scope {
        VIEW_HEALTH(FamilyPermission::getCanViewHealth),
        ADD_HEALTH(FamilyPermission::getCanAddHealth),
        VIEW_RECORDS(FamilyPermission::getCanViewRecords),
        VIEW_MEDICATIONS(FamilyPermission::getCanViewMedications),
        VIEW_REPORTS(FamilyPermission::getCanViewReports),
        USE_AI(FamilyPermission::getCanUseAi),
        MODIFY(FamilyPermission::getCanModify),
        DELETE(FamilyPermission::getCanDelete);

        private final Function<FamilyPermission, Boolean> checker;

        Scope(Function<FamilyPermission, Boolean> checker) {
            this.checker = checker;
        }

        boolean granted(FamilyPermission permission) {
            return Boolean.TRUE.equals(checker.apply(permission));
        }
    }

    private final FamilyPermissionMapper permissionMapper;
    private final FamilyGroupMemberMapper memberMapper;

    public Long require(Long actorUserId, Long requestedSubjectUserId, Scope scope) {
        Long subjectUserId = requestedSubjectUserId == null ? actorUserId : requestedSubjectUserId;
        if (actorUserId.equals(subjectUserId)) {
            return subjectUserId;
        }

        FamilyPermission permission = permissionMapper.selectOne(
                new LambdaQueryWrapper<FamilyPermission>()
                        .eq(FamilyPermission::getSubjectUserId, subjectUserId)
                        .eq(FamilyPermission::getGranteeUserId, actorUserId)
                        .eq(FamilyPermission::getStatus, "active")
                        .isNull(FamilyPermission::getRevokedAt)
                        .last("LIMIT 1"));
        if (permission == null || !scope.granted(permission)
                || !isActiveMember(permission.getFamilyId(), actorUserId)
                || !isActiveMember(permission.getFamilyId(), subjectUserId)) {
            throw BusinessException.forbidden("未获得该家庭成员的数据访问权限");
        }
        return subjectUserId;
    }

    private boolean isActiveMember(Long familyId, Long userId) {
        return memberMapper.selectCount(new LambdaQueryWrapper<FamilyGroupMember>()
                .eq(FamilyGroupMember::getFamilyId, familyId)
                .eq(FamilyGroupMember::getUserId, userId)
                .eq(FamilyGroupMember::getStatus, "active")) > 0;
    }
}
