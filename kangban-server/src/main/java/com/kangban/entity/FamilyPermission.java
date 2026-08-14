package com.kangban.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("family_permissions")
public class FamilyPermission {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long familyId;
    private Long subjectUserId;
    private Long granteeUserId;
    private Boolean canViewHealth;
    private Boolean canAddHealth;
    private Boolean canViewRecords;
    private Boolean canViewMedications;
    private Boolean canViewReports;
    private Boolean canUseAi;
    private Boolean canModify;
    private Boolean canDelete;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime revokedAt;
}
