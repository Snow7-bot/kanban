package com.kangban.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("family_members")
public class FamilyMember {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String name;

    private String relation;

    private Integer age;

    private String gender;

    private String avatarUrl;

    private String note;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableField("deleted_at")
    private LocalDateTime deletedAt;
}
