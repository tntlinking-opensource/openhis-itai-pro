-- Dev seed: booking 排班模板（T1 放号时段 / T2 日排班 / TW 周排班）
-- 科室：门诊内科 322663218269917184
-- 不含 wkf_schedule / wkf_slot；正式排班由排班员在「门诊排班」自行生成

-- ---------------------------------------------------------------------------
-- T1 · 放号时段「工作日时段」：上午 + 下午两行
-- ---------------------------------------------------------------------------
INSERT INTO bkg_slot_provision_template (
    id, tenant_id, organization_id, template_code, template_name, slot_templates_json,
    enabled, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by, version
)
VALUES (
    328057873508601856, 1, 322663218269917184, 'T1-WORKDAY', '工作日时段',
    '[{"startTime":"08:00","endTime":"12:00","capacity":10},{"startTime":"14:00","endTime":"17:00","capacity":10}]',
    TRUE, TIMESTAMPTZ '2026-06-24 06:24:52.065+00', TIMESTAMPTZ '2026-07-13 12:00:00+00', 1, 1,
    FALSE, NULL, NULL, 0
)
ON CONFLICT (id) DO UPDATE SET
    template_code        = EXCLUDED.template_code,
    template_name        = EXCLUDED.template_name,
    slot_templates_json  = EXCLUDED.slot_templates_json,
    enabled              = EXCLUDED.enabled,
    updated_at           = EXCLUDED.updated_at,
    updated_by           = EXCLUDED.updated_by;

-- ---------------------------------------------------------------------------
-- T2 · 日排班：科室普通号 + 医师专家号（共用 T1）
-- ---------------------------------------------------------------------------
INSERT INTO bkg_schedule_template (
    id, tenant_id, organization_id, template_code, template_name, schedule_mode,
    practitioner_id, location_id, healthcare_service_id, slot_provision_template_id,
    default_release_policy_id, effective_start, effective_end, priority,
    source_practitioner_role_id, enabled, created_at, updated_at, created_by, updated_by,
    is_deleted, deleted_at, deleted_by, version
)
VALUES
(
    328058080426201089, 1, 322663218269917184, 'T2-INTERNAL-GENERAL', '内科普通号', 'MODE-B',
    NULL, NULL, 322957264586018816, 328057873508601856,
    NULL, DATE '2026-01-01', DATE '2027-12-31', 0,
    NULL, TRUE, TIMESTAMPTZ '2026-06-24 06:25:41.398+00', TIMESTAMPTZ '2026-07-13 12:00:00+00', 1, 1,
    FALSE, NULL, NULL, 0
),
(
    328058080426201088, 1, 322663218269917184, 'T2-EXPERT-DOCTOR', '门诊医生专家号', 'MODE-A',
    322963602326687744, NULL, 322957296852799489, 328057873508601856,
    NULL, DATE '2026-01-01', DATE '2027-12-31', 0,
    NULL, TRUE, TIMESTAMPTZ '2026-06-24 06:25:41.398+00', TIMESTAMPTZ '2026-07-13 12:00:00+00', 1, 1,
    FALSE, NULL, NULL, 0
)
ON CONFLICT (id) DO UPDATE SET
    template_code               = EXCLUDED.template_code,
    template_name               = EXCLUDED.template_name,
    schedule_mode               = EXCLUDED.schedule_mode,
    practitioner_id             = EXCLUDED.practitioner_id,
    healthcare_service_id       = EXCLUDED.healthcare_service_id,
    slot_provision_template_id  = EXCLUDED.slot_provision_template_id,
    effective_start             = EXCLUDED.effective_start,
    effective_end               = EXCLUDED.effective_end,
    enabled                     = EXCLUDED.enabled,
    updated_at                  = EXCLUDED.updated_at,
    updated_by                  = EXCLUDED.updated_by;

