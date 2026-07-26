package com.kangban.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "更新患者信息请求")
public class UpdatePatientRequest {

    @Schema(description = "患者姓名")
    private String name;

    @Schema(description = "年龄")
    private Integer age;

    @Schema(description = "性别")
    private String gender;

    @Schema(description = "主诉")
    private String chiefComplaint;

    @Schema(description = "既往病史")
    private String medicalHistory;

    @Schema(description = "过敏史")
    private String allergyHistory;
}
