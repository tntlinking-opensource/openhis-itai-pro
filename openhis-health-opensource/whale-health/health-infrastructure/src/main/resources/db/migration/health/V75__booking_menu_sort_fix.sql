-- V75：排班预约菜单种子修复（原 booking V10 排序 + V11 隐藏，6/26 flyway 整理时遗漏）

-- 权限：Booking.Schedule.View 拆为门诊排班 / 号源日历
UPDATE whale_permissions
SET name         = 'Booking.OutpatientSchedule.View',
    display_name = '门诊排班',
    description  = '菜单入口: BookingOutpatientSchedule',
    updated_at   = TIMESTAMP '2026-06-27 00:00:00+00'
WHERE name = 'Booking.Schedule.View'
  AND is_deleted = FALSE;

INSERT INTO whale_permissions (
    id, name, display_name, parent_name, description,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by
)
VALUES (
    '310900000000000012', 'Booking.SlotCalendar.View', '号源日历', 'Booking',
    '菜单入口: BookingSlotCalendar', NULL,
    TIMESTAMP '2026-06-27 00:00:00+00', TIMESTAMP '2026-06-27 00:00:00+00', 1, 1, FALSE, NULL, NULL
)
ON CONFLICT (id) DO UPDATE SET
    name          = EXCLUDED.name,
    display_name  = EXCLUDED.display_name,
    parent_name   = EXCLUDED.parent_name,
    description   = EXCLUDED.description,
    is_deleted    = FALSE,
    deleted_at    = NULL,
    deleted_by    = NULL,
    updated_at    = EXCLUDED.updated_at;

UPDATE whale_permissions
SET display_name = '排班设置',
    updated_at   = TIMESTAMP '2026-06-27 00:00:00+00'
WHERE name = 'Booking.Template.View'
  AND is_deleted = FALSE;

UPDATE whale_permissions
SET display_name = '排班预约',
    description  = 'whale-booking 模块入口权限分组',
    updated_at   = TIMESTAMP '2026-06-27 00:00:00+00'
WHERE name = 'Booking'
  AND is_deleted = FALSE;

-- 子菜单排序与权限对齐（V10）
UPDATE whale_menus SET display_name = '排班设置', sort_order = 1, is_hidden = FALSE, updated_at = TIMESTAMP '2026-06-27 00:00:00+00' WHERE id = '310900000000000112' AND code = 'BookingScheduleTemplate' AND is_deleted = FALSE;
UPDATE whale_menus SET display_name = '门诊排班', sort_order = 2, permission_name = 'Booking.OutpatientSchedule.View', is_hidden = FALSE, updated_at = TIMESTAMP '2026-06-27 00:00:00+00' WHERE id = '310900000000000101' AND code = 'BookingOutpatientSchedule' AND is_deleted = FALSE;
UPDATE whale_menus SET display_name = '号源日历', sort_order = 3, permission_name = 'Booking.SlotCalendar.View', is_hidden = FALSE, updated_at = TIMESTAMP '2026-06-27 00:00:00+00' WHERE id = '310900000000000102' AND code = 'BookingSlotCalendar' AND is_deleted = FALSE;
UPDATE whale_menus SET sort_order = 4, updated_at = TIMESTAMP '2026-06-27 00:00:00+00' WHERE id = '310900000000000103' AND code = 'BookingOutpatientRegister' AND is_deleted = FALSE;
UPDATE whale_menus SET sort_order = 5, updated_at = TIMESTAMP '2026-06-27 00:00:00+00' WHERE id = '310900000000000104' AND code = 'BookingQueueBoard' AND is_deleted = FALSE;
UPDATE whale_menus SET sort_order = 6, updated_at = TIMESTAMP '2026-06-27 00:00:00+00' WHERE id = '310900000000000110' AND code = 'BookingQueueDisplay' AND is_deleted = FALSE;
UPDATE whale_menus SET sort_order = 7, updated_at = TIMESTAMP '2026-06-27 00:00:00+00' WHERE id = '310900000000000106' AND code = 'BookingMedTech' AND is_deleted = FALSE;
UPDATE whale_menus SET sort_order = 8, updated_at = TIMESTAMP '2026-06-27 00:00:00+00' WHERE id = '310900000000000107' AND code = 'BookingBed' AND is_deleted = FALSE;
UPDATE whale_menus SET sort_order = 9, updated_at = TIMESTAMP '2026-06-27 00:00:00+00' WHERE id = '310900000000000108' AND code = 'BookingHealthCheckup' AND is_deleted = FALSE;
UPDATE whale_menus SET display_name = '预约策略', sort_order = 10, remark = 'BookingPolicyAppService · 挂号策略与停诊策略', updated_at = TIMESTAMP '2026-06-27 00:00:00+00' WHERE id = '310900000000000105' AND code = 'BookingPolicy' AND is_deleted = FALSE;
UPDATE whale_menus SET sort_order = 11, updated_at = TIMESTAMP '2026-06-27 00:00:00+00' WHERE id = '310900000000000109' AND code = 'BookingChannel' AND is_deleted = FALSE;

-- 一级目录改名；扩展菜单隐藏（V11）
UPDATE whale_menus
SET display_name = '排班预约',
    remark       = '门诊排班 / 号源 / 预约 / 候诊叫号 / 策略 / 模板',
    updated_at   = TIMESTAMP '2026-06-27 00:00:00+00'
WHERE id = '310900000000000100'
  AND code = 'BookingHub'
  AND is_deleted = FALSE;

UPDATE whale_menus
SET is_hidden  = TRUE,
    updated_at = TIMESTAMP '2026-06-27 00:00:00+00'
WHERE id IN (
    '310900000000000103',
    '310900000000000104',
    '310900000000000110',
    '310900000000000106',
    '310900000000000107',
    '310900000000000108',
    '310900000000000105',
    '310900000000000109'
)
  AND is_deleted = FALSE;

-- 授权：Schedule.View → OutpatientSchedule.View；补 SlotCalendar.View
UPDATE whale_permission_grants
SET permission_name = 'Booking.OutpatientSchedule.View',
    updated_at      = TIMESTAMP '2026-06-27 00:00:00+00'
WHERE permission_name = 'Booking.Schedule.View'
  AND is_deleted = FALSE;

INSERT INTO whale_permission_grants (
    id, permission_name, provider_name, provider_key,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by
)
SELECT 310800000000000037, 'Booking.SlotCalendar.View', 'R', '1', NULL,
       TIMESTAMP '2026-06-27 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL
WHERE NOT EXISTS (
    SELECT 1 FROM whale_permission_grants
    WHERE permission_name = 'Booking.SlotCalendar.View'
      AND provider_name = 'R'
      AND provider_key = '1'
      AND is_deleted = FALSE
);

-- 撤销扩展菜单权限授权（全角色/用户）
UPDATE whale_permission_grants
SET is_deleted = TRUE,
    deleted_at = TIMESTAMP '2026-06-27 00:00:00+00',
    updated_at = TIMESTAMP '2026-06-27 00:00:00+00'
WHERE permission_name IN (
    'Booking.Register.View',
    'Booking.Queue.View',
    'Booking.QueueDisplay.View',
    'Booking.MedTech.View',
    'Booking.Bed.View',
    'Booking.HealthCheckup.View',
    'Booking.Policy.View',
    'Booking.Channel.View'
)
  AND is_deleted = FALSE;
