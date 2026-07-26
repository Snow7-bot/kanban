package com.kangban.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("drug_interaction_rules")
public class DrugInteractionRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** Standard identifier for drug A */
    private String drugA;

    /** Standard identifier for drug B */
    private String drugB;

    /** low | medium | high */
    private String riskLevel;

    /** Description of the interaction */
    private String description;

    /** Clinical advice */
    private String advice;

    /** Data source reference */
    private String source;

    /** Rule version */
    private String version;

    /** Whether this rule is active */
    private Integer active;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
