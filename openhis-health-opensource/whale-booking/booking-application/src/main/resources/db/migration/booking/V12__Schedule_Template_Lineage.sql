-- 正式 Schedule 保存来源 T2 / T1 快照，供放号与手动新建引用
ALTER TABLE wkf_schedule
    ADD COLUMN IF NOT EXISTS source_schedule_template_id BIGINT,
    ADD COLUMN IF NOT EXISTS slot_provision_template_id BIGINT;

COMMENT ON COLUMN wkf_schedule.source_schedule_template_id IS '来源 T2 日排班模板主键（模板生成时写入）';
COMMENT ON COLUMN wkf_schedule.slot_provision_template_id IS '关联 T1 放号时段模板主键（生成或手动创建时写入）';

CREATE INDEX IF NOT EXISTS idx_wkf_schedule_source_tpl
    ON wkf_schedule (organization_id, source_schedule_template_id)
    WHERE is_deleted = false;
