package com.kangban.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("drug_interaction_results")
public class DrugInteractionResult {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String drugNames;

    private String resultData;

    /** Comma-separated medication IDs that were checked */
    private String checkedDrugIds;

    /** Comma-separated rule IDs that matched */
    private String matchedRuleIds;

    /** Rule engine version at check time */
    private String ruleVersion;

    /** Disclaimer text */
    private String disclaimer;

    private LocalDateTime createdAt;
}
