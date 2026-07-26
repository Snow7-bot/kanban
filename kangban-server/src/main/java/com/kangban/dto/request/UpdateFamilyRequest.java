package com.kangban.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "更新家庭成员请求")
public class UpdateFamilyRequest {

    @Schema(description = "姓名")
    private String name;

    @Schema(description = "关系")
    private String relation;

    @Schema(description = "年龄")
    private Integer age;

    @Schema(description = "性别")
    private String gender;

    @Schema(description = "备注")
    private String note;
}
