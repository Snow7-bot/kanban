package com.kangban.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "添加家庭成员请求")
public class AddFamilyRequest {

    @NotBlank(message = "成员姓名不能为空")
    @Schema(description = "姓名")
    private String name;

    @NotBlank(message = "关系不能为空")
    @Schema(description = "关系")
    private String relation;

    @Schema(description = "年龄")
    private Integer age;

    @Schema(description = "性别")
    private String gender;

    @Schema(description = "备注")
    private String note;
}
