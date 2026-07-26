package com.kangban.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("medical_records")
public class MedicalRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long memberId;

    private String recordName;

    private String recordType;

    private String hospital;

    private String department;

    private String doctor;

    private LocalDate recordDate;

    private String fileUrl;

    private Long fileSize;

    private String fileType;

    private String status;

    private Integer confidence;

    private String ocrText;

    private String diagnosisData;

    private String medicationsData;

    private String advicesData;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableField("deleted_at")
    private LocalDateTime deletedAt;
}
