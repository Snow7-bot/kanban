package com.kangban.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("medications")
public class Medication {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long memberId;

    private String name;

    private String dosage;

    private String unit;

    private String instruction;

    private String frequency;

    private Integer inventory;

    private String times;

    private LocalDate startDate;

    private LocalDate endDate;

    private String note;

    /** Standard drug identifier for interaction matching */
    private String standardDrugId;

    /** Standardized drug name for interaction matching */
    private String standardDrugName;

    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableField("deleted_at")
    private LocalDateTime deletedAt;
}
