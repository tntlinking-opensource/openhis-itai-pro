-- 修复 V8 排班模板种子：有效期过短、仅周一～三、部分 tenant_id 为空
-- 并补充演示用正式排班 + 当日号源（wkf_schedule / wkf_slot），便于挂号台开箱验证

-- 1. 抬升模板有效期与租户（已执行过 V8 的库亦生效）
UPDATE bkg_schedule_template
SET tenant_id       = '1',
    effective_start = TIMESTAMPTZ '2026-01-01 00:00:00+00',
    effective_end   = TIMESTAMPTZ '2027-12-31 23:59:59+00',
    updated_at      = CURRENT_TIMESTAMP
WHERE id = '328058080426201088'
  AND is_deleted = FALSE;

UPDATE bkg_week_schedule_template
SET tenant_id     = '1',
    is_default    = TRUE,
    effective_start = TIMESTAMPTZ '2026-01-01 00:00:00+00',
    effective_end   = TIMESTAMPTZ '2027-12-31 23:59:59+00',
    updated_at      = CURRENT_TIMESTAMP
WHERE id = '328056809673723904'
  AND is_deleted = FALSE;

-- 2. 周模板补全周四～周日（ISO 1=周一 … 7=周日）
INSERT INTO bkg_week_schedule_template_item (id, tenant_id, week_schedule_template_id, weekday, day_schedule_template_id, sort_no, enabled, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by, version)
VALUES
('328058338325565443', '1', '328056809673723904', 4, '328058080426201088', 3, TRUE, '2026-06-28T00:00:00+00', '2026-06-28T00:00:00+00', '1', '1', FALSE, NULL, NULL, 0),
('328058338325565444', '1', '328056809673723904', 5, '328058080426201088', 4, TRUE, '2026-06-28T00:00:00+00', '2026-06-28T00:00:00+00', '1', '1', FALSE, NULL, NULL, 0),
('328058338325565445', '1', '328056809673723904', 6, '328058080426201088', 5, TRUE, '2026-06-28T00:00:00+00', '2026-06-28T00:00:00+00', '1', '1', FALSE, NULL, NULL, 0),
('328058338325565446', '1', '328056809673723904', 7, '328058080426201088', 6, TRUE, '2026-06-28T00:00:00+00', '2026-06-28T00:00:00+00', '1', '1', FALSE, NULL, NULL, 0)
ON CONFLICT (id) DO NOTHING;

-- 3. 演示正式排班（门诊内科 + 门诊医生 + 普通号，已发布 active）
INSERT INTO wkf_schedule (id, tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by, version, organization_id, name, status_enum, active_flag, planning_horizon_start, planning_horizon_end, service_type_concept_json, healthcare_service_id, service_category_json, specialty_json, comment_markdown, substitute_practitioner_id, substitute_reason)
VALUES (
    '329700000000000001', '1', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, '1', '1', FALSE, NULL, NULL, 0,
    '322663218269917184', '演示-门诊医生普通号', 'active', TRUE,
    TIMESTAMPTZ '2026-01-01 00:00:00+08', TIMESTAMPTZ '2027-12-31 23:59:59+08',
    NULL, '322957264586018816', NULL, NULL, 'Flyway 演示排班', NULL, NULL
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO wkf_schedule_actor (id, tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by, version, schedule_id, actor_type, actor_id, sort_no)
VALUES (
    '329700000000000002', '1', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, '1', '1', FALSE, NULL, NULL, 0,
    '329700000000000001', 10, '322963602326687744', 0
)
ON CONFLICT (id) DO NOTHING;

-- 4. 2026-06-01 起 90 天每日放号（08:00–18:00 东八区，容量 30）
INSERT INTO wkf_slot (id, tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by, version, organization_id, schedule_id, start_time, end_time, status_code, capacity, booked_count, overbooked_flag, service_category_json, service_type_json, specialty_json, appointment_type_json, comment_text)
SELECT
    329701000000000000 + gs.day_offset,
    1,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    1,
    1,
    FALSE,
    NULL,
    NULL,
    0,
    322663218269917184,
    329700000000000001,
    (DATE '2026-06-01' + gs.day_offset) + TIME '08:00:00' AT TIME ZONE 'Asia/Shanghai',
    (DATE '2026-06-01' + gs.day_offset) + TIME '18:00:00' AT TIME ZONE 'Asia/Shanghai',
    'free',
    30,
    0,
    FALSE,
    NULL,
    NULL,
    NULL,
    NULL,
    '演示号源'
FROM generate_series(0, 89) AS gs(day_offset)
ON CONFLICT (id) DO NOTHING;
