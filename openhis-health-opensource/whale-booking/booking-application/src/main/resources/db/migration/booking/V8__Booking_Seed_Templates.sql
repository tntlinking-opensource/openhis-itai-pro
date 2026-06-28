-- Booking 排班模板种子（自当前库导出 2026-06-26）

-- bkg_slot_provision_template (1 rows)
INSERT INTO bkg_slot_provision_template (id, tenant_id, organization_id, template_code, template_name, slot_templates_json, enabled, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by, version)
VALUES ('328057873508601856', '1', '322663218269917184', 'T1-4e0dda20b86d', '每天30个', '[{"startTime":"08:00","endTime":"18:00","capacity":30}]', TRUE, '2026-06-24T06:24:52.065Z', '2026-06-24T06:24:52.065Z', '1', '1', FALSE, NULL, NULL, 0)
ON CONFLICT (id) DO NOTHING;

-- bkg_schedule_template (1 rows)
INSERT INTO bkg_schedule_template (id, tenant_id, organization_id, template_code, template_name, schedule_mode, practitioner_id, location_id, healthcare_service_id, slot_provision_template_id, default_release_policy_id, effective_start, effective_end, priority, source_practitioner_role_id, enabled, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by, version)
VALUES ('328058080426201088', '1', '322663218269917184', 'T2-2011f503dd7e', '门诊医生', 'MODE-A', '322963602326687744', NULL, '322957264586018816', '328057873508601856', NULL, '2026-01-01T00:00:00.000Z', '2027-12-31T23:59:59.000Z', 0, NULL, TRUE, '2026-06-24T06:25:41.398Z', '2026-06-24T06:26:16.415Z', '1', '1', FALSE, NULL, NULL, 0)
ON CONFLICT (id) DO NOTHING;

-- bkg_week_schedule_template (1 rows)
INSERT INTO bkg_week_schedule_template (id, tenant_id, organization_id, template_code, template_name, fallback_bookable, is_default, enabled, effective_start, effective_end, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by, version)
VALUES ('328056809673723904', '1', '322663218269917184', 'TW-33336d44ecaf', '内科周排班', FALSE, TRUE, TRUE, '2026-01-01T00:00:00.000Z', '2027-12-31T23:59:59.000Z', '2026-06-24T06:20:38.427Z', '2026-06-24T06:26:42.886Z', '1', '1', FALSE, NULL, NULL, 0)
ON CONFLICT (id) DO NOTHING;

-- bkg_week_schedule_template_item (3 rows)
INSERT INTO bkg_week_schedule_template_item (id, tenant_id, week_schedule_template_id, weekday, day_schedule_template_id, sort_no, enabled, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by, version)
VALUES ('328058338325565440', '1', '328056809673723904', 1, '328058080426201088', 0, TRUE, '2026-06-24T06:26:42.886Z', '2026-06-24T06:26:42.886Z', '1', '1', FALSE, NULL, NULL, 0)
ON CONFLICT (id) DO NOTHING;

INSERT INTO bkg_week_schedule_template_item (id, tenant_id, week_schedule_template_id, weekday, day_schedule_template_id, sort_no, enabled, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by, version)
VALUES ('328058338325565441', '1', '328056809673723904', 2, '328058080426201088', 1, TRUE, '2026-06-24T06:26:42.886Z', '2026-06-24T06:26:42.886Z', '1', '1', FALSE, NULL, NULL, 0)
ON CONFLICT (id) DO NOTHING;

INSERT INTO bkg_week_schedule_template_item (id, tenant_id, week_schedule_template_id, weekday, day_schedule_template_id, sort_no, enabled, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by, version)
VALUES ('328058338325565442', '1', '328056809673723904', 3, '328058080426201088', 2, TRUE, '2026-06-24T06:26:42.886Z', '2026-06-24T06:26:42.886Z', '1', '1', FALSE, NULL, NULL, 0)
ON CONFLICT (id) DO NOTHING;

INSERT INTO bkg_week_schedule_template_item (id, tenant_id, week_schedule_template_id, weekday, day_schedule_template_id, sort_no, enabled, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by, version)
VALUES ('328058338325565443', '1', '328056809673723904', 4, '328058080426201088', 3, TRUE, '2026-06-24T06:26:42.886Z', '2026-06-24T06:26:42.886Z', '1', '1', FALSE, NULL, NULL, 0)
ON CONFLICT (id) DO NOTHING;

INSERT INTO bkg_week_schedule_template_item (id, tenant_id, week_schedule_template_id, weekday, day_schedule_template_id, sort_no, enabled, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by, version)
VALUES ('328058338325565444', '1', '328056809673723904', 5, '328058080426201088', 4, TRUE, '2026-06-24T06:26:42.886Z', '2026-06-24T06:26:42.886Z', '1', '1', FALSE, NULL, NULL, 0)
ON CONFLICT (id) DO NOTHING;

INSERT INTO bkg_week_schedule_template_item (id, tenant_id, week_schedule_template_id, weekday, day_schedule_template_id, sort_no, enabled, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by, version)
VALUES ('328058338325565445', '1', '328056809673723904', 6, '328058080426201088', 5, TRUE, '2026-06-24T06:26:42.886Z', '2026-06-24T06:26:42.886Z', '1', '1', FALSE, NULL, NULL, 0)
ON CONFLICT (id) DO NOTHING;

INSERT INTO bkg_week_schedule_template_item (id, tenant_id, week_schedule_template_id, weekday, day_schedule_template_id, sort_no, enabled, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by, version)
VALUES ('328058338325565446', '1', '328056809673723904', 7, '328058080426201088', 6, TRUE, '2026-06-24T06:26:42.886Z', '2026-06-24T06:26:42.886Z', '1', '1', FALSE, NULL, NULL, 0)
ON CONFLICT (id) DO NOTHING;

