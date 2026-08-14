package com.kangban.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "创建会话请求")
public class CreateSessionRequest {

    @NotBlank(message = "会话标题不能为空")
    @Schema(description = "会话标题")
    private String title;

    @Schema(description = "患者信息（JSON字符串）")
    private String patientData;

    @Schema(description = "共享账号用户 ID；为空表示当前账号")
    private Long subjectUserId;

    @Schema(description = "家庭成员ID；为空表示本人")
    private Long memberId;
}
