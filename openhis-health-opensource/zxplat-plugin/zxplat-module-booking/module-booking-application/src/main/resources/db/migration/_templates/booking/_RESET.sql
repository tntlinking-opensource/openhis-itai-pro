-- =============================================================================
-- Booking 模块清库脚本（手动执行，非 Flyway 版本）
-- 用途：删除 booking 业务表 + 重置 flyway_schema_history_booking，便于 V1→V7 重跑
-- 注意：勿动 flyway_schema_history（health 主迁移历史）
-- =============================================================================

BEGIN;

-- 1. Flyway booking 历史
DELETE FROM flyway_schema_history_booking;

-- 2. bkg_* 扩展表（子表优先）
DROP TABLE IF EXISTS bkg_waitlist_entry CASCADE;
DROP TABLE IF EXISTS bkg_stop_clinic_slot_freeze CASCADE;
DROP TABLE IF EXISTS bkg_week_schedule_template_item CASCADE;
DROP TABLE IF EXISTS bkg_week_schedule_template CASCADE;
DROP TABLE IF EXISTS bkg_calendar_exception CASCADE;
DROP TABLE IF EXISTS bkg_schedule_template CASCADE;
DROP TABLE IF EXISTS bkg_slot_provision_template CASCADE;
DROP TABLE IF EXISTS bkg_health_check_booking_ext CASCADE;
DROP TABLE IF EXISTS bkg_group_checkup_roster CASCADE;
DROP TABLE IF EXISTS bkg_group_checkup_batch CASCADE;
DROP TABLE IF EXISTS bkg_group_checkup_org CASCADE;
DROP TABLE IF EXISTS bkg_health_check_package_item CASCADE;
DROP TABLE IF EXISTS bkg_health_check_package CASCADE;
DROP TABLE IF EXISTS bkg_bed_reservation_ext CASCADE;
DROP TABLE IF EXISTS bkg_diagnostic_resource_map CASCADE;
DROP TABLE IF EXISTS bkg_resource_profile CASCADE;
DROP TABLE IF EXISTS bkg_queue_display_config CASCADE;
DROP TABLE IF EXISTS bkg_channel_config CASCADE;
DROP TABLE IF EXISTS bkg_schedule_change_policy CASCADE;
DROP TABLE IF EXISTS bkg_registration_policy CASCADE;
DROP TABLE IF EXISTS bkg_release_policy CASCADE;

-- 3. wkf_* 运行时表（子表优先）
DROP TABLE IF EXISTS wkf_slot_identifier CASCADE;
DROP TABLE IF EXISTS wkf_slot CASCADE;
DROP TABLE IF EXISTS wkf_schedule_identifier CASCADE;
DROP TABLE IF EXISTS wkf_schedule_actor CASCADE;
DROP TABLE IF EXISTS wkf_schedule CASCADE;
DROP TABLE IF EXISTS wkf_appointment_queue CASCADE;

-- 4. （可选）清理 V6 写入的菜单/权限 — 若需完全重建菜单，取消下方注释
-- DELETE FROM zxplat_permission_grants WHERE permission_name LIKE 'Booking%';
-- UPDATE zxplat_menus SET is_deleted = TRUE, deleted_at = NOW() WHERE id >= 310900000000000100 AND id < 310900000000000200;
-- UPDATE zxplat_permissions SET is_deleted = TRUE, deleted_at = NOW() WHERE id >= 310900000000000001 AND id <= 310900000000000011;

COMMIT;

-- 执行后启动应用（with-booking），Flyway 将依次应用 V1 → V7。
-- 若整库 DROP 重建：须先跑完 health 迁移（含 zxplat_menus），再跑 booking。