-- ---------------------------------------------------------------------------
-- TW · 周排班（科室默认；周一至周五：普通号 + 专家号）
-- ---------------------------------------------------------------------------
INSERT INTO bkg_week_schedule_template (
    id, tenant_id, organization_id, template_code, template_name,
    fallback_bookable, is_default, enabled, effective_start, effective_end,
    created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by, version
)
VALUES (
    328056809673723904, 1, 322663218269917184, 'TW-INTERNAL-DEFAULT', '内科周排班',
    FALSE, TRUE, TRUE, DATE '2026-01-01', DATE '2027-12-31',
    TIMESTAMPTZ '2026-06-24 06:20:38.427+00', TIMESTAMPTZ '2026-07-13 12:00:00+00', 1, 1,
    FALSE, NULL, NULL, 0
)
ON CONFLICT (id) DO UPDATE SET
    template_code     = EXCLUDED.template_code,
    template_name     = EXCLUDED.template_name,
    fallback_bookable = EXCLUDED.fallback_bookable,
    is_default        = EXCLUDED.is_default,
    effective_start   = EXCLUDED.effective_start,
    effective_end     = EXCLUDED.effective_end,
    enabled           = EXCLUDED.enabled,
    updated_at        = EXCLUDED.updated_at,
    updated_by        = EXCLUDED.updated_by;

INSERT INTO bkg_week_schedule_template_item (
    id, tenant_id, week_schedule_template_id, weekday, day_schedule_template_id,
    sort_no, enabled, created_at, updated_at, created_by, updated_by,
    is_deleted, deleted_at, deleted_by, version
)
VALUES
-- 周一
(328058338325565440, 1, 328056809673723904, 1, 328058080426201089, 0, TRUE, TIMESTAMPTZ '2026-07-13 12:00:00+00', TIMESTAMPTZ '2026-07-13 12:00:00+00', 1, 1, FALSE, NULL, NULL, 0),
(328058338325565441, 1, 328056809673723904, 1, 328058080426201088, 1, TRUE, TIMESTAMPTZ '2026-07-13 12:00:00+00', TIMESTAMPTZ '2026-07-13 12:00:00+00', 1, 1, FALSE, NULL, NULL, 0),
-- 周二
(328058338325565442, 1, 328056809673723904, 2, 328058080426201089, 0, TRUE, TIMESTAMPTZ '2026-07-13 12:00:00+00', TIMESTAMPTZ '2026-07-13 12:00:00+00', 1, 1, FALSE, NULL, NULL, 0),
(328058338325565443, 1, 328056809673723904, 2, 328058080426201088, 1, TRUE, TIMESTAMPTZ '2026-07-13 12:00:00+00', TIMESTAMPTZ '2026-07-13 12:00:00+00', 1, 1, FALSE, NULL, NULL, 0),
-- 周三
(328058338325565444, 1, 328056809673723904, 3, 328058080426201089, 0, TRUE, TIMESTAMPTZ '2026-07-13 12:00:00+00', TIMESTAMPTZ '2026-07-13 12:00:00+00', 1, 1, FALSE, NULL, NULL, 0),
(328058338325565445, 1, 328056809673723904, 3, 328058080426201088, 1, TRUE, TIMESTAMPTZ '2026-07-13 12:00:00+00', TIMESTAMPTZ '2026-07-13 12:00:00+00', 1, 1, FALSE, NULL, NULL, 0),
-- 周四
(328058338325565446, 1, 328056809673723904, 4, 328058080426201089, 0, TRUE, TIMESTAMPTZ '2026-07-13 12:00:00+00', TIMESTAMPTZ '2026-07-13 12:00:00+00', 1, 1, FALSE, NULL, NULL, 0),
(328058338325565447, 1, 328056809673723904, 4, 328058080426201088, 1, TRUE, TIMESTAMPTZ '2026-07-13 12:00:00+00', TIMESTAMPTZ '2026-07-13 12:00:00+00', 1, 1, FALSE, NULL, NULL, 0),
-- 周五
(328058338325565448, 1, 328056809673723904, 5, 328058080426201089, 0, TRUE, TIMESTAMPTZ '2026-07-13 12:00:00+00', TIMESTAMPTZ '2026-07-13 12:00:00+00', 1, 1, FALSE, NULL, NULL, 0),
(328058338325565449, 1, 328056809673723904, 5, 328058080426201088, 1, TRUE, TIMESTAMPTZ '2026-07-13 12:00:00+00', TIMESTAMPTZ '2026-07-13 12:00:00+00', 1, 1, FALSE, NULL, NULL, 0)
ON CONFLICT (id) DO UPDATE SET
    week_schedule_template_id = EXCLUDED.week_schedule_template_id,
    weekday                   = EXCLUDED.weekday,
    day_schedule_template_id  = EXCLUDED.day_schedule_template_id,
    sort_no                   = EXCLUDED.sort_no,
    enabled                   = EXCLUDED.enabled,
    updated_at                = EXCLUDED.updated_at,
    updated_by                = EXCLUDED.updated_by;
