package com.kangban.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ocr_analysis_tasks")
public class OcrAnalysisTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long recordId;

    private Long userId;

    private String status;

    private Integer progress;

    private String resultData;

    private String errorMessage;

    private LocalDateTime startedAt;

    private LocalDateTime completedAt;

    private LocalDateTime createdAt;
}
