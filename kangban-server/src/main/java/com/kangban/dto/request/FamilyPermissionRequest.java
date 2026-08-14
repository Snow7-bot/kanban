package com.kangban.dto.request;

import lombok.Data;

@Data
public class FamilyPermissionRequest {
    private boolean canViewHealth;
    private boolean canAddHealth;
    private boolean canViewRecords;
    private boolean canViewMedications;
    private boolean canViewReports;
    private boolean canUseAi;
    private boolean canModify;
    private boolean canDelete;
}
