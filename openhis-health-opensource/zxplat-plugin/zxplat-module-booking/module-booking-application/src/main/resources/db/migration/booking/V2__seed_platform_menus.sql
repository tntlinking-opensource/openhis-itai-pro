-- booking ModularFlyway V2 · 体检号源相关菜单最终态
-- history: flyway_schema_history_booking
-- 说明：Booking 主菜单仍在 health V10；本文件仅补模块增量菜单，并把日历显示名写成终态（原 V4/V5）。

-- 窗口体检预约（原 V4）
INSERT INTO zxplat_permissions (
    id, name, display_name, parent_name, description,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by
)
VALUES
    ('310900000000000013', 'Booking.HealthCheckupRegister.View', '窗口体检预约', 'Booking',
     '菜单入口: BookingHealthCheckupRegister · 体检号源窗口占号', NULL,
     TIMESTAMP '2026-08-30 00:00:00+00', TIMESTAMP '2026-08-30 00:00:00+00', 1, 1, FALSE, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    display_name = EXCLUDED.display_name,
    parent_name = EXCLUDED.parent_name,
    description = EXCLUDED.description,
    is_deleted = FALSE,
    deleted_at = NULL,
    deleted_by = NULL,
    updated_at = EXCLUDED.updated_at;

INSERT INTO zxplat_menus (
    id, code, name, display_name, icon, path, component, permission_name, parent_id,
    sort_order, is_enabled, is_hidden, is_external, external_url, menu_type, remark,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by, keep_alive
)
VALUES
    ('310900000000000113', 'BookingHealthCheckupRegister', 'BookingHealthCheckupRegister', '窗口体检预约',
     'lucide:clipboard-plus',
     '/booking/health-checkup-register', '/booking/health-checkup-register/index',
     'Booking.HealthCheckupRegister.View', '310900000000000100', 12,
     TRUE, FALSE, FALSE, '', 'menu',
     'HealthCheckupSlotOccupyAppService · 窗口体检预约', NULL,
     TIMESTAMP '2026-08-30 00:00:00+00', TIMESTAMP '2026-08-30 00:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE)
ON CONFLICT (id) DO UPDATE SET
    code = EXCLUDED.code,
    name = EXCLUDED.name,
    display_name = EXCLUDED.display_name,
    icon = EXCLUDED.icon,
    path = EXCLUDED.path,
    component = EXCLUDED.component,
    permission_name = EXCLUDED.permission_name,
    parent_id = EXCLUDED.parent_id,
    sort_order = EXCLUDED.sort_order,
    is_enabled = EXCLUDED.is_enabled,
    is_hidden = EXCLUDED.is_hidden,
    remark = EXCLUDED.remark,
    is_deleted = FALSE,
    deleted_at = NULL,
    deleted_by = NULL,
    updated_at = EXCLUDED.updated_at;

INSERT INTO zxplat_permission_grants (
    id, permission_name, provider_name, provider_key,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by
)
VALUES
    ('310900000000000213', 'Booking.HealthCheckupRegister.View', 'R', '1', NULL,
     TIMESTAMP '2026-08-30 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL)
ON CONFLICT (permission_name, provider_name, provider_key) DO UPDATE SET
    is_deleted = FALSE,
    deleted_at = NULL,
    deleted_by = NULL,
    updated_at = TIMESTAMP '2026-08-30 00:00:00+00';

-- 体检号源日历显示名终态（原 V5；health V10 初始 INSERT 亦已同步）
INSERT INTO zxplat_permissions (
    id, name, display_name, parent_name, description,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by
)
VALUES
    ('310900000000000008', 'Booking.HealthCheckup.View', '体检号源日历', 'Booking',
     '菜单入口: BookingHealthCheckup · 体检号源日历', NULL,
     TIMESTAMP '2026-08-30 00:00:00+00', TIMESTAMP '2026-08-30 00:00:00+00', 1, 1, FALSE, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET
    display_name = EXCLUDED.display_name,
    description = EXCLUDED.description,
    is_deleted = FALSE,
    deleted_at = NULL,
    deleted_by = NULL,
    updated_at = EXCLUDED.updated_at;

INSERT INTO zxplat_menus (
    id, code, name, display_name, icon, path, component, permission_name, parent_id,
    sort_order, is_enabled, is_hidden, is_external, external_url, menu_type, remark,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by, keep_alive
)
VALUES
    ('310900000000000108', 'BookingHealthCheckup', 'BookingHealthCheckup', '体检号源日历', 'lucide:heart-pulse',
     '/booking/health-checkup', '/booking/health-checkup/index', 'Booking.HealthCheckup.View', '310900000000000100', 9,
     TRUE, TRUE, FALSE, '', 'menu', 'HealthCheckupSlotCalendarAppService · 体检号源日历', NULL,
     TIMESTAMP '2026-08-30 00:00:00+00', TIMESTAMP '2026-08-30 00:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE)
ON CONFLICT (id) DO UPDATE SET
    display_name = EXCLUDED.display_name,
    remark = EXCLUDED.remark,
    is_deleted = FALSE,
    deleted_at = NULL,
    deleted_by = NULL,
    updated_at = EXCLUDED.updated_at;
