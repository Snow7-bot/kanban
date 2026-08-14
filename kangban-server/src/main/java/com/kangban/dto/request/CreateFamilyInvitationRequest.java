package com.kangban.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class CreateFamilyInvitationRequest {
    @NotBlank(message = "用户名不能为空")
    @Pattern(
            regexp = "^[A-Za-z\\u4e00-\\u9fa5][A-Za-z0-9_\\u4e00-\\u9fa5]{3,19}$",
            message = "请输入有效的用户名"
    )
    private String username;

    @NotBlank(message = "请选择家庭关系")
    private String relation;

    @Valid
    @NotNull(message = "请选择共享权限")
    private FamilyPermissionRequest permissions;
}
