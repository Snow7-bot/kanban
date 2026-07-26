package com.kangban.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "健康趋势查询")
public class TrendsQuery {

    @Schema(description = "指标类型")
    private String metric;

    @Schema(description = "查询天数")
    private Integer days;

    @Schema(description = "家庭成员名称")
    private String member;
}
