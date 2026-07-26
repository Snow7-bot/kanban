-- V7: Drug interaction rules engine
-- ============================================================

-- 7.1 Add standard drug identifier fields to medications
ALTER TABLE medications
    ADD COLUMN standard_drug_id   VARCHAR(100) DEFAULT NULL COMMENT 'Standard drug identifier (e.g., ATC code or reference ID)',
    ADD COLUMN standard_drug_name VARCHAR(200) DEFAULT NULL COMMENT 'Standardized drug name for interaction matching';

-- 7.2 Drug interaction rules table
CREATE TABLE drug_interaction_rules (
    id          BIGINT       NOT NULL AUTO_INCREMENT  COMMENT 'Primary key',
    drug_a      VARCHAR(200) NOT NULL                 COMMENT 'Standard identifier for drug A',
    drug_b      VARCHAR(200) NOT NULL                 COMMENT 'Standard identifier for drug B',
    risk_level  VARCHAR(20)  NOT NULL                 COMMENT 'low | medium | high',
    description TEXT         NOT NULL                 COMMENT 'Description of the interaction',
    advice      TEXT         NOT NULL                 COMMENT 'Clinical advice / recommendation',
    source      VARCHAR(200) NOT NULL DEFAULT '演示规则' COMMENT 'Data source or reference',
    version     VARCHAR(20)  NOT NULL DEFAULT '1.0'   COMMENT 'Rule version',
    active      TINYINT(1)   NOT NULL DEFAULT 1       COMMENT 'Whether this rule is active',
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_drug_pair (drug_a, drug_b),
    KEY idx_dir_risk (risk_level),
    KEY idx_dir_active (active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Drug interaction rules — demo data only, NOT medical advice';

-- 7.3 Enhance drug_interaction_results table
ALTER TABLE drug_interaction_results
    ADD COLUMN checked_drug_ids   VARCHAR(500) DEFAULT NULL COMMENT 'Comma-separated medication IDs that were checked',
    ADD COLUMN matched_rule_ids   VARCHAR(500) DEFAULT NULL COMMENT 'Comma-separated rule IDs that matched',
    ADD COLUMN rule_version       VARCHAR(20)  DEFAULT NULL COMMENT 'Rule engine version at check time',
    ADD COLUMN disclaimer         VARCHAR(500) DEFAULT '演示规则，非医疗建议。请咨询医生或药师。' COMMENT 'Disclaimer shown to user';

-- ============================================================
-- Seed demo interaction rules (5 pairs)
-- ALL explicitly marked as 演示数据 (demo data)
-- ============================================================

INSERT INTO drug_interaction_rules (drug_a, drug_b, risk_level, description, advice, source, version, active) VALUES
-- Warfarin + Aspirin: HIGH risk bleeding
('warfarin', 'aspirin', 'high',
 '华法林与阿司匹林合用会显著增加出血风险。两者均影响凝血功能，联合使用可导致胃肠道出血、颅内出血等严重不良反应。',
 '避免联合使用。如必须合用，需在医生严密监测INR值下进行，并考虑加用胃黏膜保护剂。',
 '演示规则 — 参考《中华人民共和国药典临床用药须知》', '1.0', 1),

-- Metformin + alcohol: MEDIUM risk lactic acidosis
('metformin', 'alcohol', 'medium',
 '二甲双胍与酒精合用可增加乳酸性酸中毒的风险。酒精可抑制肝脏糖异生，与二甲双胍协同加重乳酸堆积。',
 '服药期间应避免饮酒。如需饮酒，应在医生指导下暂停用药24小时以上。',
 '演示规则 — 参考药品说明书', '1.0', 1),

-- ACE inhibitor + Potassium supplement: HIGH risk hyperkalemia
('ace_inhibitor', 'potassium', 'high',
 'ACE抑制剂（如卡托普利、依那普利）与钾补充剂合用可导致严重高钾血症，可能引发心律失常。',
 '避免联合使用。如需补钾，需定期监测血钾水平，并在医生指导下调整剂量。',
 '演示规则 — 参考《中国高血压防治指南》', '1.0', 1),

-- Amlodipine + Grapefruit: MEDIUM risk increased drug level
('amlodipine', 'grapefruit', 'medium',
 '葡萄柚（西柚）及其制品可抑制CYP3A4酶，导致氨氯地平血药浓度升高，增加低血压和水肿风险。',
 '服药期间避免食用葡萄柚或饮用葡萄柚汁。',
 '演示规则 — 参考FDA药物相互作用警示', '1.0', 1),

-- Statin + Macrolide antibiotic: HIGH risk myopathy
('statin', 'macrolide', 'high',
 '他汀类药物（如阿托伐他汀、辛伐他汀）与大环内酯类抗生素（如红霉素、克拉霉素）合用可显著增加横纹肌溶解风险。大环内酯类抑制CYP3A4，导致他汀血药浓度大幅升高。',
 '避免联合使用。如必须使用抗生素，可考虑更换为青霉素类或头孢类，或暂停他汀类药物。',
 '演示规则 — 参考《血脂异常防治指南》', '1.0', 1);
