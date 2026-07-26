package com.kangban.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class CreateFamilyInvitationRequest {
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1\\d{10}$", message = "请输入有效的手机号")
    private String phone;

    @NotBlank(message = "请选择家庭关系")
    private String relation;

    @Valid
    @NotNull(message = "请选择共享权限")
    private FamilyPermissionRequest permissions;
}
