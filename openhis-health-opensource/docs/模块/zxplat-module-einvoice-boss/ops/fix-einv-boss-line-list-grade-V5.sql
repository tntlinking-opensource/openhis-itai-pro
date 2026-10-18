-- 应急：einv_boss_line 补清单编码/甲乙丙列（对齐 Flyway V5）
-- 适用：Hibernate Schema-validation missing column [item_code]
-- 正常路径：重启应用，待 flyway_schema_history_einvoice_boss 执行 V5__einv_boss_line_list_grade.sql
-- 本脚本可重复执行（IF NOT EXISTS）

ALTER TABLE einv_boss_line
    ADD COLUMN IF NOT EXISTS item_code VARCHAR(50);
ALTER TABLE einv_boss_line
    ADD COLUMN IF NOT EXISTS list_medical_care_type VARCHAR(1);
ALTER TABLE einv_boss_line
    ADD COLUMN IF NOT EXISTS list_remark VARCHAR(200);

COMMENT ON COLUMN einv_boss_line.item_code IS '清单药品/项目编码（listDetail.code；医保 ybNo 优先）';
COMMENT ON COLUMN einv_boss_line.list_medical_care_type IS '清单医保药品分类（1甲/2乙/3丙）';
COMMENT ON COLUMN einv_boss_line.list_remark IS '清单备注（甲类/乙类/丙类/自费）';

-- 可选核对：
-- SELECT column_name FROM information_schema.columns
--  WHERE table_name = 'einv_boss_line'
--    AND column_name IN ('item_code', 'list_medical_care_type', 'list_remark');
--
-- SELECT version, description, success FROM flyway_schema_history_einvoice_boss
--  WHERE version IN ('4', '5') ORDER BY installed_rank;
