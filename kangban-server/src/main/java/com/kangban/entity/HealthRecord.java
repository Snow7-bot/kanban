package com.kangban.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@TableName("health_records")
public class HealthRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long memberId;

    private String memberName;

    private String metric;

    private String value;

    private String unit;

    private LocalDate recordedDate;

    private LocalTime recordedTime;

    private String note;

    private LocalDateTime createdAt;

    @TableField("deleted_at")
    private LocalDateTime deletedAt;
}
