package com.kangban.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("family_invitations")
public class FamilyInvitation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long familyId;
    private Long inviterUserId;
    private Long inviteeUserId;
    private String relation;
    private Boolean canViewHealth;
    private Boolean canAddHealth;
    private Boolean canViewRecords;
    private Boolean canViewMedications;
    private Boolean canViewReports;
    private Boolean canUseAi;
    private Boolean canModify;
    private Boolean canDelete;
    private String status;
    private LocalDateTime expiresAt;
    private LocalDateTime respondedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
