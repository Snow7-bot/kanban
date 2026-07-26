package com.kangban.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;

@Data
@Schema(description = "更新个人信息请求")
public class UpdateProfileRequest {

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "姓名")
    private String name;

    @Schema(description = "性别")
    private String gender;

    @Schema(description = "出生日期")
    private LocalDate birthday;

    @Schema(description = "血型")
    private String bloodType;

    @Schema(description = "身高(cm)")
    private Double height;

    @Schema(description = "体重(kg)")
    private Double weight;

    @Schema(description = "紧急联系人")
    private String emergencyContact;

    @Schema(description = "邮箱")
    private String email;
}
