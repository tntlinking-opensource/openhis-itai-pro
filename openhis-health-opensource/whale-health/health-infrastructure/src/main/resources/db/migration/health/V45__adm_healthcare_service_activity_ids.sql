-- V41：挂号类型绑定诊查费 AD（DM-ADM-007 v1.1.0 / AS-BAS-MST-015）
-- 已跑库增量；新库由 V2__adm_ddl 基线直接带列。

ALTER TABLE adm_healthcare_service
    ADD COLUMN IF NOT EXISTS activity_ids varchar(512);

COMMENT ON COLUMN adm_healthcare_service.activity_ids IS
    '诊查费 ActivityDefinition 主键 CSV（逗号分隔）；零元号可空；见 DM-ADM-007 BR-HCS-010～012';

COMMENT ON COLUMN adm_healthcare_service.yb_no IS
    '医保编号（yb_no）已废弃业务读写；存量列可空，新业务取绑定诊查费 AD';

-- 产品零元号种子：清空写死医保码（资格仍按 name=复诊0元号）
UPDATE adm_healthcare_service
SET yb_no = NULL,
    updated_at = CURRENT_TIMESTAMP
WHERE id = 322957328119180160
  AND is_deleted = false;
