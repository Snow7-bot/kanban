package com.kangban.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "药物相互作用检查请求")
public class CheckInteractionRequest {

    @Schema(description = "药物名称列表")
    private List<String> drugNames;

    @Schema(description = "药物 ID 列表")
    private List<String> drugIds;

    @Schema(description = "药物名称（逗号分隔）")
    private String drugs;

    public List<String> getResolvedDrugIds() {
        if (drugIds != null && !drugIds.isEmpty()) {
            return drugIds;
        }
        if (drugNames != null && !drugNames.isEmpty()) {
            return drugNames;
        }
        if (drugs == null || drugs.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(drugs.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }
}
