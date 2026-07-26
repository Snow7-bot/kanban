package com.kangban.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@Schema(description = "更新健康记录请求")
public class UpdateHealthRecordRequest {

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
