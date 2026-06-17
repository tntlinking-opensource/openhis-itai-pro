-- =============================================================================
-- V6 · 菜单、权限与角色授权
-- 依赖 health 模块已存在的 whale_permissions / whale_menus / whale_permission_grants
-- 结构：顶级「统一预约」目录下集中 P0～P2 入口
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. Booking 权限定义
-- ---------------------------------------------------------------------------
INSERT INTO whale_permissions (
    id, name, display_name, parent_name, description,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by
)
VALUES
    -- P0 分组与入口
    ('310900000000000001', 'Booking', '统一预约', NULL,
     'whale-booking 模块入口权限分组', NULL,
     TIMESTAMP '2026-06-13 00:00:00+00', TIMESTAMP '2026-06-13 00:00:00+00', 1, 1, FALSE, NULL, NULL),
    ('310900000000000002', 'Booking.Schedule.View', '排班与号源', 'Booking',
     '门诊排班、号源日历', NULL,
     TIMESTAMP '2026-06-13 00:00:00+00', TIMESTAMP '2026-06-13 00:00:00+00', 1, 1, FALSE, NULL, NULL),
    ('310900000000000003', 'Booking.Register.View', '门诊预约', 'Booking',
     '门诊预约登记', NULL,
     TIMESTAMP '2026-06-13 00:00:00+00', TIMESTAMP '2026-06-13 00:00:00+00', 1, 1, FALSE, NULL, NULL),
    ('310900000000000004', 'Booking.Queue.View', '候诊叫号', 'Booking',
     '候诊叫号台', NULL,
     TIMESTAMP '2026-06-13 00:00:00+00', TIMESTAMP '2026-06-13 00:00:00+00', 1, 1, FALSE, NULL, NULL),
    -- P1/P2 扩展
    ('310900000000000005', 'Booking.Policy.View', '预约策略', 'Booking',
     '放号策略与院点挂号策略', NULL,
     TIMESTAMP '2026-06-13 00:00:00+00', TIMESTAMP '2026-06-13 00:00:00+00', 1, 1, FALSE, NULL, NULL),
    ('310900000000000006', 'Booking.MedTech.View', '医技预约', 'Booking',
     '医技预约登记', NULL,
     TIMESTAMP '2026-06-13 00:00:00+00', TIMESTAMP '2026-06-13 00:00:00+00', 1, 1, FALSE, NULL, NULL),
    ('310900000000000007', 'Booking.Bed.View', '床位预约', 'Booking',
     '床位预留日历', NULL,
     TIMESTAMP '2026-06-13 00:00:00+00', TIMESTAMP '2026-06-13 00:00:00+00', 1, 1, FALSE, NULL, NULL),
    ('310900000000000008', 'Booking.HealthCheckup.View', '体检预约', 'Booking',
     '团检与个检预约', NULL,
     TIMESTAMP '2026-06-13 00:00:00+00', TIMESTAMP '2026-06-13 00:00:00+00', 1, 1, FALSE, NULL, NULL),
    ('310900000000000009', 'Booking.Channel.View', '渠道配置', 'Booking',
     '预约渠道与配额', NULL,
     TIMESTAMP '2026-06-13 00:00:00+00', TIMESTAMP '2026-06-13 00:00:00+00', 1, 1, FALSE, NULL, NULL),
    ('310900000000000010', 'Booking.QueueDisplay.View', '叫号大屏', 'Booking',
     '候诊叫号大屏', NULL,
     TIMESTAMP '2026-06-13 00:00:00+00', TIMESTAMP '2026-06-13 00:00:00+00', 1, 1, FALSE, NULL, NULL),
    -- 排班模板 T1/T2/TW
    ('310900000000000011', 'Booking.Template.View', '排班模板', 'Booking',
     'T1/T2/TW 排班模板维护', NULL,
     TIMESTAMP '2026-06-14 00:00:00+00', TIMESTAMP '2026-06-14 00:00:00+00', 1, 1, FALSE, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    display_name = EXCLUDED.display_name,
    parent_name = EXCLUDED.parent_name,
    description = EXCLUDED.description,
    is_deleted = FALSE,
    deleted_at = NULL,
    deleted_by = NULL,
    updated_at = EXCLUDED.updated_at;

-- ---------------------------------------------------------------------------
-- 2. 菜单（upsert）
-- ---------------------------------------------------------------------------
INSERT INTO whale_menus (
    id, code, name, display_name, icon, path, component, permission_name, parent_id,
    sort_order, is_enabled, is_hidden, is_external, external_url, menu_type, remark,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by, keep_alive
)
VALUES
    -- 顶级目录
    ('310900000000000100', 'BookingHub', 'BookingHub', '统一预约', 'lucide:calendar-range',
     '/booking', '', NULL, NULL, 8, TRUE, FALSE, FALSE, '', 'directory',
     '门诊排班 / 号源 / 预约 / 候诊叫号 / 策略 / 模板', NULL,
     TIMESTAMP '2026-06-13 00:00:00+00', TIMESTAMP '2026-06-13 00:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE),

    -- P0
    ('310900000000000101', 'BookingOutpatientSchedule', 'BookingOutpatientSchedule', '门诊排班', 'lucide:calendar-clock',
     '/booking/outpatient-schedule', '/booking/outpatient-schedule/index', 'Booking.Schedule.View', '310900000000000100', 1,
     TRUE, FALSE, FALSE, '', 'menu', 'OutpatientScheduleAppService', NULL,
     TIMESTAMP '2026-06-13 00:00:00+00', TIMESTAMP '2026-06-13 00:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE),
    ('310900000000000102', 'BookingSlotCalendar', 'BookingSlotCalendar', '号源日历', 'lucide:calendar-days',
     '/booking/slot-calendar', '/booking/slot-calendar/index', 'Booking.Schedule.View', '310900000000000100', 2,
     TRUE, FALSE, FALSE, '', 'menu', 'SlotManageAppService', NULL,
     TIMESTAMP '2026-06-13 00:00:00+00', TIMESTAMP '2026-06-13 00:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE),
    ('310900000000000103', 'BookingOutpatientRegister', 'BookingOutpatientRegister', '门诊预约', 'lucide:calendar-check',
     '/booking/outpatient-register', '/booking/outpatient-register/index', 'Booking.Register.View', '310900000000000100', 3,
     TRUE, FALSE, FALSE, '', 'menu', 'BookingRegisterAppService', NULL,
     TIMESTAMP '2026-06-13 00:00:00+00', TIMESTAMP '2026-06-13 00:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE),
    ('310900000000000104', 'BookingQueueBoard', 'BookingQueueBoard', '候诊叫号', 'lucide:megaphone',
     '/booking/queue-board', '/booking/queue-board/index', 'Booking.Queue.View', '310900000000000100', 4,
     TRUE, FALSE, FALSE, '', 'menu', 'QueueBoardAppService', NULL,
     TIMESTAMP '2026-06-13 00:00:00+00', TIMESTAMP '2026-06-13 00:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE),

    -- P1/P2
    ('310900000000000105', 'BookingPolicy', 'BookingPolicy', '放号策略', 'lucide:settings-2',
     '/booking/policy', '/booking/policy/index', 'Booking.Policy.View', '310900000000000100', 5,
     TRUE, FALSE, FALSE, '', 'menu', 'BookingPolicyAppService', NULL,
     TIMESTAMP '2026-06-13 00:00:00+00', TIMESTAMP '2026-06-13 00:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE),
    ('310900000000000106', 'BookingMedTech', 'BookingMedTech', '医技预约', 'lucide:stethoscope',
     '/booking/med-tech', '/booking/med-tech/index', 'Booking.MedTech.View', '310900000000000100', 6,
     TRUE, FALSE, FALSE, '', 'menu', 'MedTechBookingAppService', NULL,
     TIMESTAMP '2026-06-13 00:00:00+00', TIMESTAMP '2026-06-13 00:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE),
    ('310900000000000107', 'BookingBed', 'BookingBed', '床位预约', 'lucide:bed',
     '/booking/bed', '/booking/bed/index', 'Booking.Bed.View', '310900000000000100', 7,
     TRUE, FALSE, FALSE, '', 'menu', 'BedBookingAppService', NULL,
     TIMESTAMP '2026-06-13 00:00:00+00', TIMESTAMP '2026-06-13 00:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE),
    ('310900000000000108', 'BookingHealthCheckup', 'BookingHealthCheckup', '体检预约', 'lucide:heart-pulse',
     '/booking/health-checkup', '/booking/health-checkup/index', 'Booking.HealthCheckup.View', '310900000000000100', 8,
     TRUE, FALSE, FALSE, '', 'menu', 'HealthCheckupBookingAppService', NULL,
     TIMESTAMP '2026-06-13 00:00:00+00', TIMESTAMP '2026-06-13 00:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE),
    ('310900000000000109', 'BookingChannel', 'BookingChannel', '渠道配置', 'lucide:share-2',
     '/booking/channel', '/booking/channel/index', 'Booking.Channel.View', '310900000000000100', 9,
     TRUE, FALSE, FALSE, '', 'menu', 'ChannelBookingAppService', NULL,
     TIMESTAMP '2026-06-13 00:00:00+00', TIMESTAMP '2026-06-13 00:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE),
    ('310900000000000110', 'BookingQueueDisplay', 'BookingQueueDisplay', '叫号大屏', 'lucide:monitor',
     '/booking/queue-display', '/booking/queue-display/index', 'Booking.QueueDisplay.View', '310900000000000100', 10,
     TRUE, FALSE, FALSE, '', 'menu', 'Queue display fullscreen', NULL,
     TIMESTAMP '2026-06-13 00:00:00+00', TIMESTAMP '2026-06-13 00:00:00+00', 1, 1, FALSE, NULL, NULL, FALSE),

    -- 排班模板
    ('310900000000000112', 'BookingScheduleTemplate', 'BookingScheduleTemplate', '排班模板', 'lucide:layout-template',
     '/booking/schedule-template', '/booking/schedule-template/index', 'Booking.Template.View', '310900000000000100', 11,
     TRUE, FALSE, FALSE, '', 'menu', 'ScheduleTemplateAppService T1/T2/TW', NULL,
     TIMESTAMP '2026-06-14 00:00:00+00', TIMESTAMP '2026-06-14 00:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE)
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

-- ---------------------------------------------------------------------------
-- 3. 管理员角色（R / 1）授权
-- ---------------------------------------------------------------------------
INSERT INTO whale_permission_grants (
    id, permission_name, provider_name, provider_key,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by
)
VALUES
    ('310900000000000011', 'Booking.Schedule.View', 'R', '1', NULL, TIMESTAMP '2026-06-13 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('310900000000000012', 'Booking.Register.View', 'R', '1', NULL, TIMESTAMP '2026-06-13 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('310900000000000013', 'Booking.Queue.View', 'R', '1', NULL, TIMESTAMP '2026-06-13 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('310900000000000014', 'Booking', 'R', '1', NULL, TIMESTAMP '2026-06-13 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('310900000000000015', 'Booking.Policy.View', 'R', '1', NULL, TIMESTAMP '2026-06-13 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('310900000000000016', 'Booking.MedTech.View', 'R', '1', NULL, TIMESTAMP '2026-06-13 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('310900000000000017', 'Booking.Bed.View', 'R', '1', NULL, TIMESTAMP '2026-06-13 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('310900000000000018', 'Booking.HealthCheckup.View', 'R', '1', NULL, TIMESTAMP '2026-06-13 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('310900000000000019', 'Booking.Channel.View', 'R', '1', NULL, TIMESTAMP '2026-06-13 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('310900000000000020', 'Booking.QueueDisplay.View', 'R', '1', NULL, TIMESTAMP '2026-06-13 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('310900000000000021', 'Booking.Template.View', 'R', '1', NULL, TIMESTAMP '2026-06-14 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL)
ON CONFLICT (permission_name, provider_name, provider_key) DO UPDATE SET
    is_deleted = FALSE,
    deleted_at = NULL,
    deleted_by = NULL,
    updated_at = TIMESTAMP '2026-06-14 00:00:00+00';
