package com.kangban.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@TableName("dose_records")
public class DoseRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long medicationId;

    private Long userId;

    private LocalTime scheduledTime;

    private LocalDateTime confirmedAt;

    private String status;

    private LocalDateTime createdAt;
}
