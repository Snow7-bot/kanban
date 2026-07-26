package com.kangban.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "分页查询")
public class PageQuery {

    @Schema(description = "当前页码")
    private int page = 1;

    @Schema(description = "每页条数")
    private int pageSize = 20;
}
