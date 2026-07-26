package com.kangban.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "健康报告查询")
public class ReportQuery {

    @Schema(description = "统计周期（week/month/quarter/year）")
    private String period;

    @Schema(description = "家庭成员名称")
    private String member;
}
