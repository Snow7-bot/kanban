package com.kangban.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@Schema(description = "添加健康记录请求")
public class AddHealthRecordRequest {

    @Schema(description = "共享账号用户 ID（空表示当前账号）")
    private Long subjectUserId;

    @Schema(description = "家庭成员名称（空表示本人）")
    private String memberName;

    @Schema(description = "家庭成员 ID（空表示本人）")
    private Long memberId;

    @NotBlank(message = "指标类型不能为空")
    @Schema(description = "指标类型（blood_pressure/blood_sugar/heart_rate/temperature/weight等）")
    private String metric;

    @NotBlank(message = "指标值不能为空")
    @Schema(description = "指标值")
    private String value;

    @Schema(description = "单位")
    private String unit;

    @Schema(description = "记录日期")
    private LocalDate recordedDate;

    @Schema(description = "记录时间")
    private LocalTime recordedTime;

    @Schema(description = "备注")
    private String note;
}
