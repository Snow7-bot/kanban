package com.kangban.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "添加用药提醒请求")
public class AddMedicationRequest {

    @Schema(description = "家庭成员ID；为空表示本人")
    private Long memberId;

    @NotBlank(message = "药品名称不能为空")
    @Schema(description = "药品名称")
    private String name;

    @Schema(description = "剂量")
    private String dosage;

    @Schema(description = "单位")
    private String unit;

    @Schema(description = "服用说明")
    private String instruction;

    @Schema(description = "频率")
    private String frequency;

    @Schema(description = "库存")
    private Integer inventory;

    @Schema(description = "服用时间（逗号分隔）")
    private String times;
}
