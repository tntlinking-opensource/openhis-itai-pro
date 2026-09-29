-- 病案首页手术行横表：对齐口径 v0.9 / DM-MRMS-001 v1.1（禁 JSON）
-- 存量列（V9）不改名：heal_grade_code / anst_way_code / oper_dr_name 等
-- 增量：助手术/配合护士/手术时间/麻醉起止/样本备注/送检

ALTER TABLE mrms_homepage_procedure
    ADD COLUMN IF NOT EXISTS asit_1_name varchar(50);
ALTER TABLE mrms_homepage_procedure
    ADD COLUMN IF NOT EXISTS asit_2_name varchar(50);
ALTER TABLE mrms_homepage_procedure
    ADD COLUMN IF NOT EXISTS nurse_name varchar(50);
ALTER TABLE mrms_homepage_procedure
    ADD COLUMN IF NOT EXISTS oprn_time timestamptz(6);
ALTER TABLE mrms_homepage_procedure
    ADD COLUMN IF NOT EXISTS anst_start_time timestamptz(6);
ALTER TABLE mrms_homepage_procedure
    ADD COLUMN IF NOT EXISTS anst_end_time timestamptz(6);
ALTER TABLE mrms_homepage_procedure
    ADD COLUMN IF NOT EXISTS specimen varchar(500);
ALTER TABLE mrms_homepage_procedure
    ADD COLUMN IF NOT EXISTS note varchar(500);
ALTER TABLE mrms_homepage_procedure
    ADD COLUMN IF NOT EXISTS send_lab_flag bool;

COMMENT ON COLUMN mrms_homepage_procedure.asit_1_name IS
    '一助姓名（终改；4401 asit_1_name）';
COMMENT ON COLUMN mrms_homepage_procedure.asit_2_name IS
    '二助姓名（终改；4401 asit_name2）';
COMMENT ON COLUMN mrms_homepage_procedure.nurse_name IS
    '配合护士姓名（终改）';
COMMENT ON COLUMN mrms_homepage_procedure.oprn_time IS
    '手术时间（业务终改；非计费 occurrence_time；可推导 oprn_date）';
COMMENT ON COLUMN mrms_homepage_procedure.anst_start_time IS
    '麻醉开始时间（回显/终改）';
COMMENT ON COLUMN mrms_homepage_procedure.anst_end_time IS
    '麻醉结束时间（回显/终改）';
COMMENT ON COLUMN mrms_homepage_procedure.specimen IS
    '样本（回显执行；可终改；与备注分列）';
COMMENT ON COLUMN mrms_homepage_procedure.note IS
    '备注（回显执行；可终改；与样本分列）';
COMMENT ON COLUMN mrms_homepage_procedure.send_lab_flag IS
    '是否送检（回显/终改）';
