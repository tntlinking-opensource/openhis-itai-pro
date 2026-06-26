-- =============================================================================
-- 建库脚本（Flyway 迁移前，在 psql 中连接 postgres 库后手动执行一次）
-- 库名须与 application.yml 中 spring.datasource.url 一致（默认 whale）
-- =============================================================================
-- CREATE DATABASE whale
--     ENCODING 'UTF8'
--     TEMPLATE template0;

-- ========== whale_users ==========
CREATE TABLE IF NOT EXISTS whale_users (
    id BIGINT PRIMARY KEY,
    user_name VARCHAR(256) NOT NULL,
    email VARCHAR(256),
    phone_number VARCHAR(32),
    password_hash VARCHAR(256) NOT NULL,
    name VARCHAR(64),
    surname VARCHAR(64),
    is_active BOOLEAN NOT NULL DEFAULT true,
    is_locked_out BOOLEAN NOT NULL DEFAULT false,
    lockout_end TIMESTAMP,
    access_failed_count INTEGER NOT NULL DEFAULT 0,
    extra_properties JSONB,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT false,
    deleted_at TIMESTAMP,
    deleted_by BIGINT,
    ukey_id VARCHAR(128),
    ukey_src_id VARCHAR(128)
);

CREATE INDEX IF NOT EXISTS idx_users_email ON whale_users(email);

CREATE UNIQUE INDEX IF NOT EXISTS idx_users_user_name_unique ON whale_users(user_name) WHERE is_deleted = false;

CREATE UNIQUE INDEX IF NOT EXISTS idx_users_email_unique ON whale_users(email) WHERE email IS NOT NULL AND is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_whale_users_is_deleted ON whale_users(is_deleted);

CREATE INDEX IF NOT EXISTS idx_whale_users_is_active ON whale_users(is_active) WHERE is_active = true;

CREATE INDEX IF NOT EXISTS idx_whale_users_is_locked_out ON whale_users(is_locked_out) WHERE is_locked_out = true;

CREATE INDEX IF NOT EXISTS idx_whale_users_created_at ON whale_users(created_at DESC);

CREATE INDEX IF NOT EXISTS idx_users_ukey_id ON whale_users(ukey_id);

CREATE INDEX IF NOT EXISTS idx_users_ukey_src_id ON whale_users(ukey_src_id);
COMMENT ON TABLE whale_users IS '系统用户';
COMMENT ON COLUMN whale_users.id IS '主键';
COMMENT ON COLUMN whale_users.user_name IS '登录用户名';
COMMENT ON COLUMN whale_users.email IS '邮箱';
COMMENT ON COLUMN whale_users.phone_number IS '手机号';
COMMENT ON COLUMN whale_users.password_hash IS '密码哈希';
COMMENT ON COLUMN whale_users.name IS '名';
COMMENT ON COLUMN whale_users.surname IS '姓';
COMMENT ON COLUMN whale_users.is_active IS '是否启用';
COMMENT ON COLUMN whale_users.is_locked_out IS '是否锁定';
COMMENT ON COLUMN whale_users.lockout_end IS '锁定结束时间';
COMMENT ON COLUMN whale_users.access_failed_count IS '登录失败次数';
COMMENT ON COLUMN whale_users.extra_properties IS '扩展属性 JSON';
COMMENT ON COLUMN whale_users.created_at IS '创建时间';
COMMENT ON COLUMN whale_users.updated_at IS '最后更新时间';
COMMENT ON COLUMN whale_users.created_by IS '创建人';
COMMENT ON COLUMN whale_users.updated_by IS '最后更新人';
COMMENT ON COLUMN whale_users.is_deleted IS '软删标记';
COMMENT ON COLUMN whale_users.deleted_at IS '删除时间';
COMMENT ON COLUMN whale_users.deleted_by IS '删除人';
COMMENT ON COLUMN whale_users.ukey_id IS 'UKey 标识';
COMMENT ON COLUMN whale_users.ukey_src_id IS 'UKey 来源标识';

-- seed: whale_users
INSERT INTO "whale_users" ("id", "user_name", "email", "phone_number", "password_hash", "name", "surname", "is_active", "is_locked_out", "lockout_end", "access_failed_count", "extra_properties", "created_at", "updated_at", "created_by", "updated_by", "is_deleted", "deleted_at", "deleted_by", "ukey_id", "ukey_src_id")
VALUES
('1', 'admin', 'admin@example.com', NULL, '$2a$10$7H11xZICXxBlwxh2/hBRzuB0TC7wEduR4Fs2x4s7yFOn4fjrOIMuK', 'admin', NULL, true, false, NULL, 0, NULL, '2026-05-02 22:07:04.503+00', '2026-06-10 08:49:49.813+00', NULL, NULL, false, NULL, NULL, NULL, NULL),
('322932010666430464', 'yaofang', NULL, NULL, '$2a$10$fgewpcSdDLz1V8hz5mGJs.VI/AQSxgh15vCqQXv3uExk3MQQXeNq6', '药房', NULL, true, false, NULL, 0, NULL, '2026-06-10 02:56:31.129+00', '2026-06-10 02:56:31.129+00', '1', '1', false, NULL, NULL, NULL, NULL),
('322932096091820032', 'kuguan', NULL, NULL, '$2a$10$xmtLIpu./wIOtQvtOCasN.CxUuSv0Z27KYkoK/KsmR0862gtZd2ne', '库管', NULL, true, false, NULL, 0, NULL, '2026-06-10 02:56:51.496+00', '2026-06-10 02:57:30.134+00', '1', '1', false, NULL, NULL, NULL, NULL),
('322932211309350912', 'shoufei', NULL, NULL, '$2a$10$aUBhg.2LPcQaoaZJcqQSZusUsUxb52Sr9oPVhfE8.M6c40RPNs4di', '收费', NULL, true, false, NULL, 0, NULL, '2026-06-10 02:57:18.966+00', '2026-06-10 02:57:18.966+00', '1', '1', false, NULL, NULL, NULL, NULL),
('322935698131259392', 'zyyisheng', NULL, NULL, '$2a$10$WLfRePjxQHwBcz5v/qpsPejS3I.bZU1PUKocrAL5rzn.fWGoDrvey', '住院医生', NULL, true, false, NULL, 0, NULL, '2026-06-10 03:11:10.289+00', '2026-06-10 03:11:10.289+00', '1', '1', false, NULL, NULL, NULL, NULL),
('322963602154721280', 'yisheng', NULL, NULL, '$2a$10$40qkQYyXUxyemhox33jLMOaNht/hJnwdpA8ERsbdKN2lbXWUV0vBK', '门诊医生', NULL, true, false, NULL, 0, NULL, '2026-06-10 05:02:03.127+00', '2026-06-10 05:02:03.127+00', '1', '1', false, NULL, NULL, NULL, NULL),
('322965022853566464', 'hushi', NULL, NULL, '$2a$10$L0qwEnhMmYg/UfpMnNGA1eaEy5NhM1LogeA8ehGQBSiO86ebOpsBm', '护士', NULL, true, false, NULL, 0, NULL, '2026-06-10 05:07:41.848+00', '2026-06-10 05:08:11.113+00', '1', '1', false, NULL, NULL, NULL, NULL),
('323022664197214208', 'xinxike', NULL, NULL, '$2a$10$yUUyPkFp8YrLgFHB3ydovuoBwaqe6mF6dcs7PMpyImY4obRTFMSMS', '信息科', NULL, true, false, NULL, 0, NULL, '2026-06-10 08:56:44.615+00', '2026-06-10 08:56:44.615+00', '1', '1', false, NULL, NULL, NULL, NULL)
ON CONFLICT ("id") DO NOTHING;

-- ========== whale_roles ==========
CREATE TABLE IF NOT EXISTS whale_roles (
    id BIGINT PRIMARY KEY,
    name VARCHAR(256) NOT NULL UNIQUE,
    display_name VARCHAR(256),
    code VARCHAR(256),
    description TEXT,
    is_default BOOLEAN NOT NULL DEFAULT false,
    is_static BOOLEAN NOT NULL DEFAULT false,
    tenant_id BIGINT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT false,
    deleted_at TIMESTAMP,
    deleted_by BIGINT
);

CREATE INDEX IF NOT EXISTS idx_whale_roles_code ON whale_roles(code);

CREATE INDEX IF NOT EXISTS idx_whale_roles_tenant_id ON whale_roles(tenant_id);

CREATE INDEX IF NOT EXISTS idx_whale_roles_is_deleted ON whale_roles(is_deleted);

CREATE INDEX IF NOT EXISTS idx_whale_roles_is_default ON whale_roles(is_default) WHERE is_default = true;

CREATE INDEX IF NOT EXISTS idx_whale_roles_is_static ON whale_roles(is_static) WHERE is_static = true;

CREATE INDEX IF NOT EXISTS idx_whale_roles_created_at ON whale_roles(created_at DESC);



-- seed: whale_roles
INSERT INTO "whale_roles" ("id", "name", "display_name", "code", "description", "is_default", "is_static", "tenant_id", "created_at", "updated_at", "created_by", "updated_by", "is_deleted", "deleted_at", "deleted_by")
VALUES
('1', 'admin', '系统管理员', 'ROLE_ADMIN', '系统管理员，拥有所有权限', false, true, NULL, '2026-05-02 22:07:04.508+00', NULL, NULL, NULL, false, NULL, NULL),
('10', 'doctor', '医生', 'ROLE_DOCTOR', '医生角色，负责诊疗相关工作', true, true, NULL, '2026-06-09 12:07:51.267+00', '2026-06-09 12:18:57.404+00', NULL, NULL, false, NULL, NULL),
('11', 'nurse', '护士', 'ROLE_NURSE', '护士角色，负责护理相关工作', true, true, NULL, '2026-06-09 12:07:51.267+00', '2026-06-09 12:18:57.404+00', NULL, NULL, false, NULL, NULL),
('12', 'pharmacist', '药师', 'ROLE_PHARMACIST', '药师角色，负责药房药品管理', true, true, NULL, '2026-06-09 12:07:51.267+00', '2026-06-09 12:18:57.404+00', NULL, NULL, false, NULL, NULL),
('13', 'warehouse_keeper', '库管', 'ROLE_WAREHOUSE', '库管角色，负责库存物资管理', true, true, NULL, '2026-06-09 12:07:51.267+00', '2026-06-09 12:18:57.404+00', NULL, NULL, false, NULL, NULL),
('14', 'accountant', '财务', 'ROLE_ACCOUNTANT', '财务角色，负责财务收费管理', true, true, NULL, '2026-06-09 12:07:51.267+00', '2026-06-09 12:18:57.404+00', NULL, NULL, false, NULL, NULL),
('323022177255297024', 'custom_role_6da58f2e', '信息科', 'ROLE_CUSTOM_6DA58F2E', '', true, true, '1', '2026-06-10 08:54:48.519+00', '2026-06-10 08:54:48.519+00', '1', '1', false, NULL, NULL)
ON CONFLICT ("id") DO NOTHING;

-- ========== whale_user_roles ==========
CREATE TABLE IF NOT EXISTS whale_user_roles (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    tenant_id BIGINT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT false,
    deleted_at TIMESTAMP,
    deleted_by BIGINT,
    UNIQUE(user_id, role_id)
);

CREATE INDEX IF NOT EXISTS idx_whale_user_roles_role_id ON whale_user_roles(role_id);

CREATE INDEX IF NOT EXISTS idx_whale_user_roles_tenant_id ON whale_user_roles(tenant_id);

CREATE INDEX IF NOT EXISTS idx_whale_user_roles_is_deleted ON whale_user_roles(is_deleted);



-- seed: whale_user_roles
INSERT INTO "whale_user_roles" ("id", "user_id", "role_id", "tenant_id", "created_at", "updated_at", "created_by", "updated_by", "is_deleted", "deleted_at", "deleted_by")
VALUES
('1', '1', '1', NULL, '2026-05-02 22:07:04.512+00', NULL, NULL, NULL, false, NULL, NULL),
('322932010733539328', '322932010666430464', '12', '1', '2026-06-10 02:56:31.145+00', '2026-06-10 02:56:31.145+00', '1', '1', false, NULL, NULL),
('322932096154734592', '322932096091820032', '13', '1', '2026-06-10 02:56:51.511+00', '2026-06-10 02:56:51.511+00', '1', '1', false, NULL, NULL),
('322932211372265472', '322932211309350912', '14', '1', '2026-06-10 02:57:18.981+00', '2026-06-10 02:57:18.981+00', '1', '1', false, NULL, NULL),
('322935698261282816', '322935698131259392', '10', '1', '2026-06-10 03:11:10.320+00', '2026-06-10 03:11:10.320+00', '1', '1', false, NULL, NULL),
('322963602620289024', '322963602154721280', '10', '1', '2026-06-10 05:02:03.238+00', '2026-06-10 05:02:03.238+00', '1', '1', false, NULL, NULL),
('322965022908092416', '322965022853566464', '11', '1', '2026-06-10 05:07:41.861+00', '2026-06-10 05:07:41.861+00', '1', '1', false, NULL, NULL),
('322965878780989440', '1', '14', '1', '2026-06-10 05:11:05.917+00', '2026-06-10 05:11:05.917+00', '1', '1', false, NULL, NULL),
('322965878881652736', '1', '10', '1', '2026-06-10 05:11:05.941+00', '2026-06-10 05:11:05.941+00', '1', '1', false, NULL, NULL),
('322965878919401472', '1', '13', '1', '2026-06-10 05:11:05.950+00', '2026-06-10 05:11:05.950+00', '1', '1', false, NULL, NULL),
('322965878990704640', '1', '12', '1', '2026-06-10 05:11:05.967+00', '2026-06-10 05:11:05.967+00', '1', '1', false, NULL, NULL),
('322965879020064768', '1', '11', '1', '2026-06-10 05:11:05.974+00', '2026-06-10 05:11:05.974+00', '1', '1', false, NULL, NULL),
('323021900313792512', '322965022853566464', '13', '1', '2026-06-10 08:53:42.491+00', '2026-06-10 08:53:42.491+00', '1', '1', false, NULL, NULL),
('323022664272711680', '323022664197214208', '323022177255297024', '1', '2026-06-10 08:56:44.633+00', '2026-06-10 08:56:44.633+00', '1', '1', false, NULL, NULL)
ON CONFLICT ("id") DO NOTHING;

-- ========== whale_permissions ==========
CREATE TABLE IF NOT EXISTS whale_permissions (
    id BIGINT PRIMARY KEY,
    name VARCHAR(256) NOT NULL UNIQUE,
    display_name VARCHAR(256),
    parent_name VARCHAR(256),
    description TEXT,
    tenant_id BIGINT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT false,
    deleted_at TIMESTAMP,
    deleted_by BIGINT
);

CREATE INDEX IF NOT EXISTS idx_whale_permissions_parent_name ON whale_permissions(parent_name);

CREATE INDEX IF NOT EXISTS idx_whale_permissions_tenant_id ON whale_permissions(tenant_id);

CREATE INDEX IF NOT EXISTS idx_whale_permissions_is_deleted ON whale_permissions(is_deleted);



-- seed: whale_permissions
INSERT INTO "whale_permissions" ("id", "name", "display_name", "parent_name", "description", "tenant_id", "created_at", "updated_at", "created_by", "updated_by", "is_deleted", "deleted_at", "deleted_by")
VALUES
('1', 'Navigation', '导航菜单', NULL, '由业务菜单自动生成的入口权限分组', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('2', 'Navigation.UserManagement', '用户管理', 'Navigation', '菜单入口: user_management', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('3', 'Navigation.RoleManagement', '角色管理', 'Navigation', '菜单入口: role_management', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('4', 'Navigation.PermissionManagement', '权限管理', 'Navigation', '菜单入口: permission_management', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('5', 'Navigation.TenantManagement', '租户管理', 'Navigation', '菜单入口: tenant_management', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('6', 'Navigation.MenuManagement', '菜单管理', 'Navigation', '菜单入口: menu_management', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('7', 'Navigation.SettingManagement', '设置管理', 'Navigation', '菜单入口: setting_management', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('8', 'Navigation.CodesystemManagement', '编码管理', 'Navigation', '菜单入口: codesystem_management', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('9', 'Navigation.BodystructureManagement', '部位管理', 'Navigation', '菜单入口: bodystructure_management', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('10', 'Navigation.WarehouseManagement', '库房药房', 'Navigation', '菜单入口: warehouse_management', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('11', 'Navigation.WardLocationManagement', '病区病房', 'Navigation', '菜单入口: ward_location_management', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('12', 'Navigation.PatientManagement', '患者管理', 'Navigation', '菜单入口: patient_management', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('14', 'Navigation.DeptManagement', '科室管理', 'Navigation', '菜单入口: dept_management', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('15', 'Navigation.HealthcareServiceManagement', '挂号类型管理', 'Navigation', '菜单入口: healthcare_service_management', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('16', 'Navigation.ContractManagement', '费用性质管理', 'Navigation', '菜单入口: contract_management', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('17', 'Navigation.Analytics', '概览', 'Navigation', '菜单入口: Analytics', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('18', 'Navigation.Frequency', '频次管理', 'Navigation', '菜单入口: Frequency', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('19', 'Navigation.Supplier', '供应商管理', 'Navigation', '菜单入口: Supplier', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('20', 'Navigation.Medicine', '药品管理', 'Navigation', '菜单入口: Medicine', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('21', 'Navigation.Device', '器材管理', 'Navigation', '菜单入口: Device', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('22', 'Navigation.Diagnosistreatment', '诊疗管理', 'Navigation', '菜单入口: Diagnosistreatment', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('23', 'Navigation.Disease', '诊断', 'Navigation', '菜单入口: Disease', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('25', 'Navigation.Outpatientnursestation', '门诊护士站', 'Navigation', '菜单入口: OutpatientNurseStation', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('26', 'Navigation.Inpatientnursestation', '住院护士站', 'Navigation', '菜单入口: InpatientNurseStation', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('27', 'Navigation.Reportfinance', '财务报表', 'Navigation', '菜单入口: ReportFinance', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('28', 'Navigation.Chargeopregister', '门诊挂号', 'Navigation', '菜单入口: ChargeOpRegister', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('29', 'Navigation.Chargeopcharge', '门诊收费', 'Navigation', '菜单入口: ChargeOpCharge', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('30', 'Navigation.Chargeoprefund', '门诊退费', 'Navigation', '菜单入口: ChargeOpRefund', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('31', 'Navigation.Chargeopdayclose', '门诊日结', 'Navigation', '菜单入口: ChargeOpDayClose', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('32', 'Navigation.Chargeipregister', '住院登记', 'Navigation', '菜单入口: ChargeIpRegister', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('33', 'Navigation.Chargeipsettle', '住院结算', 'Navigation', '菜单入口: ChargeIpSettle', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('34', 'Navigation.Chargeipprepay', '预交金管理', 'Navigation', '菜单入口: ChargeIpPrepay', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('35', 'Navigation.Chargeipfee', '住院费用管理', 'Navigation', '菜单入口: ChargeIpFee', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('73', 'Navigation.Chargeipdayclose', '住院日结', 'Navigation', '菜单入口: ChargeIpDayClose', NULL, '2026-06-21 00:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('74', 'Navigation.Dispenserecordop', '门诊发退药记录', 'Navigation', '菜单入口: DispenseRecordOp', NULL, '2026-06-21 00:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('75', 'Navigation.Dispenserecordip', '住院发退药记录', 'Navigation', '菜单入口: DispenseRecordIp', NULL, '2026-06-21 00:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('76', 'Navigation.Opfeedetail', '门诊费用明细', 'Navigation', '菜单入口: OpFeeDetail', NULL, '2026-06-21 00:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('36', 'Navigation.Outpatientdoctorstation', '门诊医生站', 'Navigation', '菜单入口: OutpatientDoctorStation', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('37', 'Navigation.Inpatientdoctorstation', '住院医生站', 'Navigation', '菜单入口: InpatientDoctorStation', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('38', 'Navigation.Dispenseop', '门诊发药', 'Navigation', '菜单入口: DispenseOp', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('39', 'Navigation.Dispenseip', '住院发药', 'Navigation', '菜单入口: DispenseIp', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('40', 'Navigation.Dispensedoc', '发药单据', 'Navigation', '菜单入口: DispenseDoc', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('41', 'Navigation.Whpurchaseorder', '药库订货单', 'Navigation', '菜单入口: WhPurchaseOrder', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('42', 'Navigation.Reporthome', '首页', 'Navigation', '菜单入口: ReportHome', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('43', 'Navigation.Reportdean', '院长', 'Navigation', '菜单入口: ReportDean', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('44', 'Navigation.Reportclinical', '临床报表', 'Navigation', '菜单入口: ReportClinical', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('45', 'Navigation.Whpurchaseinbound', '药库进货单', 'Navigation', '菜单入口: WhPurchaseInbound', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('46', 'Navigation.Whstockout', '药库出库单', 'Navigation', '菜单入口: WhStockOut', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('47', 'Navigation.Phstockin', '药房入库单', 'Navigation', '菜单入口: PhStockIn', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('48', 'Navigation.Invstocklist', '库存明细', 'Navigation', '菜单入口: InvStockList', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('49', 'Navigation.Medicinereturnop', '门诊退药', 'Navigation', '菜单入口: MedicineReturnOp', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('53', 'Navigation.Medicinereturnip', '住院退药', 'Navigation', '菜单入口: MedicineReturnIp', NULL, '2026-06-11 00:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('50', 'Navigation.Emrtemplate', '病历模版', 'Navigation', '菜单入口: EmrTemplate', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('51', 'Navigation.Whstocktakeorder', '药库盘点单', 'Navigation', '菜单入口: WhStocktakeOrder', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('52', 'Navigation.Phstocktakeorder', '药房盘点单', 'Navigation', '菜单入口: PhStocktakeOrder', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('54', 'Navigation.ActivityDeviceBinding', '耗材绑定', 'Navigation', '菜单入口: activity_device_binding', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('55', 'Navigation.Executelocation', '执行地点', 'Navigation', '菜单入口: ExecuteLocation', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('56', 'Navigation.OrderTemplate', '医嘱组套', 'Navigation', '菜单入口: order_template', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('57', 'Navigation.ServiceLocation', '取药科室', 'Navigation', '菜单入口: service_location', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('322589916090863616', 'Identity.Users', '用户管理', NULL, NULL, NULL, '2026-06-09 04:17:09.426+00', '2026-06-09 04:17:09.426+00', NULL, NULL, false, NULL, NULL),
('322589916120223744', 'Identity.Users.Create', '创建用户', 'Identity.Users', NULL, NULL, '2026-06-09 04:17:09.433+00', '2026-06-09 04:17:09.433+00', NULL, NULL, false, NULL, NULL),
('322589916137000960', 'Identity.Users.Update', '更新用户', 'Identity.Users', NULL, NULL, '2026-06-09 04:17:09.437+00', '2026-06-09 04:17:09.437+00', NULL, NULL, false, NULL, NULL),
('322589916157972480', 'Identity.Users.Delete', '删除用户', 'Identity.Users', NULL, NULL, '2026-06-09 04:17:09.442+00', '2026-06-09 04:17:09.442+00', NULL, NULL, false, NULL, NULL),
('322589916174749696', 'Identity.Users.View', '查看用户', 'Identity.Users', NULL, NULL, '2026-06-09 04:17:09.446+00', '2026-06-09 04:17:09.446+00', NULL, NULL, false, NULL, NULL),
('322589916199915520', 'Identity.Roles', '角色管理', NULL, NULL, NULL, '2026-06-09 04:17:09.452+00', '2026-06-09 04:17:09.452+00', NULL, NULL, false, NULL, NULL),
('322589916216692736', 'Identity.Roles.Create', '创建角色', 'Identity.Roles', NULL, NULL, '2026-06-09 04:17:09.456+00', '2026-06-09 04:17:09.456+00', NULL, NULL, false, NULL, NULL),
('322589916229275648', 'Identity.Roles.Update', '更新角色', 'Identity.Roles', NULL, NULL, '2026-06-09 04:17:09.459+00', '2026-06-09 04:17:09.459+00', NULL, NULL, false, NULL, NULL),
('322589916246052864', 'Identity.Roles.Delete', '删除角色', 'Identity.Roles', NULL, NULL, '2026-06-09 04:17:09.463+00', '2026-06-09 04:17:09.463+00', NULL, NULL, false, NULL, NULL),
('322589916254441472', 'Identity.Roles.View', '查看角色', 'Identity.Roles', NULL, NULL, '2026-06-09 04:17:09.465+00', '2026-06-09 04:17:09.465+00', NULL, NULL, false, NULL, NULL),
('322589916267024384', 'Identity.Permissions', '权限管理', NULL, NULL, NULL, '2026-06-09 04:17:09.468+00', '2026-06-09 04:17:09.468+00', NULL, NULL, false, NULL, NULL),
('322589916287995904', 'Identity.Permissions.Grant', '授予权限', 'Identity.Permissions', NULL, NULL, '2026-06-09 04:17:09.473+00', '2026-06-09 04:17:09.473+00', NULL, NULL, false, NULL, NULL),
('322589916304773120', 'Identity.Permissions.Revoke', '撤销权限', 'Identity.Permissions', NULL, NULL, '2026-06-09 04:17:09.477+00', '2026-06-09 04:17:09.477+00', NULL, NULL, false, NULL, NULL),
('322589916325744640', 'Identity.Permissions.View', '查看权限', 'Identity.Permissions', NULL, NULL, '2026-06-09 04:17:09.482+00', '2026-06-09 04:17:09.482+00', NULL, NULL, false, NULL, NULL),
('322589916338327552', 'Identity.OrganizationUnits', '组织单元管理', NULL, NULL, NULL, '2026-06-09 04:17:09.485+00', '2026-06-09 04:17:09.485+00', NULL, NULL, false, NULL, NULL),
('322589916355104768', 'Identity.OrganizationUnits.Create', '创建组织单元', 'Identity.OrganizationUnits', NULL, NULL, '2026-06-09 04:17:09.489+00', '2026-06-09 04:17:09.489+00', NULL, NULL, false, NULL, NULL),
('322589916380270592', 'Identity.OrganizationUnits.Update', '更新组织单元', 'Identity.OrganizationUnits', NULL, NULL, '2026-06-09 04:17:09.495+00', '2026-06-09 04:17:09.495+00', NULL, NULL, false, NULL, NULL),
('322589916397047808', 'Identity.OrganizationUnits.Delete', '删除组织单元', 'Identity.OrganizationUnits', NULL, NULL, '2026-06-09 04:17:09.499+00', '2026-06-09 04:17:09.499+00', NULL, NULL, false, NULL, NULL),
('322589916413825024', 'Identity.OrganizationUnits.View', '查看组织单元', 'Identity.OrganizationUnits', NULL, NULL, '2026-06-09 04:17:09.503+00', '2026-06-09 04:17:09.503+00', NULL, NULL, false, NULL, NULL),
('322589916430602240', 'Identity.Users.Impersonate', '代理登录', 'Identity.Users', NULL, NULL, '2026-06-09 04:17:09.507+00', '2026-06-09 04:17:09.507+00', NULL, NULL, false, NULL, NULL),
('322589916443185152', 'TenantManagement.Tenants', '租户管理', NULL, NULL, NULL, '2026-06-09 04:17:09.510+00', '2026-06-09 04:17:09.510+00', NULL, NULL, false, NULL, NULL),
('322589916459962368', 'TenantManagement.Tenants.Create', '创建租户', 'TenantManagement.Tenants', NULL, NULL, '2026-06-09 04:17:09.514+00', '2026-06-09 04:17:09.514+00', NULL, NULL, false, NULL, NULL),
('322589916476739584', 'TenantManagement.Tenants.Update', '更新租户', 'TenantManagement.Tenants', NULL, NULL, '2026-06-09 04:17:09.518+00', '2026-06-09 04:17:09.518+00', NULL, NULL, false, NULL, NULL),
('322589916497711104', 'TenantManagement.Tenants.Delete', '删除租户', 'TenantManagement.Tenants', NULL, NULL, '2026-06-09 04:17:09.523+00', '2026-06-09 04:17:09.523+00', NULL, NULL, false, NULL, NULL),
('322589916510294016', 'TenantManagement.Tenants.View', '查看租户', 'TenantManagement.Tenants', NULL, NULL, '2026-06-09 04:17:09.526+00', '2026-06-09 04:17:09.526+00', NULL, NULL, false, NULL, NULL),
('322589916527071232', 'SettingManagement.Settings', '设置管理', NULL, NULL, NULL, '2026-06-09 04:17:09.530+00', '2026-06-09 04:17:09.530+00', NULL, NULL, false, NULL, NULL),
('322589916548042752', 'SettingManagement.Settings.Create', '创建设置', 'SettingManagement.Settings', NULL, NULL, '2026-06-09 04:17:09.535+00', '2026-06-09 04:17:09.535+00', NULL, NULL, false, NULL, NULL),
('322589916564819968', 'SettingManagement.Settings.Update', '更新设置', 'SettingManagement.Settings', NULL, NULL, '2026-06-09 04:17:09.539+00', '2026-06-09 04:17:09.539+00', NULL, NULL, false, NULL, NULL),
('322589916577402880', 'SettingManagement.Settings.Delete', '删除设置', 'SettingManagement.Settings', NULL, NULL, '2026-06-09 04:17:09.542+00', '2026-06-09 04:17:09.542+00', NULL, NULL, false, NULL, NULL),
('322589916594180096', 'SettingManagement.Settings.View', '查看设置', 'SettingManagement.Settings', NULL, NULL, '2026-06-09 04:17:09.546+00', '2026-06-09 04:17:09.546+00', NULL, NULL, false, NULL, NULL),
('322970151327633408', 'Navigation.Reporttemplatedesigner', '打印模板设计', 'Navigation', '菜单入口: PrintTemplateDesigner（Report 遗留，已废弃）', '1', '2026-06-10 05:28:04.571+00', '2026-06-25 00:00:00.000+00', '1', '1', true, '2026-06-25 00:00:00.000+00', NULL)
ON CONFLICT ("id") DO NOTHING;

-- ========== whale_permission_grants ==========
CREATE TABLE IF NOT EXISTS whale_permission_grants (
    id BIGINT PRIMARY KEY,
    permission_name VARCHAR(256) NOT NULL,
    provider_name VARCHAR(64) NOT NULL,
    provider_key VARCHAR(256) NOT NULL,
    tenant_id BIGINT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT false,
    deleted_at TIMESTAMP,
    deleted_by BIGINT,
    UNIQUE(permission_name, provider_name, provider_key)
);

CREATE INDEX IF NOT EXISTS idx_whale_permission_grants_provider ON whale_permission_grants(provider_name, provider_key);

CREATE INDEX IF NOT EXISTS idx_whale_permission_grants_tenant_id ON whale_permission_grants(tenant_id);

CREATE INDEX IF NOT EXISTS idx_whale_permission_grants_is_deleted ON whale_permission_grants(is_deleted);

CREATE INDEX IF NOT EXISTS idx_whale_permission_grants_provider_lookup ON whale_permission_grants(provider_name, provider_key, permission_name);



-- seed: whale_permission_grants
INSERT INTO "whale_permission_grants" ("id", "permission_name", "provider_name", "provider_key", "tenant_id", "created_at", "updated_at", "created_by", "updated_by", "is_deleted", "deleted_at", "deleted_by")
VALUES
('10001', 'Navigation.ActivityDeviceBinding', 'R', '1', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('10002', 'Navigation.Analytics', 'R', '1', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('10003', 'Navigation.BodystructureManagement', 'R', '1', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('10004', 'Navigation.Chargeipfee', 'R', '1', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('10005', 'Navigation.Chargeipprepay', 'R', '1', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('10006', 'Navigation.Chargeipregister', 'R', '1', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('10007', 'Navigation.Chargeipsettle', 'R', '1', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('10008', 'Navigation.Chargeopcharge', 'R', '1', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('10009', 'Navigation.Chargeopdayclose', 'R', '1', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('10075', 'Navigation.Chargeipdayclose', 'R', '1', NULL, '2026-06-21 00:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('10076', 'Navigation.Dispenserecordop', 'R', '1', NULL, '2026-06-21 00:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('10077', 'Navigation.Dispenserecordip', 'R', '1', NULL, '2026-06-21 00:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('10078', 'Navigation.Opfeedetail', 'R', '1', NULL, '2026-06-21 00:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('10010', 'Navigation.Chargeoprefund', 'R', '1', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('10011', 'Navigation.Chargeopregister', 'R', '1', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('10012', 'Navigation.CodesystemManagement', 'R', '1', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('10013', 'Navigation.ContractManagement', 'R', '1', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('10014', 'Navigation.DeptManagement', 'R', '1', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('10015', 'Navigation.Device', 'R', '1', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('10017', 'Navigation.Diagnosistreatment', 'R', '1', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('10018', 'Navigation.Disease', 'R', '1', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('10019', 'Navigation.Dispensedoc', 'R', '1', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('10020', 'Navigation.Dispenseip', 'R', '1', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('10021', 'Navigation.Dispenseop', 'R', '1', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('10022', 'Navigation.Emrtemplate', 'R', '1', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('10023', 'Navigation.Executelocation', 'R', '1', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('10024', 'Navigation.Frequency', 'R', '1', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('10025', 'Navigation.HealthcareServiceManagement', 'R', '1', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('10026', 'Navigation.Inpatientdoctorstation', 'R', '1', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('10027', 'Navigation.Inpatientnursestation', 'R', '1', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('10028', 'Navigation.Invstocklist', 'R', '1', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('10029', 'Navigation.Medicine', 'R', '1', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('10031', 'Navigation.Medicinereturnop', 'R', '1', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('10032', 'Navigation.MenuManagement', 'R', '1', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('10033', 'Navigation.OrderTemplate', 'R', '1', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('10034', 'Navigation.Outpatientdoctorstation', 'R', '1', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('10035', 'Navigation.Outpatientnursestation', 'R', '1', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('10036', 'Navigation.PatientManagement', 'R', '1', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('10037', 'Navigation.PermissionManagement', 'R', '1', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('10038', 'Navigation.Phstockin', 'R', '1', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('10039', 'Navigation.Phstocktakeorder', 'R', '1', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('10041', 'Navigation.Reportclinical', 'R', '1', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('10042', 'Navigation.Reportdean', 'R', '1', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('10043', 'Navigation.Reportfinance', 'R', '1', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('10044', 'Navigation.Reporthome', 'R', '1', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('10045', 'Navigation.RoleManagement', 'R', '1', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('10046', 'Navigation.ServiceLocation', 'R', '1', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('10047', 'Navigation.SettingManagement', 'R', '1', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('10048', 'Navigation.Supplier', 'R', '1', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('10049', 'Navigation.TenantManagement', 'R', '1', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('10050', 'Navigation.UserManagement', 'R', '1', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('10051', 'Navigation.WardLocationManagement', 'R', '1', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('10052', 'Navigation.WarehouseManagement', 'R', '1', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('10053', 'Navigation.Whpurchaseinbound', 'R', '1', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('10054', 'Navigation.Whpurchaseorder', 'R', '1', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('10055', 'Navigation.Whstockout', 'R', '1', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('10056', 'Navigation.Whstocktakeorder', 'R', '1', NULL, '2026-05-26 20:00:00.000+00', NULL, '1', NULL, false, NULL, NULL),
('322733268284215296', 'Navigation.Inpatientdoctorstation', 'R', '10', '1', '2026-06-09 13:46:47.253+00', '2026-06-09 13:46:47.253+00', '1', '1', false, NULL, NULL),
('322733268309381120', 'Navigation.Outpatientdoctorstation', 'R', '10', '1', '2026-06-09 13:46:47.259+00', '2026-06-09 13:46:47.259+00', '1', '1', false, NULL, NULL),
('322733323334455296', 'Navigation.Outpatientnursestation', 'R', '11', '1', '2026-06-09 13:47:00.379+00', '2026-06-09 13:47:00.379+00', '1', '1', false, NULL, NULL),
('322733323347038208', 'Navigation.Inpatientnursestation', 'R', '11', '1', '2026-06-09 13:47:00.381+00', '2026-06-09 13:47:00.381+00', '1', '1', false, NULL, NULL),
('322733438140944384', 'Navigation.Dispenseip', 'R', '12', '1', '2026-06-09 13:47:27.750+00', '2026-06-09 13:47:27.750+00', '1', '1', false, NULL, NULL),
('322733438157721600', 'Navigation.Dispenseop', 'R', '12', '1', '2026-06-09 13:47:27.754+00', '2026-06-09 13:47:27.754+00', '1', '1', false, NULL, NULL),
('322733438174498816', 'Navigation.Device', 'R', '12', '1', '2026-06-09 13:47:27.758+00', '2026-06-09 13:47:27.758+00', '1', '1', false, NULL, NULL),
('322733438187081728', 'Navigation.Dispensedoc', 'R', '12', '1', '2026-06-09 13:47:27.761+00', '2026-06-09 13:47:27.761+00', '1', '1', false, NULL, NULL),
('322733438203858944', 'Navigation.Medicine', 'R', '12', '1', '2026-06-09 13:47:27.765+00', '2026-06-09 13:47:27.765+00', '1', '1', false, NULL, NULL),
('322733438216441856', 'Navigation.Medicinereturnop', 'R', '12', '1', '2026-06-09 13:47:27.768+00', '2026-06-09 13:47:27.768+00', '1', '1', false, NULL, NULL),
('322733572107014144', 'Navigation.Invstocklist', 'R', '13', '1', '2026-06-09 13:47:59.690+00', '2026-06-09 13:47:59.690+00', '1', '1', false, NULL, NULL),
('322733572119597056', 'Navigation.Whpurchaseinbound', 'R', '13', '1', '2026-06-09 13:47:59.693+00', '2026-06-09 13:47:59.693+00', '1', '1', false, NULL, NULL),
('322733572136374272', 'Navigation.Whpurchaseorder', 'R', '13', '1', '2026-06-09 13:47:59.697+00', '2026-06-09 13:47:59.697+00', '1', '1', false, NULL, NULL),
('322733572153151488', 'Navigation.Phstockin', 'R', '13', '1', '2026-06-09 13:47:59.701+00', '2026-06-09 13:47:59.701+00', '1', '1', false, NULL, NULL),
('322733572165734400', 'Navigation.Device', 'R', '13', '1', '2026-06-09 13:47:59.704+00', '2026-06-09 13:47:59.704+00', '1', '1', false, NULL, NULL),
('322733572182511616', 'Navigation.Whstockout', 'R', '13', '1', '2026-06-09 13:47:59.708+00', '2026-06-09 13:47:59.708+00', '1', '1', false, NULL, NULL),
('322733572199288832', 'Navigation.Phstocktakeorder', 'R', '13', '1', '2026-06-09 13:47:59.712+00', '2026-06-09 13:47:59.712+00', '1', '1', false, NULL, NULL),
('322733572207677440', 'Navigation.Medicine', 'R', '13', '1', '2026-06-09 13:47:59.714+00', '2026-06-09 13:47:59.714+00', '1', '1', false, NULL, NULL),
('322733572220260352', 'Navigation.Whstocktakeorder', 'R', '13', '1', '2026-06-09 13:47:59.717+00', '2026-06-09 13:47:59.717+00', '1', '1', false, NULL, NULL),
('322733614511427584', 'Navigation.Reportclinical', 'R', '14', '1', '2026-06-09 13:48:09.800+00', '2026-06-09 13:48:09.800+00', '1', '1', false, NULL, NULL),
('322733614519816192', 'Navigation.Chargeipfee', 'R', '14', '1', '2026-06-09 13:48:09.802+00', '2026-06-09 13:48:09.802+00', '1', '1', false, NULL, NULL),
('322733614528204800', 'Navigation.Chargeipprepay', 'R', '14', '1', '2026-06-09 13:48:09.804+00', '2026-06-09 13:48:09.804+00', '1', '1', false, NULL, NULL),
('322733614544982016', 'Navigation.Chargeoprefund', 'R', '14', '1', '2026-06-09 13:48:09.808+00', '2026-06-09 13:48:09.808+00', '1', '1', false, NULL, NULL),
('322733614553370624', 'Navigation.Reporthome', 'R', '14', '1', '2026-06-09 13:48:09.810+00', '2026-06-09 13:48:09.810+00', '1', '1', false, NULL, NULL),
('322733614561759232', 'Navigation.Chargeopdayclose', 'R', '14', '1', '2026-06-09 13:48:09.812+00', '2026-06-09 13:48:09.812+00', '1', '1', false, NULL, NULL),
('322733614561759233', 'Navigation.Chargeipdayclose', 'R', '14', '1', '2026-06-21 00:00:00.000+00', '2026-06-21 00:00:00.000+00', '1', '1', false, NULL, NULL),
('322733614561759234', 'Navigation.Opfeedetail', 'R', '14', '1', '2026-06-21 00:00:00.000+00', '2026-06-21 00:00:00.000+00', '1', '1', false, NULL, NULL),
('322733438157721601', 'Navigation.Dispenserecordop', 'R', '12', '1', '2026-06-21 00:00:00.000+00', '2026-06-21 00:00:00.000+00', '1', '1', false, NULL, NULL),
('322733438157721602', 'Navigation.Dispenserecordip', 'R', '12', '1', '2026-06-21 00:00:00.000+00', '2026-06-21 00:00:00.000+00', '1', '1', false, NULL, NULL),
('322733614574342144', 'Navigation.Chargeipregister', 'R', '14', '1', '2026-06-09 13:48:09.815+00', '2026-06-09 13:48:09.815+00', '1', '1', false, NULL, NULL),
('322733614582730752', 'Navigation.Chargeopregister', 'R', '14', '1', '2026-06-09 13:48:09.817+00', '2026-06-09 13:48:09.817+00', '1', '1', false, NULL, NULL),
('322733614586925056', 'Navigation.Chargeopcharge', 'R', '14', '1', '2026-06-09 13:48:09.818+00', '2026-06-09 13:48:09.818+00', '1', '1', false, NULL, NULL),
('322733614599507968', 'Navigation.Reportdean', 'R', '14', '1', '2026-06-09 13:48:09.821+00', '2026-06-09 13:48:09.821+00', '1', '1', false, NULL, NULL),
('322733614612090880', 'Navigation.Chargeipsettle', 'R', '14', '1', '2026-06-09 13:48:09.824+00', '2026-06-09 13:48:09.824+00', '1', '1', false, NULL, NULL),
('322733614620479488', 'Navigation.Reportfinance', 'R', '14', '1', '2026-06-09 13:48:09.826+00', '2026-06-09 13:48:09.826+00', '1', '1', false, NULL, NULL),
('322973244809809920', 'Navigation.Analytics', 'R', '10', '1', '2026-06-10 05:40:22.115+00', '2026-06-10 05:40:22.115+00', '1', '1', false, NULL, NULL),
('322973259494068224', 'Navigation.Analytics', 'R', '11', '1', '2026-06-10 05:40:25.616+00', '2026-06-10 05:40:25.616+00', '1', '1', false, NULL, NULL),
('322973270965489664', 'Navigation.Analytics', 'R', '12', '1', '2026-06-10 05:40:28.351+00', '2026-06-10 05:40:28.351+00', '1', '1', false, NULL, NULL),
('322973282835369984', 'Navigation.Analytics', 'R', '13', '1', '2026-06-10 05:40:31.181+00', '2026-06-10 05:40:31.181+00', '1', '1', false, NULL, NULL),
('322973294264848384', 'Navigation.Analytics', 'R', '14', '1', '2026-06-10 05:40:33.906+00', '2026-06-10 05:40:33.906+00', '1', '1', false, NULL, NULL),
('323005257756971008', 'Navigation.Phstockin', 'R', '12', '1', '2026-06-10 07:47:34.596+00', '2026-06-10 07:47:34.596+00', '1', '1', false, NULL, NULL),
('323005257777942528', 'Navigation.Phstocktakeorder', 'R', '12', '1', '2026-06-10 07:47:34.601+00', '2026-06-10 07:47:34.601+00', '1', '1', false, NULL, NULL),
('323022177444040704', 'Navigation.WarehouseManagement', 'R', '323022177255297024', '1', '2026-06-10 08:54:48.564+00', '2026-06-10 08:54:48.564+00', '1', '1', false, NULL, NULL),
('323022177465012224', 'Navigation.PatientManagement', 'R', '323022177255297024', '1', '2026-06-10 08:54:48.569+00', '2026-06-10 08:54:48.569+00', '1', '1', false, NULL, NULL),
('323022177477595136', 'Navigation.Disease', 'R', '323022177255297024', '1', '2026-06-10 08:54:48.572+00', '2026-06-10 08:54:48.572+00', '1', '1', false, NULL, NULL),
('323022177490178048', 'Navigation.Diagnosistreatment', 'R', '323022177255297024', '1', '2026-06-10 08:54:48.575+00', '2026-06-10 08:54:48.575+00', '1', '1', false, NULL, NULL),
('323022177502760960', 'Navigation.DeptManagement', 'R', '323022177255297024', '1', '2026-06-10 08:54:48.578+00', '2026-06-10 08:54:48.578+00', '1', '1', false, NULL, NULL),
('323022177511149568', 'Navigation.CodesystemManagement', 'R', '323022177255297024', '1', '2026-06-10 08:54:48.580+00', '2026-06-10 08:54:48.580+00', '1', '1', false, NULL, NULL),
('323022177519538176', 'Navigation.Reporttemplatedesigner', 'R', '323022177255297024', '1', '2026-06-10 08:54:48.582+00', '2026-06-10 08:54:48.582+00', '1', '1', false, NULL, NULL),
('323022177532121088', 'Navigation.Executelocation', 'R', '323022177255297024', '1', '2026-06-10 08:54:48.585+00', '2026-06-10 08:54:48.585+00', '1', '1', false, NULL, NULL),
('323022177540509696', 'Navigation.OrderTemplate', 'R', '323022177255297024', '1', '2026-06-10 08:54:48.587+00', '2026-06-10 08:54:48.587+00', '1', '1', false, NULL, NULL),
('323022177553092608', 'Navigation.ServiceLocation', 'R', '323022177255297024', '1', '2026-06-10 08:54:48.590+00', '2026-06-10 08:54:48.590+00', '1', '1', false, NULL, NULL),
('323022177565675520', 'Navigation.BodystructureManagement', 'R', '323022177255297024', '1', '2026-06-10 08:54:48.593+00', '2026-06-10 08:54:48.593+00', '1', '1', false, NULL, NULL),
('323022177574064128', 'Navigation.WardLocationManagement', 'R', '323022177255297024', '1', '2026-06-10 08:54:48.595+00', '2026-06-10 08:54:48.595+00', '1', '1', false, NULL, NULL),
('323022177586647040', 'Navigation.HealthcareServiceManagement', 'R', '323022177255297024', '1', '2026-06-10 08:54:48.598+00', '2026-06-10 08:54:48.598+00', '1', '1', false, NULL, NULL),
('323022177595035648', 'Navigation.Device', 'R', '323022177255297024', '1', '2026-06-10 08:54:48.600+00', '2026-06-10 08:54:48.600+00', '1', '1', false, NULL, NULL),
('323022177603424256', 'Navigation.Medicine', 'R', '323022177255297024', '1', '2026-06-10 08:54:48.602+00', '2026-06-10 08:54:48.602+00', '1', '1', false, NULL, NULL),
('323022177620201472', 'Navigation.ContractManagement', 'R', '323022177255297024', '1', '2026-06-10 08:54:48.606+00', '2026-06-10 08:54:48.606+00', '1', '1', false, NULL, NULL),
('323022177632784384', 'Navigation.Supplier', 'R', '323022177255297024', '1', '2026-06-10 08:54:48.609+00', '2026-06-10 08:54:48.609+00', '1', '1', false, NULL, NULL),
('323022177645367296', 'Navigation.Frequency', 'R', '323022177255297024', '1', '2026-06-10 08:54:48.612+00', '2026-06-10 08:54:48.612+00', '1', '1', false, NULL, NULL),
('323022177653755904', 'Navigation.Emrtemplate', 'R', '323022177255297024', '1', '2026-06-10 08:54:48.614+00', '2026-06-10 08:54:48.614+00', '1', '1', false, NULL, NULL),
('323022177662144512', 'Navigation.ActivityDeviceBinding', 'R', '323022177255297024', '1', '2026-06-10 08:54:48.616+00', '2026-06-10 08:54:48.616+00', '1', '1', false, NULL, NULL),
('323022431488839680', 'Navigation.Reportclinical', 'R', '323022177255297024', '1', '2026-06-10 08:55:49.133+00', '2026-06-10 08:55:49.133+00', '1', '1', false, NULL, NULL),
('323022431501422592', 'Navigation.RoleManagement', 'R', '323022177255297024', '1', '2026-06-10 08:55:49.136+00', '2026-06-10 08:55:49.136+00', '1', '1', false, NULL, NULL),
('323022431514005504', 'Navigation.Reportfinance', 'R', '323022177255297024', '1', '2026-06-10 08:55:49.139+00', '2026-06-10 08:55:49.139+00', '1', '1', false, NULL, NULL),
('323022431522394112', 'Navigation.UserManagement', 'R', '323022177255297024', '1', '2026-06-10 08:55:49.141+00', '2026-06-10 08:55:49.141+00', '1', '1', false, NULL, NULL),
('323022431534977024', 'Navigation.Reporthome', 'R', '323022177255297024', '1', '2026-06-10 08:55:49.144+00', '2026-06-10 08:55:49.144+00', '1', '1', false, NULL, NULL),
('323022431547559936', 'Navigation.Reportdean', 'R', '323022177255297024', '1', '2026-06-10 08:55:49.147+00', '2026-06-10 08:55:49.147+00', '1', '1', false, NULL, NULL),
('323022431560142848', 'Navigation.SettingManagement', 'R', '323022177255297024', '1', '2026-06-10 08:55:49.150+00', '2026-06-10 08:55:49.150+00', '1', '1', false, NULL, NULL),
('323023014815862784', 'Navigation.Analytics', 'R', '323022177255297024', '1', '2026-06-10 08:58:08.209+00', '2026-06-10 08:58:08.209+00', '1', '1', false, NULL, NULL)
ON CONFLICT ("id") DO NOTHING;

-- ========== whale_security_logs ==========
CREATE TABLE IF NOT EXISTS whale_security_logs (
    id BIGINT PRIMARY KEY,
    user_id BIGINT,
    user_name VARCHAR(256),
    action VARCHAR(64) NOT NULL,
    client_ip VARCHAR(64),
    user_agent VARCHAR(512),
    result VARCHAR(32),
    failure_reason VARCHAR(512),
    tenant_id BIGINT,
    extra_properties JSONB,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT false,
    deleted_at TIMESTAMP,
    deleted_by BIGINT
);

CREATE INDEX IF NOT EXISTS idx_whale_security_logs_action ON whale_security_logs(action);

CREATE INDEX IF NOT EXISTS idx_whale_security_logs_created_at ON whale_security_logs(created_at);

CREATE INDEX IF NOT EXISTS idx_whale_security_logs_tenant_id ON whale_security_logs(tenant_id);

CREATE INDEX IF NOT EXISTS idx_whale_security_logs_is_deleted ON whale_security_logs(is_deleted);

CREATE INDEX IF NOT EXISTS idx_whale_security_logs_user_action ON whale_security_logs(user_id, action, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_whale_security_logs_result ON whale_security_logs(result) WHERE result = 'FAILED';

-- (empty) no rows in whale_security_logs

-- ========== whale_organization_units ==========
CREATE TABLE IF NOT EXISTS whale_organization_units (
    id BIGINT PRIMARY KEY,
    code VARCHAR(128) NOT NULL UNIQUE,
    display_name VARCHAR(256) NOT NULL,
    parent_id BIGINT,
    sort_order INTEGER DEFAULT 0,
    is_enabled BOOLEAN NOT NULL DEFAULT true,
    remark VARCHAR(512),
    tenant_id BIGINT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT false,
    deleted_at TIMESTAMP,
    deleted_by BIGINT,
    CONSTRAINT fk_whale_ou_parent FOREIGN KEY (parent_id) REFERENCES whale_organization_units(id)
);

CREATE INDEX IF NOT EXISTS idx_whale_ou_parent_id ON whale_organization_units(parent_id);

CREATE INDEX IF NOT EXISTS idx_whale_ou_tenant_id ON whale_organization_units(tenant_id);



-- seed: whale_organization_units
INSERT INTO "whale_organization_units" ("id", "code", "display_name", "parent_id", "sort_order", "is_enabled", "remark", "tenant_id", "created_at", "updated_at", "created_by", "updated_by", "is_deleted", "deleted_at", "deleted_by")
VALUES
('322663105157926912', 'OR0007', '测试医院', NULL, 0, true, NULL, '1', '2026-06-09 09:07:59.060+00', '2026-06-09 09:07:59.060+00', '1', '1', false, NULL, NULL),
('322663218286694400', 'OR0007.OR0008', '门诊内科', '322663105157926912', 0, true, NULL, '1', '2026-06-09 09:08:26.032+00', '2026-06-09 09:08:26.032+00', '1', '1', false, NULL, NULL),
('322663308824940544', 'OR0007.OR0009', '住院内科', '322663105157926912', 0, true, NULL, '1', '2026-06-09 09:08:47.618+00', '2026-06-09 09:08:47.618+00', '1', '1', false, NULL, NULL),
('322663606398226432', 'OR0007.OR0010', '处置室', '322663105157926912', 0, true, NULL, '1', '2026-06-09 09:09:58.565+00', '2026-06-09 09:09:58.565+00', '1', '1', false, NULL, NULL),
('322663652535570432', 'OR0007.OR0011', '护理站', '322663105157926912', 0, true, NULL, '1', '2026-06-09 09:10:09.565+00', '2026-06-09 09:10:09.565+00', '1', '1', false, NULL, NULL),
('322663815438143488', 'OR0007.OR0012', '挂号收费处', '322663105157926912', 0, true, NULL, '1', '2026-06-09 09:10:48.405+00', '2026-06-09 09:10:48.405+00', '1', '1', false, NULL, NULL)
ON CONFLICT ("id") DO NOTHING;

-- ========== whale_user_organization_units ==========
CREATE TABLE IF NOT EXISTS whale_user_organization_units (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    organization_unit_id BIGINT NOT NULL,
    tenant_id BIGINT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT false,
    deleted_at TIMESTAMP,
    deleted_by BIGINT,
    CONSTRAINT fk_whale_uou_user FOREIGN KEY (user_id) REFERENCES whale_users(id),
    CONSTRAINT fk_whale_uou_ou FOREIGN KEY (organization_unit_id) REFERENCES whale_organization_units(id),
    CONSTRAINT uk_whale_uou_user_ou UNIQUE (user_id, organization_unit_id)
);

CREATE INDEX IF NOT EXISTS idx_whale_uou_ou_id ON whale_user_organization_units(organization_unit_id);

CREATE INDEX IF NOT EXISTS idx_whale_uou_tenant_id ON whale_user_organization_units(tenant_id);



-- seed: whale_user_organization_units
INSERT INTO "whale_user_organization_units" ("id", "user_id", "organization_unit_id", "tenant_id", "created_at", "updated_at", "created_by", "updated_by", "is_deleted", "deleted_at", "deleted_by")
VALUES
('322961416313114624', '322935698131259392', '322663308824940544', '1', '2026-06-10 04:53:21.982+00', '2026-06-10 04:53:21.982+00', '1', '1', false, NULL, NULL),
('322963602691592192', '322963602154721280', '322663218286694400', '1', '2026-06-10 05:02:03.255+00', '2026-06-10 05:02:03.255+00', '1', '1', false, NULL, NULL),
('322965879032647680', '1', '322663218286694400', '1', '2026-06-10 05:11:05.977+00', '2026-06-10 05:11:05.977+00', '1', '1', false, NULL, NULL),
('322965879032647681', '1', '322663308824940544', '1', '2026-06-10 05:11:05.977+00', '2026-06-10 05:11:05.977+00', '1', '1', false, NULL, NULL),
('322972327045238784', '322965022853566464', '322663652535570432', '1', '2026-06-10 05:36:43.303+00', '2026-06-10 05:36:43.303+00', '1', '1', false, NULL, NULL),
('322972706579943424', '322932211309350912', '322663815438143488', '1', '2026-06-10 05:38:13.791+00', '2026-06-10 05:38:13.791+00', '1', '1', false, NULL, NULL),
('323005938243997696', '322932010666430464', '322663105157926912', '1', '2026-06-10 07:50:16.837+00', '2026-06-10 07:50:16.837+00', '322932010666430464', '322932010666430464', false, NULL, NULL),
('323022941348433920', '323022664197214208', '322663105157926912', '1', '2026-06-10 08:57:50.693+00', '2026-06-10 08:57:50.693+00', '323022664197214208', '323022664197214208', false, NULL, NULL),
('323023779286487040', '322932096091820032', '322663105157926912', '1', '2026-06-10 09:01:10.473+00', '2026-06-10 09:01:10.473+00', '322932096091820032', '322932096091820032', false, NULL, NULL)
ON CONFLICT ("id") DO NOTHING;

-- ========== whale_user_claims ==========
CREATE TABLE IF NOT EXISTS whale_user_claims (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    claim_type VARCHAR(256) NOT NULL,
    claim_value VARCHAR(512),
    tenant_id BIGINT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT false,
    deleted_at TIMESTAMP,
    deleted_by BIGINT,
    CONSTRAINT fk_whale_uc_user FOREIGN KEY (user_id) REFERENCES whale_users(id),
    CONSTRAINT uk_whale_uc_user_type_value UNIQUE (user_id, claim_type, claim_value)
);

CREATE INDEX IF NOT EXISTS idx_whale_uc_claim_type ON whale_user_claims(claim_type);

CREATE INDEX IF NOT EXISTS idx_whale_uc_tenant_id ON whale_user_claims(tenant_id);

-- ========== whale_role_claims ==========
CREATE TABLE IF NOT EXISTS whale_role_claims (
    id BIGINT PRIMARY KEY,
    role_id BIGINT NOT NULL,
    claim_type VARCHAR(256) NOT NULL,
    claim_value VARCHAR(512),
    tenant_id BIGINT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT false,
    deleted_at TIMESTAMP,
    deleted_by BIGINT,
    CONSTRAINT fk_whale_rc_role FOREIGN KEY (role_id) REFERENCES whale_roles(id),
    CONSTRAINT uk_whale_rc_role_type_value UNIQUE (role_id, claim_type, claim_value)
);

CREATE INDEX IF NOT EXISTS idx_whale_rc_claim_type ON whale_role_claims(claim_type);

CREATE INDEX IF NOT EXISTS idx_whale_rc_tenant_id ON whale_role_claims(tenant_id);



-- seed: whale_role_claims
INSERT INTO "whale_role_claims" ("id", "role_id", "claim_type", "claim_value", "tenant_id", "created_at", "updated_at", "created_by", "updated_by", "is_deleted", "deleted_at", "deleted_by")
VALUES
('105001', '10', 'AssignmentScope', 'ORG', NULL, '2026-06-09 12:18:57.404+00', '2026-06-09 12:18:57.404+00', NULL, NULL, false, NULL, NULL),
('105002', '10', 'StaffScope', 'DOCTOR_VISIT_ORG', NULL, '2026-06-09 12:18:57.404+00', '2026-06-09 12:18:57.404+00', NULL, NULL, false, NULL, NULL),
('105004', '11', 'AssignmentScope', 'WARD', NULL, '2026-06-09 12:18:57.404+00', '2026-06-09 12:18:57.404+00', NULL, NULL, false, NULL, NULL),
('105006', '11', 'StaffScope', 'NURSE_WARD_LOCATION', NULL, '2026-06-09 12:18:57.404+00', '2026-06-09 12:18:57.404+00', NULL, NULL, false, NULL, NULL),
('105007', '12', 'AssignmentScope', 'PHARMACY', NULL, '2026-06-09 12:18:57.404+00', '2026-06-09 12:18:57.404+00', NULL, NULL, false, NULL, NULL),
('105008', '12', 'StaffScope', 'PHARMACY_LOCATION', NULL, '2026-06-09 12:18:57.404+00', '2026-06-09 12:18:57.404+00', NULL, NULL, false, NULL, NULL),
('105009', '13', 'AssignmentScope', 'WAREHOUSE', NULL, '2026-06-09 12:18:57.404+00', '2026-06-09 12:18:57.404+00', NULL, NULL, false, NULL, NULL),
('105010', '13', 'StaffScope', 'WAREHOUSE_LOCATION', NULL, '2026-06-09 12:18:57.404+00', '2026-06-09 12:18:57.404+00', NULL, NULL, false, NULL, NULL),
('105011', '14', 'AssignmentScope', 'ORG', NULL, '2026-06-09 12:18:57.404+00', '2026-06-09 12:18:57.404+00', NULL, NULL, false, NULL, NULL),
('105013', '1', 'AssignmentScope', 'GLOBAL', NULL, '2026-06-09 12:03:45.933+00', '2026-06-09 12:18:57.404+00', NULL, NULL, false, NULL, NULL),
('105015', '1', 'StaffScope', 'DOCTOR_VISIT_ORG', NULL, '2026-06-09 12:03:45.933+00', '2026-06-09 12:18:57.404+00', NULL, NULL, false, NULL, NULL),
('105016', '1', 'StaffScope', 'WAREHOUSE_LOCATION', NULL, '2026-06-09 12:03:45.933+00', '2026-06-09 12:18:57.404+00', NULL, NULL, false, NULL, NULL),
('105017', '1', 'StaffScope', 'PHARMACY_LOCATION', NULL, '2026-06-09 12:03:45.933+00', '2026-06-09 12:18:57.404+00', NULL, NULL, false, NULL, NULL),
('105018', '1', 'StaffScope', 'NURSE_ORG', NULL, '2026-06-09 12:03:45.933+00', '2026-06-09 12:18:57.404+00', NULL, NULL, false, NULL, NULL),
('105019', '1', 'StaffScope', 'NURSE_WARD_LOCATION', NULL, '2026-06-09 12:03:45.933+00', '2026-06-09 12:18:57.404+00', NULL, NULL, false, NULL, NULL),
('105021', '13', 'AssignmentScope', 'PHARMACY', NULL, '2026-06-10 03:51:26.617+00', '2026-06-10 03:51:26.617+00', NULL, NULL, false, NULL, NULL),
('105022', '13', 'StaffScope', 'PHARMACY_LOCATION', NULL, '2026-06-10 03:51:26.617+00', '2026-06-10 03:51:26.617+00', NULL, NULL, false, NULL, NULL),
('323022177729253376', '323022177255297024', 'StaffScope', 'DOCTOR_VISIT_ORG', '1', '2026-06-10 08:54:48.632+00', '2026-06-10 08:54:48.632+00', '1', '1', false, NULL, NULL),
('323022177729253378', '323022177255297024', 'StaffScope', 'WAREHOUSE_LOCATION', '1', '2026-06-10 08:54:48.632+00', '2026-06-10 08:54:48.632+00', '1', '1', false, NULL, NULL),
('323022177729253379', '323022177255297024', 'StaffScope', 'PHARMACY_LOCATION', '1', '2026-06-10 08:54:48.632+00', '2026-06-10 08:54:48.632+00', '1', '1', false, NULL, NULL),
('323022177733447680', '323022177255297024', 'StaffScope', 'NURSE_WARD_LOCATION', '1', '2026-06-10 08:54:48.633+00', '2026-06-10 08:54:48.633+00', '1', '1', false, NULL, NULL)
ON CONFLICT ("id") DO NOTHING;

-- ========== whale_qrtz_locks ==========
CREATE TABLE IF NOT EXISTS whale_qrtz_locks (
    sched_name VARCHAR(120) NOT NULL,
    lock_name VARCHAR(40) NOT NULL,
    PRIMARY KEY (sched_name, lock_name)
);



-- seed: whale_qrtz_locks
INSERT INTO "whale_qrtz_locks" ("sched_name", "lock_name")
VALUES
('WhaleScheduler', 'TRIGGER_ACCESS')
ON CONFLICT ("sched_name", "lock_name") DO NOTHING;

-- ========== whale_qrtz_job_details ==========
CREATE TABLE IF NOT EXISTS whale_qrtz_job_details (
    sched_name VARCHAR(120) NOT NULL,
    job_name VARCHAR(200) NOT NULL,
    job_group VARCHAR(200) NOT NULL,
    description VARCHAR(250),
    job_class_name VARCHAR(250) NOT NULL,
    is_durable BOOLEAN NOT NULL,
    is_nonconcurrent BOOLEAN NOT NULL,
    is_update_data BOOLEAN NOT NULL,
    requests_recovery BOOLEAN NOT NULL,
    job_data BYTEA,
    PRIMARY KEY (sched_name, job_name, job_group)
);

CREATE INDEX IF NOT EXISTS idx_whale_qrtz_j_grp ON whale_qrtz_job_details(sched_name, job_group);

-- ========== whale_qrtz_triggers ==========
CREATE TABLE IF NOT EXISTS whale_qrtz_triggers (
    sched_name VARCHAR(120) NOT NULL,
    trigger_name VARCHAR(200) NOT NULL,
    trigger_group VARCHAR(200) NOT NULL,
    job_name VARCHAR(200) NOT NULL,
    job_group VARCHAR(200) NOT NULL,
    description VARCHAR(250),
    next_fire_time BIGINT,
    prev_fire_time BIGINT,
    priority INTEGER,
    trigger_state VARCHAR(16) NOT NULL,
    trigger_type VARCHAR(8) NOT NULL,
    start_time BIGINT NOT NULL,
    end_time BIGINT,
    calendar_name VARCHAR(200),
    misfire_instr SMALLINT,
    job_data BYTEA,
    PRIMARY KEY (sched_name, trigger_name, trigger_group),
    FOREIGN KEY (sched_name, job_name, job_group)
        REFERENCES whale_qrtz_job_details(sched_name, job_name, job_group)
);

CREATE INDEX IF NOT EXISTS idx_whale_qrtz_t_j ON whale_qrtz_triggers(sched_name, job_name, job_group);

CREATE INDEX IF NOT EXISTS idx_whale_qrtz_t_jg ON whale_qrtz_triggers(sched_name, job_group);

CREATE INDEX IF NOT EXISTS idx_whale_qrtz_t_c ON whale_qrtz_triggers(sched_name, calendar_name);

CREATE INDEX IF NOT EXISTS idx_whale_qrtz_t_g ON whale_qrtz_triggers(sched_name, trigger_group);

CREATE INDEX IF NOT EXISTS idx_whale_qrtz_t_state ON whale_qrtz_triggers(sched_name, trigger_state);

CREATE INDEX IF NOT EXISTS idx_whale_qrtz_t_n_state ON whale_qrtz_triggers(sched_name, trigger_name, trigger_group, trigger_state);

CREATE INDEX IF NOT EXISTS idx_whale_qrtz_t_n_g_state ON whale_qrtz_triggers(sched_name, trigger_group, trigger_state);

CREATE INDEX IF NOT EXISTS idx_whale_qrtz_t_next_fire_time ON whale_qrtz_triggers(sched_name, next_fire_time);

CREATE INDEX IF NOT EXISTS idx_whale_qrtz_t_nft_st ON whale_qrtz_triggers(sched_name, trigger_state, next_fire_time);

CREATE INDEX IF NOT EXISTS idx_whale_qrtz_t_nft_misfire ON whale_qrtz_triggers(sched_name, misfire_instr, next_fire_time);

CREATE INDEX IF NOT EXISTS idx_whale_qrtz_t_nft_st_misfire ON whale_qrtz_triggers(sched_name, misfire_instr, next_fire_time, trigger_state);

-- ========== whale_qrtz_cron_triggers ==========
CREATE TABLE IF NOT EXISTS whale_qrtz_cron_triggers (
    sched_name VARCHAR(120) NOT NULL,
    trigger_name VARCHAR(200) NOT NULL,
    trigger_group VARCHAR(200) NOT NULL,
    cron_expression VARCHAR(120) NOT NULL,
    time_zone_id VARCHAR(80),
    PRIMARY KEY (sched_name, trigger_name, trigger_group),
    FOREIGN KEY (sched_name, trigger_name, trigger_group)
        REFERENCES whale_qrtz_triggers(sched_name, trigger_name, trigger_group)
);

-- ========== whale_qrtz_simple_triggers ==========
CREATE TABLE IF NOT EXISTS whale_qrtz_simple_triggers (
    sched_name VARCHAR(120) NOT NULL,
    trigger_name VARCHAR(200) NOT NULL,
    trigger_group VARCHAR(200) NOT NULL,
    repeat_count BIGINT NOT NULL,
    repeat_interval BIGINT NOT NULL,
    times_triggered BIGINT NOT NULL,
    PRIMARY KEY (sched_name, trigger_name, trigger_group),
    FOREIGN KEY (sched_name, trigger_name, trigger_group)
        REFERENCES whale_qrtz_triggers(sched_name, trigger_name, trigger_group)
);

-- ========== whale_qrtz_blob_triggers ==========
CREATE TABLE IF NOT EXISTS whale_qrtz_blob_triggers (
    sched_name VARCHAR(120) NOT NULL,
    trigger_name VARCHAR(200) NOT NULL,
    trigger_group VARCHAR(200) NOT NULL,
    blob_data BYTEA,
    PRIMARY KEY (sched_name, trigger_name, trigger_group),
    FOREIGN KEY (sched_name, trigger_name, trigger_group)
        REFERENCES whale_qrtz_triggers(sched_name, trigger_name, trigger_group)
);

-- ========== whale_qrtz_calendars ==========
CREATE TABLE IF NOT EXISTS whale_qrtz_calendars (
    sched_name VARCHAR(120) NOT NULL,
    calendar_name VARCHAR(200) NOT NULL,
    calendar BYTEA NOT NULL,
    PRIMARY KEY (sched_name, calendar_name)
);

-- ========== whale_qrtz_paused_trigger_grps ==========
CREATE TABLE IF NOT EXISTS whale_qrtz_paused_trigger_grps (
    sched_name VARCHAR(120) NOT NULL,
    trigger_group VARCHAR(200) NOT NULL,
    PRIMARY KEY (sched_name, trigger_group)
);

-- ========== whale_qrtz_scheduler_state ==========
CREATE TABLE IF NOT EXISTS whale_qrtz_scheduler_state (
    sched_name VARCHAR(120) NOT NULL,
    instance_name VARCHAR(200) NOT NULL,
    last_checkin_time BIGINT NOT NULL,
    checkin_interval BIGINT NOT NULL,
    PRIMARY KEY (sched_name, instance_name)
);

-- ========== whale_qrtz_fired_triggers ==========
CREATE TABLE IF NOT EXISTS whale_qrtz_fired_triggers (
    sched_name VARCHAR(120) NOT NULL,
    entry_id VARCHAR(95) NOT NULL,
    trigger_name VARCHAR(200) NOT NULL,
    trigger_group VARCHAR(200) NOT NULL,
    instance_name VARCHAR(200) NOT NULL,
    fired_time BIGINT NOT NULL,
    sched_time BIGINT NOT NULL,
    priority INTEGER NOT NULL,
    state VARCHAR(16) NOT NULL,
    job_name VARCHAR(200),
    job_group VARCHAR(200),
    is_nonconcurrent BOOLEAN,
    requests_recovery BOOLEAN,
    PRIMARY KEY (sched_name, entry_id)
);

CREATE INDEX IF NOT EXISTS idx_whale_qrtz_ft_trig_inst_name ON whale_qrtz_fired_triggers(sched_name, instance_name);

CREATE INDEX IF NOT EXISTS idx_whale_qrtz_ft_inst_job_req_rcvry ON whale_qrtz_fired_triggers(sched_name, instance_name, requests_recovery);

CREATE INDEX IF NOT EXISTS idx_whale_qrtz_ft_j_g ON whale_qrtz_fired_triggers(sched_name, job_name, job_group);

CREATE INDEX IF NOT EXISTS idx_whale_qrtz_ft_jg ON whale_qrtz_fired_triggers(sched_name, job_group);

CREATE INDEX IF NOT EXISTS idx_whale_qrtz_ft_t_g ON whale_qrtz_fired_triggers(sched_name, trigger_name, trigger_group);

CREATE INDEX IF NOT EXISTS idx_whale_qrtz_ft_tg ON whale_qrtz_fired_triggers(sched_name, trigger_group);

-- ========== whale_tenants ==========
CREATE TABLE IF NOT EXISTS whale_tenants (
    id BIGINT PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    code VARCHAR(64) NOT NULL UNIQUE,
    description VARCHAR(512),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    extra_properties TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,
    deleted_by BIGINT
);

CREATE INDEX IF NOT EXISTS idx_whale_tenants_code ON whale_tenants(code);

CREATE INDEX IF NOT EXISTS idx_whale_tenants_is_active ON whale_tenants(is_active);

CREATE INDEX IF NOT EXISTS idx_whale_tenants_created_at ON whale_tenants(created_at);

CREATE INDEX IF NOT EXISTS idx_whale_tenants_is_deleted ON whale_tenants(is_deleted);



-- seed: whale_tenants
INSERT INTO "whale_tenants" ("id", "name", "code", "description", "is_active", "extra_properties", "created_at", "updated_at", "created_by", "updated_by", "is_deleted", "deleted_at", "deleted_by")
VALUES
('1', '默认租户', 'DEFAULT', '系统默认租户，用于单租户或主租户场景', true, NULL, '2026-05-02 22:07:04.556+00', '2026-05-02 22:07:04.556+00', '1', '1', false, NULL, NULL)
ON CONFLICT ("id") DO NOTHING;

-- ========== whale_user_tenants ==========
CREATE TABLE IF NOT EXISTS whale_user_tenants (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT false,
    deleted_at TIMESTAMP,
    deleted_by BIGINT,
    CONSTRAINT uk_user_tenant UNIQUE (user_id, tenant_id)
);

CREATE INDEX IF NOT EXISTS idx_whale_user_tenants_user_id ON whale_user_tenants(user_id);

CREATE INDEX IF NOT EXISTS idx_whale_user_tenants_tenant_id ON whale_user_tenants(tenant_id);

CREATE INDEX IF NOT EXISTS idx_whale_user_tenants_is_deleted ON whale_user_tenants(is_deleted);



-- seed: whale_user_tenants
INSERT INTO "whale_user_tenants" ("id", "user_id", "tenant_id", "created_at", "updated_at", "created_by", "updated_by", "is_deleted", "deleted_at", "deleted_by")
VALUES
('5', '1', '1', '2026-05-02 22:07:04.564+00', '2026-05-02 22:07:04.564+00', '1', NULL, false, NULL, NULL),
('322934748440170496', '322932211309350912', '1', '2026-06-10 03:07:23.865+00', '2026-06-10 03:07:23.865+00', '1', '1', false, NULL, NULL),
('322934748461142016', '322932096091820032', '1', '2026-06-10 03:07:23.870+00', '2026-06-10 03:07:23.870+00', '1', '1', false, NULL, NULL),
('322934748473724928', '322932010666430464', '1', '2026-06-10 03:07:23.873+00', '2026-06-10 03:07:23.873+00', '1', '1', false, NULL, NULL),
('322963602188275712', '322963602154721280', '1', '2026-06-10 05:02:03.135+00', '2026-06-10 05:02:03.135+00', '1', '1', false, NULL, NULL),
('322965022861955072', '322965022853566464', '1', '2026-06-10 05:07:41.850+00', '2026-06-10 05:07:41.850+00', '1', '1', false, NULL, NULL),
('323022664205602816', '323022664197214208', '1', '2026-06-10 08:56:44.617+00', '2026-06-10 08:56:44.617+00', '1', '1', false, NULL, NULL)
ON CONFLICT ("id") DO NOTHING;

-- ========== whale_settings ==========
CREATE TABLE IF NOT EXISTS whale_settings (
    id BIGINT PRIMARY KEY,
    name VARCHAR(256) NOT NULL,
    value TEXT,
    provider_name VARCHAR(64),
    provider_key VARCHAR(256),
    tenant_id BIGINT,
    description VARCHAR(512),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,
    deleted_by BIGINT
);

CREATE INDEX IF NOT EXISTS idx_whale_settings_provider_name ON whale_settings(provider_name);

CREATE INDEX IF NOT EXISTS idx_whale_settings_provider_key ON whale_settings(provider_key);

CREATE INDEX IF NOT EXISTS idx_whale_settings_tenant_id ON whale_settings(tenant_id);

CREATE INDEX IF NOT EXISTS idx_whale_settings_created_at ON whale_settings(created_at);

CREATE INDEX IF NOT EXISTS idx_whale_settings_is_deleted ON whale_settings(is_deleted);



-- seed: whale_settings
INSERT INTO "whale_settings" ("id", "name", "value", "provider_name", "provider_key", "tenant_id", "description", "created_at", "updated_at", "created_by", "updated_by", "is_deleted", "deleted_at", "deleted_by")
VALUES
('322347974589943808', 'ybSwich', '0', 'G', NULL, '1', '医保开关', '2026-06-08 12:15:46.078+00', '2026-06-10 07:14:45.901+00', '1', '1', false, NULL, NULL),
('322983522708492288', 'deviceAutoInventoryDeductSwich', '1', 'G', NULL, '1', '耗材是否自动扣库', '2026-06-10 06:21:12.557+00', '2026-06-10 06:21:12.557+00', '1', '1', false, NULL, NULL)
ON CONFLICT ("id") DO NOTHING;

-- ========== whale_menus ==========
CREATE TABLE IF NOT EXISTS whale_menus (
    id BIGINT PRIMARY KEY,
    code VARCHAR(128) NOT NULL UNIQUE,
    name VARCHAR(256) NOT NULL,
    display_name VARCHAR(256),
    icon VARCHAR(128),
    path VARCHAR(512),
    component VARCHAR(512),
    permission_name VARCHAR(256),
    parent_id BIGINT,
    sort_order INTEGER DEFAULT 0,
    is_enabled BOOLEAN NOT NULL DEFAULT true,
    is_hidden BOOLEAN NOT NULL DEFAULT false,
    is_external BOOLEAN NOT NULL DEFAULT false,
    external_url VARCHAR(512),
    menu_type VARCHAR(32) NOT NULL DEFAULT 'menu',
    remark VARCHAR(512),
    tenant_id BIGINT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT false,
    deleted_at TIMESTAMP,
    deleted_by BIGINT,
    keep_alive BOOLEAN,
    CONSTRAINT fk_whale_menus_parent FOREIGN KEY (parent_id) REFERENCES whale_menus(id)
);

CREATE INDEX IF NOT EXISTS idx_whale_menus_parent_id ON whale_menus(parent_id);

CREATE INDEX IF NOT EXISTS idx_whale_menus_tenant_id ON whale_menus(tenant_id);

CREATE INDEX IF NOT EXISTS idx_whale_menus_menu_type ON whale_menus(menu_type);

CREATE INDEX IF NOT EXISTS idx_whale_menus_is_enabled ON whale_menus(is_enabled);



-- seed: whale_menus
INSERT INTO "whale_menus" ("id", "code", "name", "display_name", "icon", "path", "component", "permission_name", "parent_id", "sort_order", "is_enabled", "is_hidden", "is_external", "external_url", "menu_type", "remark", "tenant_id", "created_at", "updated_at", "created_by", "updated_by", "is_deleted", "deleted_at", "deleted_by", "keep_alive")
VALUES
('1000', 'system_management', 'SystemManagement', '系统管理', 'lucide:folder-tree', '/system', '', NULL, NULL, 1, true, false, false, '', 'directory', '系统管理目录', NULL, '2026-05-26 20:00:00.000+00', '2026-05-26 20:00:00.000+00', '1', '1', false, NULL, NULL, true),
('1001', 'user_management', 'UserManagement', '用户管理', 'lucide:user', '/system/user', '/system/user/index', 'Navigation.UserManagement', '1000', 1, true, false, false, '', 'menu', '', NULL, '2026-05-26 20:00:00.000+00', '2026-05-26 20:00:00.000+00', '1', '1', false, NULL, NULL, true),
('1002', 'role_management', 'RoleManagement', '角色管理', 'lucide:shield-check', '/system/user/role', '/system/user/role/index', 'Navigation.RoleManagement', '1000', 2, true, false, false, '', 'menu', '', NULL, '2026-05-26 20:00:00.000+00', '2026-05-26 20:00:00.000+00', '1', '1', false, NULL, NULL, true),
('1004', 'permission_management', 'PermissionManagement', '权限管理', 'lucide:fingerprint-pattern', '/system/user/permission', '/system/user/permission/index', 'Navigation.PermissionManagement', '1000', 3, true, false, false, '', 'menu', '', NULL, '2026-05-26 20:00:00.000+00', '2026-05-26 20:00:00.000+00', '1', '1', false, NULL, NULL, true),
('1005', 'tenant_management', 'TenantManagement', '租户管理', 'lucide:square-user-round', '/system/tenant', '/system/tenant/index', 'Navigation.TenantManagement', '1000', 4, true, false, false, '', 'menu', '', NULL, '2026-05-26 20:00:00.000+00', '2026-05-26 20:00:00.000+00', '1', '1', false, NULL, NULL, true),
('1006', 'menu_management', 'MenuManagement', '菜单管理', 'lucide:square-menu', '/system/menu', '/system/menu/index', 'Navigation.MenuManagement', '1000', 5, true, false, false, '', 'menu', '', NULL, '2026-05-26 20:00:00.000+00', '2026-05-26 20:00:00.000+00', '1', '1', false, NULL, NULL, true),
('1007', 'setting_management', 'SettingManagement', '设置管理', 'lucide:settings', '/system/settings', '/system/settings/index', 'Navigation.SettingManagement', '1000', 6, true, false, false, '', 'menu', '', NULL, '2026-05-26 20:00:00.000+00', '2026-05-26 20:00:00.000+00', '1', '1', false, NULL, NULL, true),
('2000', 'basic_data', 'BasicData', '基础数据', 'lucide:database', '/basic', '', NULL, NULL, 2, true, false, false, '', 'directory', '基础数据目录', NULL, '2026-05-26 20:00:00.000+00', '2026-05-26 20:00:00.000+00', '1', '1', false, NULL, NULL, true),
('2001', 'codesystem_management', 'CodeSystemManagement', '编码管理', 'lucide:message-circle-code', '/basic/code', '/basic/code/index', 'Navigation.CodesystemManagement', '2000', 2, true, false, false, '', 'menu', '', NULL, '2026-05-26 20:00:00.000+00', '2026-05-26 20:00:00.000+00', '1', '1', false, NULL, NULL, true),
('2002', 'bodystructure_management', 'BodyStructureManagement', '部位管理', 'lucide:person-standing', '/basic/body', '/basic/body/index', 'Navigation.BodystructureManagement', '2000', 5, true, false, false, '', 'menu', '', NULL, '2026-05-26 20:00:00.000+00', '2026-05-26 20:00:00.000+00', '1', '1', false, NULL, NULL, true),
('2003', 'warehouse_management', 'WarehouseManagement', '库房药房', 'lucide:map-pinned', '/basic/warehouse', '/basic/warehouse/index', 'Navigation.WarehouseManagement', '2000', 7, true, false, false, '', 'menu', '', NULL, '2026-05-26 20:00:00.000+00', '2026-05-26 20:00:00.000+00', '1', '1', false, NULL, NULL, true),
('2004', 'ward_location_management', 'WardLocationManagement', '病区病房', 'lucide:circle-pile', '/basic/wardbed', '/basic/wardbed/index', 'Navigation.WardLocationManagement', '2000', 6, true, false, false, '', 'menu', '', NULL, '2026-05-26 20:00:00.000+00', '2026-05-26 20:00:00.000+00', '1', '1', false, NULL, NULL, true),
('2005', 'patient_management', 'PatientManagement', '患者管理', 'lucide:file-user', '/basic/patient', '/basic/patient/index', 'Navigation.PatientManagement', '2000', 3, true, false, false, '', 'menu', '', NULL, '2026-05-26 20:00:00.000+00', '2026-05-26 20:00:00.000+00', '1', '1', false, NULL, NULL, true),
('2007', 'dept_management', 'DeptManagement', '科室管理', 'lucide:building', '/basic/department', '/basic/department/index', 'Navigation.DeptManagement', '2000', 1, true, false, false, '', 'menu', '', NULL, '2026-05-26 20:00:00.000+00', '2026-05-26 20:00:00.000+00', '1', '1', false, NULL, NULL, true),
('2008', 'healthcare_service_management', 'HealthcareServiceManagement', '挂号类型管理', 'lucide:clipboard-list', '/basic/registrationtype', '/basic/registrationtype/index', 'Navigation.HealthcareServiceManagement', '2000', 11, true, false, false, '', 'menu', '', NULL, '2026-05-26 20:00:00.000+00', '2026-05-26 20:00:00.000+00', '1', '1', false, NULL, NULL, true),
('2010', 'contract_management', 'ContractManagement', '费用性质管理', 'lucide:credit-card', '/basic/chargetype', '/basic/chargetype/index', 'Navigation.ContractManagement', '2000', 10, true, false, false, '', 'menu', '', NULL, '2026-05-26 20:00:00.000+00', '2026-05-26 20:00:00.000+00', '1', '1', false, NULL, NULL, true),
('279418897187213312', 'Analytics', 'Analytics', '概览', 'lucide:chart-line', '/analytics', '/_core/analytics/index', 'Navigation.Analytics', NULL, 0, true, false, false, '', 'menu', '', NULL, '2026-05-26 20:00:00.000+00', '2026-05-26 20:00:00.000+00', '1', '1', false, NULL, NULL, true),
('284501743746682880', 'Frequency', 'Frequency', '频次管理', 'lucide:alarm-clock', '/basic/frequency', '/basic/frequency/index', 'Navigation.Frequency', '2000', 9, true, false, false, '', 'menu', '', NULL, '2026-05-26 20:00:00.000+00', '2026-05-26 20:00:00.000+00', '1', '1', false, NULL, NULL, true),
('284502213630365696', 'Supplier', 'Supplier', '供应商管理', 'lucide:truck', '/basic/supplier', '/basic/supplier/index', 'Navigation.Supplier', '2000', 8, true, false, false, '', 'menu', '', NULL, '2026-05-26 20:00:00.000+00', '2026-05-26 20:00:00.000+00', '1', '1', false, NULL, NULL, true),
('284503862625177600', 'Warehouse', 'Warehouse', '库房管理', 'lucide:warehouse', '/inventory', '', NULL, NULL, 9, true, false, false, '', 'directory', '', NULL, '2026-05-26 20:00:00.000+00', '2026-05-26 20:00:00.000+00', '1', '1', false, NULL, NULL, true),
('284517877903003648', 'Medicine', 'Medicine', '药品管理', 'lucide:pill', '/basic/medicationcatalog', '/basic/medicationcatalog/index', 'Navigation.Medicine', '2000', 13, true, false, false, '', 'menu', '', NULL, '2026-05-26 20:00:00.000+00', '2026-05-26 20:00:00.000+00', '1', '1', false, NULL, NULL, true),
('284518561658441728', 'Device', 'Device', '器材管理', 'lucide:syringe', '/basic/devicecatalog', '/basic/devicecatalog/index', 'Navigation.Device', '2000', 16, true, false, false, '', 'menu', '', NULL, '2026-05-26 20:00:00.000+00', '2026-05-26 20:00:00.000+00', '1', '1', false, NULL, NULL, true),
('284519152342274048', 'Diagnosistreatment', 'Diagnosistreatment', '诊疗管理', 'lucide:square-activity', '/basic/servicecatalog', '/basic/servicecatalog/index', 'Navigation.Diagnosistreatment', '2000', 15, true, false, false, '', 'menu', '', NULL, '2026-05-26 20:00:00.000+00', '2026-05-26 20:00:00.000+00', '1', '1', false, NULL, NULL, true),
('284519592148602880', 'Disease', 'Disease', '诊断', 'lucide:heart-pulse', '/basic/diagnosiscatalog', '/basic/diagnosiscatalog/index', 'Navigation.Disease', '2000', 12, true, false, false, '', 'menu', '', NULL, '2026-05-26 20:00:00.000+00', '2026-05-26 20:00:00.000+00', '1', '1', false, NULL, NULL, true),
('301665359451131904', 'OutpatientNurseStation', 'OutpatientNurseStation', '门诊护士站', 'lucide:clipboard-list', '/outpatient/nurse-station', '/outpatient/nurse-station/index', 'Navigation.Outpatientnursestation', NULL, 6, true, false, false, '', 'menu', '', NULL, '2026-05-26 20:00:00.000+00', '2026-05-26 20:00:00.000+00', '1', '1', false, NULL, NULL, true),
('301666596158443520', 'InpatientNurseStation', 'InpatientNurseStation', '住院护士站', 'lucide:bed-double', '/inpatient/nurse-station', '/inpatient/nurse-station/index', 'Navigation.Inpatientnursestation', NULL, 7, true, false, false, '', 'menu', '', NULL, '2026-05-26 20:00:00.000+00', '2026-05-26 20:00:00.000+00', '1', '1', false, NULL, NULL, true),
('301696493912330240', 'Pharmacy', 'Pharmacy', '药房管理', 'lucide:store', '/pharmacy', '', NULL, NULL, 10, true, false, false, '', 'directory', '', '1', '2026-05-26 20:00:00.000+00', '2026-05-26 20:00:00.000+00', '1', '1', false, NULL, NULL, true),
('301699662818054144', 'ReportFinance', 'ReportFinance', '财务报表', 'lucide:receipt-text', '/reports/finance', '/reports/finance/index', 'Navigation.Reportfinance', '310400000000000100', 4, true, false, false, '', 'menu', '', '1', '2026-05-26 20:00:00.000+00', '2026-05-26 20:00:00.000+00', '1', '1', false, NULL, NULL, true),
('310400000000000001', 'ChargeStation', 'ChargeStation', '收费工作站', 'lucide:banknote', '/charge', '', NULL, NULL, 3, true, false, false, '', 'directory', 'HIS清单-收费工作站', NULL, '2026-05-26 20:00:00.000+00', '2026-05-26 20:00:00.000+00', '1', '1', false, NULL, NULL, true),
('310400000000000002', 'ChargeOpRegister', 'ChargeOpRegister', '门诊挂号', 'lucide:calendar-plus', '/outpatient/charge/register', '/outpatient/charge/register/index', 'Navigation.Chargeopregister', '310400000000000001', 1, true, false, false, '', 'menu', '', NULL, '2026-05-26 20:00:00.000+00', '2026-05-26 20:00:00.000+00', '1', '1', false, NULL, NULL, true),
('310400000000000003', 'ChargeOpCharge', 'ChargeOpCharge', '门诊收费', 'lucide:circle-dollar-sign', '/outpatient/charge/charge', '/outpatient/charge/charge/index', 'Navigation.Chargeopcharge', '310400000000000001', 2, true, false, false, '', 'menu', '', NULL, '2026-05-26 20:00:00.000+00', '2026-05-26 20:00:00.000+00', '1', '1', false, NULL, NULL, true),
('310400000000000004', 'ChargeOpRefund', 'ChargeOpRefund', '门诊退费', 'lucide:undo-2', '/outpatient/charge/refund', '/outpatient/charge/refund/index', 'Navigation.Chargeoprefund', '310400000000000001', 3, true, false, false, '', 'menu', '', NULL, '2026-05-26 20:00:00.000+00', '2026-05-26 20:00:00.000+00', '1', '1', false, NULL, NULL, true),
('310400000000000005', 'ChargeOpDayClose', 'ChargeOpDayClose', '门诊日结', 'lucide:calendar-check', '/outpatient/charge/daily-settlement', '/outpatient/charge/daily-settlement/index', 'Navigation.Chargeopdayclose', '310400000000000001', 4, true, false, false, '', 'menu', '', NULL, '2026-05-26 20:00:00.000+00', '2026-05-26 20:00:00.000+00', '1', '1', false, NULL, NULL, true),
('310400000000000006', 'ChargeIpRegister', 'ChargeIpRegister', '住院登记', 'lucide:bed-single', '/inpatient/charge/register', '/inpatient/charge/register/index', 'Navigation.Chargeipregister', '310400000000000001', 5, true, false, false, '', 'menu', '', NULL, '2026-05-26 20:00:00.000+00', '2026-05-26 20:00:00.000+00', '1', '1', false, NULL, NULL, true),
('310400000000000007', 'ChargeIpSettle', 'ChargeIpSettle', '住院结算', 'lucide:wallet', '/inpatient/charge/settle', '/inpatient/charge/settle/index', 'Navigation.Chargeipsettle', '310400000000000001', 6, true, false, false, '', 'menu', '', NULL, '2026-05-26 20:00:00.000+00', '2026-05-26 20:00:00.000+00', '1', '1', false, NULL, NULL, true),
('310400000000000008', 'ChargeIpPrepay', 'ChargeIpPrepay', '预交金管理', 'lucide:coins', '/inpatient/charge/prepay', '/inpatient/charge/prepay/index', 'Navigation.Chargeipprepay', '310400000000000001', 7, true, false, false, '', 'menu', '', NULL, '2026-05-26 20:00:00.000+00', '2026-05-26 20:00:00.000+00', '1', '1', false, NULL, NULL, true),
('310400000000000009', 'ChargeIpFee', 'ChargeIpFee', '住院费用管理', 'lucide:list-todo', '/inpatient/charge/fee', '/inpatient/charge/fee/index', 'Navigation.Chargeipfee', '310400000000000001', 8, true, false, false, '', 'menu', '', NULL, '2026-05-26 20:00:00.000+00', '2026-05-26 20:00:00.000+00', '1', '1', false, NULL, NULL, true),
('310400000000000117', 'ChargeIpDayClose', 'ChargeIpDayClose', '住院日结', 'lucide:calendar-check', '/inpatient/charge/daily-settlement', '/inpatient/charge/daily-settlement/index', 'Navigation.Chargeipdayclose', '310400000000000001', 9, true, false, false, '', 'menu', '住院收费日结交账', NULL, '2026-06-21 00:00:00.000+00', '2026-06-21 00:00:00.000+00', '1', '1', false, NULL, NULL, true),
('310400000000000118', 'DispenseRecordOp', 'DispenseRecordOp', '门诊发退药记录', 'lucide:clipboard-list', '/pharmacy/dispense-record/outpatient', '/pharmacy/dispense-record/outpatient/index', 'Navigation.Dispenserecordop', '301696493912330240', 3, true, false, false, '', 'menu', '门诊发退药记录查询与导出', NULL, '2026-06-21 00:00:00.000+00', '2026-06-21 00:00:00.000+00', '1', '1', false, NULL, NULL, true),
('310400000000000119', 'DispenseRecordIp', 'DispenseRecordIp', '住院发退药记录', 'lucide:clipboard-list', '/pharmacy/dispense-record/inpatient', '/pharmacy/dispense-record/inpatient/index', 'Navigation.Dispenserecordip', '301696493912330240', 4, true, false, false, '', 'menu', '住院发退药记录查询与导出', NULL, '2026-06-21 00:00:00.000+00', '2026-06-21 00:00:00.000+00', '1', '1', false, NULL, NULL, true),
('310400000000000120', 'OpReport', 'OpReport', '门诊报表', 'lucide:file-bar-chart', '/outpatient/report', '', NULL, '310400000000000001', 10, true, false, false, '', 'directory', '门诊域报表（费用明细等）', NULL, '2026-06-21 00:00:00.000+00', '2026-06-21 00:00:00.000+00', '1', '1', false, NULL, NULL, true),
('310400000000000121', 'OpFeeDetail', 'OpFeeDetail', '门诊费用明细', 'lucide:receipt', '/outpatient/report/fee-detail', '/outpatient/report/fee-detail/index', 'Navigation.Opfeedetail', '310400000000000120', 1, true, false, false, '', 'menu', '门诊费用明细查询与导出', NULL, '2026-06-21 00:00:00.000+00', '2026-06-21 00:00:00.000+00', '1', '1', false, NULL, NULL, true),
('310400000000000020', 'OutpatientDoctorStation', 'OutpatientDoctorStation', '门诊医生站', 'lucide:user-round-pen', '/outpatient/doctor-station', '/outpatient/doctor-station/index', 'Navigation.Outpatientdoctorstation', NULL, 4, true, false, false, '', 'menu', '', NULL, '2026-05-26 20:00:00.000+00', '2026-05-26 20:00:00.000+00', '1', '1', false, NULL, NULL, true),
('310400000000000040', 'InpatientDoctorStation', 'InpatientDoctorStation', '住院医生站', 'lucide:hospital', '/inpatient/doctor-station', '/inpatient/doctor-station/index', 'Navigation.Inpatientdoctorstation', NULL, 5, true, false, false, '', 'menu', '', NULL, '2026-05-26 20:00:00.000+00', '2026-05-26 20:00:00.000+00', '1', '1', false, NULL, NULL, true),
('310400000000000060', 'MedicationDispense', 'MedicationDispense', '发药管理', 'lucide:pill-bottle', '/pharmacy/dispense', '', NULL, NULL, 8, true, false, false, '', 'directory', 'HIS清单-发药管理', NULL, '2026-05-26 20:00:00.000+00', '2026-05-26 20:00:00.000+00', '1', '1', false, NULL, NULL, true),
('310400000000000061', 'DispenseOp', 'DispenseOp', '门诊发药', 'lucide:package-open', '/pharmacy/dispense/outpatient', '/pharmacy/dispense/outpatient/index', 'Navigation.Dispenseop', '310400000000000060', 1, true, false, false, '', 'menu', '', NULL, '2026-05-26 20:00:00.000+00', '2026-05-26 20:00:00.000+00', '1', '1', false, NULL, NULL, true),
('310400000000000062', 'DispenseIp', 'DispenseIp', '住院发药', 'lucide:truck', '/pharmacy/dispense/inpatient', '/pharmacy/dispense/inpatient/index', 'Navigation.Dispenseip', '310400000000000060', 2, true, false, false, '', 'menu', '', NULL, '2026-05-26 20:00:00.000+00', '2026-05-26 20:00:00.000+00', '1', '1', false, NULL, NULL, true),
('310400000000000063', 'DispenseDoc', 'DispenseDoc', '发药单据', 'lucide:file-box', '/pharmacy/dispense/documents', '/pharmacy/dispense/documents/index', 'Navigation.Dispensedoc', '310400000000000060', 3, true, false, false, '', 'menu', '', NULL, '2026-05-26 20:00:00.000+00', '2026-05-26 20:00:00.000+00', '1', '1', false, NULL, NULL, true),
('310400000000000081', 'WhPurchaseOrder', 'WhPurchaseOrder', '药库订货单', 'lucide:file-spreadsheet', '/inventory/warehouse/purchase-order', '/inventory/warehouse/purchase-order/index', 'Navigation.Whpurchaseorder', '284503862625177600', 1, true, false, false, '', 'menu', '', NULL, '2026-05-26 20:00:00.000+00', '2026-05-26 20:00:00.000+00', '1', '1', false, NULL, NULL, true),
('310400000000000100', 'ReportManagement', 'ReportManagement', '报表管理', 'lucide:layout-dashboard', '/reports', '', NULL, NULL, 11, true, false, false, '', 'directory', 'HIS清单-报表管理', NULL, '2026-05-26 20:00:00.000+00', '2026-05-26 20:00:00.000+00', '1', '1', false, NULL, NULL, true),
('310400000000000101', 'ReportHome', 'ReportHome', '首页', 'lucide:home', '/reports/home', '/reports/home/index', 'Navigation.Reporthome', '310400000000000100', 1, true, false, false, '', 'menu', '', NULL, '2026-05-26 20:00:00.000+00', '2026-05-26 20:00:00.000+00', '1', '1', false, NULL, NULL, true),
('310400000000000102', 'ReportDean', 'ReportDean', '院长', 'lucide:building-2', '/reports/dean', '/reports/dean/index', 'Navigation.Reportdean', '310400000000000100', 2, true, false, false, '', 'menu', '', NULL, '2026-05-26 20:00:00.000+00', '2026-05-26 20:00:00.000+00', '1', '1', false, NULL, NULL, true),
('310400000000000103', 'ReportClinical', 'ReportClinical', '临床报表', 'lucide:activity', '/reports/clinical', '/reports/clinical/index', 'Navigation.Reportclinical', '310400000000000100', 3, true, false, false, '', 'menu', '', NULL, '2026-05-26 20:00:00.000+00', '2026-05-26 20:00:00.000+00', '1', '1', false, NULL, NULL, true),
('310500000000000100', 'ChsManagement', 'ChsManagement', '医保管理', 'lucide:shield', '/chs', '', '', NULL, 12, true, false, false, '', 'directory', 'CHS 医保扩展菜单', NULL, '2026-06-01 16:00:00.000+00', '2026-06-01 16:00:00.000+00', '1', '1', false, NULL, NULL, true),
('310500000000000101', 'ChsNationalCatalog', 'ChsNationalCatalog', '国家目录查询', 'lucide:book-open', '/chs/national-catalog', '/chs/national-catalog/index', 'Navigation.Chsnationalcatalog', '310500000000000100', 1, true, false, false, '', 'menu', '1312/1301 国家医保目录查询与下载', NULL, '2026-06-01 16:00:00.000+00', '2026-06-01 16:00:00.000+00', '1', '1', false, NULL, NULL, true),
('310500000000000102', 'ChsCatalogDownload', 'ChsCatalogDownload', '目录文件下载', 'lucide:download', '/chs/catalog-download', '/chs/catalog-download/index', 'Navigation.Chscatalogdownload', '310500000000000100', 2, true, false, false, '', 'menu', '1301/1305/1306 + 9102 文件下载解压入库', NULL, '2026-06-01 16:00:00.000+00', '2026-06-01 16:00:00.000+00', '1', '1', false, NULL, NULL, true),
('310600000000000101', 'PrintTemplate', 'PrintTemplate', '打印模板', 'lucide:printer', '/basic/print-template', '/basic/print-template/index', 'Print.Templates.View', '2000', 25, true, false, false, '', 'menu', 'PrintJson 模板列表（新建/发布/复制草稿）；设计请用「打印设计器」菜单', NULL, '2026-06-03 16:00:00.000+00', '2026-06-25 00:00:00.000+00', '1', '1', false, NULL, NULL, true),
('310600000000000102', 'PrintTemplateDesigner', 'PrintTemplateDesigner', '打印设计器', 'lucide:layout-template', '/basic/print-design', '/basic/print-template/designer/index', 'Print.Templates.Design', '2000', 24, true, false, false, '', 'menu', '独立 hiprint 设计器；纯画布编辑，与业务 binding/发印无关', NULL, '2026-06-22 00:00:00.000+00', '2026-06-25 00:00:00.000+00', '1', '1', false, NULL, NULL, false),
('310600000000000103', 'ReportPrintLog', 'ReportPrintLog', '打印日志', 'lucide:scroll-text', '/basic/print-log', '/basic/print-log/index', 'Print.PrintLog.View', '2000', 27, true, false, false, '', 'menu', 'PRT 打印审计 prt_print_event_log', NULL, '2026-06-03 16:00:00.000+00', '2026-06-22 00:00:00.000+00', '1', '1', false, NULL, NULL, true),
('310600000000000104', 'ReportPrintBinding', 'ReportPrintBinding', '打印模板配置', 'lucide:link-2', '/basic/print-binding', '/basic/print-binding/index', 'Print.PrintBinding.View', '2000', 26, true, false, false, '', 'menu', 'PRT bindingCode→busNo 租户绑定', NULL, '2026-06-22 00:00:00.000+00', '2026-06-22 00:00:00.000+00', '1', '1', false, NULL, NULL, true),
('310600000000000111', 'ReportDesigner', 'ReportDesigner', '报表设计器', 'lucide:pen-line', '/reports/designer', '/report/designer/index', 'Report.Designer.View', '310400000000000100', 4, true, true, false, '', 'menu', 'C 类报表配置、PDF 模板绑定与发布（draft → published）', NULL, '2026-06-03 16:00:00.000+00', '2026-06-03 16:00:00.000+00', '1', '1', false, NULL, NULL, true),
('312881978679496704', 'WhPurchaseInbound', 'WhPurchaseInbound', '药库进货单', 'lucide:package-check', '/inventory/warehouse/purchase-inbound', '/inventory/warehouse/purchase-inbound/index', 'Navigation.Whpurchaseinbound', '284503862625177600', 2, true, false, false, '', 'menu', '', NULL, '2026-05-26 20:00:00.000+00', '2026-05-26 20:00:00.000+00', '1', '1', false, NULL, NULL, true),
('313234941086273536', 'WhStockOut', 'WhStockOut', '药库出库单', 'lucide:package-minus', '/inventory/warehouse/stock-out', '/inventory/warehouse/stock-out/index', 'Navigation.Whstockout', '284503862625177600', 3, true, false, false, '', 'menu', '', NULL, '2026-05-26 20:00:00.000+00', '2026-05-26 20:00:00.000+00', '1', '1', false, NULL, NULL, true),
('313295483593297921', 'PhStockIn', 'PhStockIn', '药房入库单', 'lucide:package-plus', '/inventory/pharmacy/stock-in', '/inventory/pharmacy/stock-in/index', 'Navigation.Phstockin', '301696493912330240', 1, true, false, false, '', 'menu', '', '1', '2026-05-26 20:00:00.000+00', '2026-05-26 20:00:00.000+00', '1', '1', false, NULL, NULL, true),
('313570119616827392', 'InvStockList', 'InvStockList', '库存明细', 'lucide:rows-3', '/inventory/stock-list', '/inventory/stock-list/index', 'Navigation.Invstocklist', '284503862625177600', 5, true, false, false, '', 'menu', '', NULL, '2026-05-26 20:00:00.000+00', '2026-05-26 20:00:00.000+00', '1', '1', false, NULL, NULL, true),
('313945127476793344', 'MedicineReturnOp', 'MedicineReturnOp', '门诊退药', 'lucide:truck', '/pharmacy/return-medicine/outpatient', '/pharmacy/return-medicine/outpatient/index', 'Navigation.Medicinereturnop', '310400000000000060', 4, true, false, false, '', 'menu', '', NULL, '2026-05-26 20:00:00.000+00', '2026-05-26 20:00:00.000+00', '1', '1', false, NULL, NULL, true),
('310400000000000064', 'MedicineReturnIp', 'MedicineReturnIp', '住院退药', 'lucide:undo-2', '/pharmacy/return-medicine/inpatient', '/pharmacy/return-medicine/inpatient/index', 'Navigation.Medicinereturnip', '310400000000000060', 5, true, false, false, '', 'menu', '', NULL, '2026-06-11 00:00:00.000+00', '2026-06-11 00:00:00.000+00', '1', '1', false, NULL, NULL, true),
('315372803691384832', 'EmrTemplate', 'EmrTemplate', '病历模版', 'lucide:file-text', '/basic/emr-template', '/basic/emr-template/index', 'Navigation.Emrtemplate', '2000', 18, true, false, false, '', 'menu', '', NULL, '2026-05-26 20:00:00.000+00', '2026-05-26 20:00:00.000+00', '1', '1', false, NULL, NULL, true),
('316800463887929340', 'WhStocktakeOrder', 'WhStocktakeOrder', '药库盘点单', 'lucide:clipboard-list', '/inventory/warehouse/stocktake', '/inventory/warehouse/stocktake/index', 'Navigation.Whstocktakeorder', '284503862625177600', 4, true, false, false, '', 'menu', '', NULL, '2026-05-26 20:00:00.000+00', '2026-05-26 20:00:00.000+00', '1', '1', false, NULL, NULL, true),
('316800463887929341', 'PhStocktakeOrder', 'PhStocktakeOrder', '药房盘点单', 'lucide:clipboard-check', '/inventory/pharmacy/stocktake', '/inventory/pharmacy/stocktake/index', 'Navigation.Phstocktakeorder', '301696493912330240', 2, true, false, false, '', 'menu', '', '1', '2026-05-26 20:00:00.000+00', '2026-05-26 20:00:00.000+00', '1', '1', false, NULL, NULL, true),
('316900463887929346', 'ServiceDeviceBinding', 'ServiceDeviceBinding', '诊疗耗材绑定', 'lucide:link-2', '/basic/servicedevicebinding', '/basic/servicedevicebinding/index', 'Navigation.ActivityDeviceBinding', '2000', 23, true, false, false, '', 'menu', '基础数据-诊疗耗材绑定', NULL, '2026-05-26 20:00:00.000+00', '2026-06-04 16:00:00.000+00', '1', '1', false, NULL, NULL, true),
('316900463887929347', 'ExecuteLocation', 'ExecuteLocation', '执行地点', 'lucide:map-pin', '/basic/executelocation', '/basic/executelocation/index', 'Navigation.Executelocation', '2000', 20, true, false, false, '', 'menu', '', NULL, '2026-05-26 20:00:00.000+00', '2026-05-26 20:00:00.000+00', '1', '1', false, NULL, NULL, true),
('316900463887929348', 'order_template', 'OrderTemplate', '医嘱组套', 'lucide:layers', '/basic/ordertemplate', '/basic/ordertemplate/index', 'Navigation.OrderTemplate', '2000', 21, true, false, false, '', 'menu', '基础数据-医嘱组套模板', NULL, '2026-06-02 09:46:21.106+00', '2026-05-26 20:00:00.000+00', '1', '1', false, NULL, NULL, true),
('316900463887929349', 'service_location', 'ServiceLocation', '取药科室', 'lucide:pill-bottle', '/basic/servicelocation', '/basic/servicelocation/index', 'Navigation.ServiceLocation', '2000', 22, true, false, false, '', 'menu', '基础数据-取药科室配置', NULL, '2026-06-02 09:46:21.106+00', '2026-05-26 20:00:00.000+00', '1', '1', false, NULL, NULL, true),
('317000463887929350', 'specimen_definition', 'SpecimenDefinition', '样本管理', 'lucide:test-tubes', '/basic/specimendefinition', '/basic/specimendefinition/index', 'Navigation.SpecimenDefinition', '2000', 24, true, false, false, '', 'menu', '基础数据-样本定义维护（SpecimenDefinitionAppService）', NULL, '2026-06-12 08:18:12.580+00', '2026-06-12 08:18:12.580+00', '1', '1', false, NULL, NULL, true)
ON CONFLICT ("id") DO NOTHING;

-- ========== whale_sequence ==========
CREATE TABLE IF NOT EXISTS whale_sequence (
    seq_key       VARCHAR(255) NOT NULL PRIMARY KEY,
    current_value BIGINT      NOT NULL DEFAULT 0,
    updated_at    TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_whale_sequence_updated_at ON whale_sequence(updated_at);

-- (empty) 业务序号由运行时递增，不预置

-- ========== whale_audit_logs ==========
CREATE TABLE IF NOT EXISTS whale_audit_logs (
    id BIGINT PRIMARY KEY,
    entity_type VARCHAR(256) NOT NULL,
    entity_id BIGINT NOT NULL,
    operation_type VARCHAR(32) NOT NULL,
    user_id BIGINT,
    tenant_id BIGINT,
    ip_address VARCHAR(64),
    user_agent VARCHAR(512),
    changes TEXT,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_audit_logs_entity_type ON whale_audit_logs(entity_type);

CREATE INDEX IF NOT EXISTS idx_audit_logs_entity_id ON whale_audit_logs(entity_id);

CREATE INDEX IF NOT EXISTS idx_audit_logs_created_at ON whale_audit_logs(created_at DESC);

CREATE INDEX IF NOT EXISTS idx_audit_logs_user_id ON whale_audit_logs(user_id);

-- ========== whale_framework_runtime_state ==========
-- 框架运行时状态快照（whale-runtime 中心节点收集，无种子）
CREATE TABLE IF NOT EXISTS whale_framework_runtime_state (
    id BIGINT PRIMARY KEY,
    insight_code VARCHAR(128) NOT NULL,
    category VARCHAR(64),
    runtime_version VARCHAR(64),
    operator_user_id BIGINT,
    session_started_at TIMESTAMP,
    org_category VARCHAR(128),
    org_name VARCHAR(256),
    deployment_fingerprint VARCHAR(128),
    license_customer VARCHAR(256),
    license_not_after TIMESTAMP,
    payload_json TEXT,
    observed_at TIMESTAMP,
    received_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_fw_runtime_state_insight_code ON whale_framework_runtime_state(insight_code);

CREATE INDEX IF NOT EXISTS idx_fw_runtime_state_fingerprint ON whale_framework_runtime_state(deployment_fingerprint);

CREATE INDEX IF NOT EXISTS idx_fw_runtime_state_received_at ON whale_framework_runtime_state(received_at DESC);

-- ========== 表/列注释（须在 CREATE TABLE 之后执行）==========

COMMENT ON TABLE whale_roles IS '系统角色';
COMMENT ON COLUMN whale_roles.id IS '主键';
COMMENT ON COLUMN whale_roles.name IS '角色名称（唯一）';
COMMENT ON COLUMN whale_roles.display_name IS '显示名称';
COMMENT ON COLUMN whale_roles.code IS '角色编码';
COMMENT ON COLUMN whale_roles.description IS '描述';
COMMENT ON COLUMN whale_roles.is_default IS '是否默认角色';
COMMENT ON COLUMN whale_roles.is_static IS '是否静态角色（不可删）';
COMMENT ON COLUMN whale_roles.tenant_id IS '租户标识';
COMMENT ON COLUMN whale_roles.created_at IS '创建时间';
COMMENT ON COLUMN whale_roles.updated_at IS '最后更新时间';
COMMENT ON COLUMN whale_roles.created_by IS '创建人';
COMMENT ON COLUMN whale_roles.updated_by IS '最后更新人';
COMMENT ON COLUMN whale_roles.is_deleted IS '软删标记';
COMMENT ON COLUMN whale_roles.deleted_at IS '删除时间';
COMMENT ON COLUMN whale_roles.deleted_by IS '删除人';

COMMENT ON TABLE whale_user_roles IS '用户角色关联';
COMMENT ON COLUMN whale_user_roles.id IS '主键';
COMMENT ON COLUMN whale_user_roles.user_id IS '用户标识';
COMMENT ON COLUMN whale_user_roles.role_id IS '角色标识';
COMMENT ON COLUMN whale_user_roles.tenant_id IS '租户标识';
COMMENT ON COLUMN whale_user_roles.created_at IS '创建时间';
COMMENT ON COLUMN whale_user_roles.updated_at IS '最后更新时间';
COMMENT ON COLUMN whale_user_roles.created_by IS '创建人';
COMMENT ON COLUMN whale_user_roles.updated_by IS '最后更新人';
COMMENT ON COLUMN whale_user_roles.is_deleted IS '软删标记';
COMMENT ON COLUMN whale_user_roles.deleted_at IS '删除时间';
COMMENT ON COLUMN whale_user_roles.deleted_by IS '删除人';

COMMENT ON TABLE whale_permissions IS '权限定义';
COMMENT ON COLUMN whale_permissions.id IS '主键';
COMMENT ON COLUMN whale_permissions.name IS '权限名称（唯一）';
COMMENT ON COLUMN whale_permissions.display_name IS '显示名称';
COMMENT ON COLUMN whale_permissions.parent_name IS '父权限名称';
COMMENT ON COLUMN whale_permissions.description IS '描述';
COMMENT ON COLUMN whale_permissions.tenant_id IS '租户标识';
COMMENT ON COLUMN whale_permissions.created_at IS '创建时间';
COMMENT ON COLUMN whale_permissions.updated_at IS '最后更新时间';
COMMENT ON COLUMN whale_permissions.created_by IS '创建人';
COMMENT ON COLUMN whale_permissions.updated_by IS '最后更新人';
COMMENT ON COLUMN whale_permissions.is_deleted IS '软删标记';
COMMENT ON COLUMN whale_permissions.deleted_at IS '删除时间';
COMMENT ON COLUMN whale_permissions.deleted_by IS '删除人';

COMMENT ON TABLE whale_permission_grants IS '权限授权';
COMMENT ON COLUMN whale_permission_grants.id IS '主键';
COMMENT ON COLUMN whale_permission_grants.permission_name IS '权限名称';
COMMENT ON COLUMN whale_permission_grants.provider_name IS '授权主体类型（R=角色/U=用户）';
COMMENT ON COLUMN whale_permission_grants.provider_key IS '授权主体键';
COMMENT ON COLUMN whale_permission_grants.tenant_id IS '租户标识';
COMMENT ON COLUMN whale_permission_grants.created_at IS '创建时间';
COMMENT ON COLUMN whale_permission_grants.updated_at IS '最后更新时间';
COMMENT ON COLUMN whale_permission_grants.created_by IS '创建人';
COMMENT ON COLUMN whale_permission_grants.updated_by IS '最后更新人';
COMMENT ON COLUMN whale_permission_grants.is_deleted IS '软删标记';
COMMENT ON COLUMN whale_permission_grants.deleted_at IS '删除时间';
COMMENT ON COLUMN whale_permission_grants.deleted_by IS '删除人';

COMMENT ON TABLE whale_security_logs IS '安全审计日志';
COMMENT ON COLUMN whale_security_logs.id IS '主键';
COMMENT ON COLUMN whale_security_logs.user_id IS '用户标识';
COMMENT ON COLUMN whale_security_logs.user_name IS '用户名称';
COMMENT ON COLUMN whale_security_logs.action IS '操作动作';
COMMENT ON COLUMN whale_security_logs.client_ip IS '客户端ip';
COMMENT ON COLUMN whale_security_logs.user_agent IS '用户agent';
COMMENT ON COLUMN whale_security_logs.result IS '结果';
COMMENT ON COLUMN whale_security_logs.failure_reason IS 'failure原因';
COMMENT ON COLUMN whale_security_logs.tenant_id IS '租户标识';
COMMENT ON COLUMN whale_security_logs.extra_properties IS '扩展属性 JSON';
COMMENT ON COLUMN whale_security_logs.created_at IS '创建时间';
COMMENT ON COLUMN whale_security_logs.updated_at IS '最后更新时间';
COMMENT ON COLUMN whale_security_logs.created_by IS '创建人';
COMMENT ON COLUMN whale_security_logs.updated_by IS '最后更新人';
COMMENT ON COLUMN whale_security_logs.is_deleted IS '软删标记';
COMMENT ON COLUMN whale_security_logs.deleted_at IS '删除时间';
COMMENT ON COLUMN whale_security_logs.deleted_by IS '删除人';

COMMENT ON TABLE whale_organization_units IS '组织机构单元';
COMMENT ON COLUMN whale_organization_units.id IS '主键';
COMMENT ON COLUMN whale_organization_units.code IS '编码';
COMMENT ON COLUMN whale_organization_units.display_name IS '显示名称';
COMMENT ON COLUMN whale_organization_units.parent_id IS '父节点标识';
COMMENT ON COLUMN whale_organization_units.sort_order IS '排序号';
COMMENT ON COLUMN whale_organization_units.is_enabled IS '是否启用，语义等同 { PublicationStatus.ACTIVE}（见 health-domain-shared）';
COMMENT ON COLUMN whale_organization_units.remark IS '备注';
COMMENT ON COLUMN whale_organization_units.tenant_id IS '租户标识';
COMMENT ON COLUMN whale_organization_units.created_at IS '创建时间';
COMMENT ON COLUMN whale_organization_units.updated_at IS '最后更新时间';
COMMENT ON COLUMN whale_organization_units.created_by IS '创建人';
COMMENT ON COLUMN whale_organization_units.updated_by IS '最后更新人';
COMMENT ON COLUMN whale_organization_units.is_deleted IS '软删标记';
COMMENT ON COLUMN whale_organization_units.deleted_at IS '删除时间';
COMMENT ON COLUMN whale_organization_units.deleted_by IS '删除人';

COMMENT ON TABLE whale_user_organization_units IS '用户组织单元关联';
COMMENT ON COLUMN whale_user_organization_units.id IS '主键';
COMMENT ON COLUMN whale_user_organization_units.user_id IS '用户标识';
COMMENT ON COLUMN whale_user_organization_units.organization_unit_id IS '组织单元标识';
COMMENT ON COLUMN whale_user_organization_units.tenant_id IS '租户标识';
COMMENT ON COLUMN whale_user_organization_units.created_at IS '创建时间';
COMMENT ON COLUMN whale_user_organization_units.updated_at IS '最后更新时间';
COMMENT ON COLUMN whale_user_organization_units.created_by IS '创建人';
COMMENT ON COLUMN whale_user_organization_units.updated_by IS '最后更新人';
COMMENT ON COLUMN whale_user_organization_units.is_deleted IS '软删标记';
COMMENT ON COLUMN whale_user_organization_units.deleted_at IS '删除时间';
COMMENT ON COLUMN whale_user_organization_units.deleted_by IS '删除人';

COMMENT ON TABLE whale_user_claims IS '用户声明';
COMMENT ON COLUMN whale_user_claims.id IS '主键';
COMMENT ON COLUMN whale_user_claims.user_id IS '用户标识';
COMMENT ON COLUMN whale_user_claims.claim_type IS '声明类型';
COMMENT ON COLUMN whale_user_claims.claim_value IS '声明值';
COMMENT ON COLUMN whale_user_claims.tenant_id IS '租户标识';
COMMENT ON COLUMN whale_user_claims.created_at IS '创建时间';
COMMENT ON COLUMN whale_user_claims.updated_at IS '最后更新时间';
COMMENT ON COLUMN whale_user_claims.created_by IS '创建人';
COMMENT ON COLUMN whale_user_claims.updated_by IS '最后更新人';
COMMENT ON COLUMN whale_user_claims.is_deleted IS '软删标记';
COMMENT ON COLUMN whale_user_claims.deleted_at IS '删除时间';
COMMENT ON COLUMN whale_user_claims.deleted_by IS '删除人';

COMMENT ON TABLE whale_role_claims IS '角色声明';
COMMENT ON COLUMN whale_role_claims.id IS '主键';
COMMENT ON COLUMN whale_role_claims.role_id IS '角色标识';
COMMENT ON COLUMN whale_role_claims.claim_type IS '声明类型';
COMMENT ON COLUMN whale_role_claims.claim_value IS '声明值';
COMMENT ON COLUMN whale_role_claims.tenant_id IS '租户标识';
COMMENT ON COLUMN whale_role_claims.created_at IS '创建时间';
COMMENT ON COLUMN whale_role_claims.updated_at IS '最后更新时间';
COMMENT ON COLUMN whale_role_claims.created_by IS '创建人';
COMMENT ON COLUMN whale_role_claims.updated_by IS '最后更新人';
COMMENT ON COLUMN whale_role_claims.is_deleted IS '软删标记';
COMMENT ON COLUMN whale_role_claims.deleted_at IS '删除时间';
COMMENT ON COLUMN whale_role_claims.deleted_by IS '删除人';

COMMENT ON TABLE whale_qrtz_locks IS 'Quartz 调度锁';
COMMENT ON COLUMN whale_qrtz_locks.sched_name IS '调度器名称';
COMMENT ON COLUMN whale_qrtz_locks.lock_name IS '锁名称';

COMMENT ON TABLE whale_qrtz_job_details IS 'Quartz 作业详情';
COMMENT ON COLUMN whale_qrtz_job_details.sched_name IS '调度器名称';
COMMENT ON COLUMN whale_qrtz_job_details.job_name IS '作业名称';
COMMENT ON COLUMN whale_qrtz_job_details.job_group IS '作业组';
COMMENT ON COLUMN whale_qrtz_job_details.description IS '描述';
COMMENT ON COLUMN whale_qrtz_job_details.job_class_name IS '作业类名';
COMMENT ON COLUMN whale_qrtz_job_details.is_durable IS '是否持久化';
COMMENT ON COLUMN whale_qrtz_job_details.is_nonconcurrent IS '是否禁止并发';
COMMENT ON COLUMN whale_qrtz_job_details.is_update_data IS '是否更新数据';
COMMENT ON COLUMN whale_qrtz_job_details.requests_recovery IS '是否请求恢复';
COMMENT ON COLUMN whale_qrtz_job_details.job_data IS '作业数据（BLOB）';

COMMENT ON TABLE whale_qrtz_triggers IS 'Quartz 触发器';
COMMENT ON COLUMN whale_qrtz_triggers.sched_name IS '调度器名称';
COMMENT ON COLUMN whale_qrtz_triggers.trigger_name IS '触发器名称';
COMMENT ON COLUMN whale_qrtz_triggers.trigger_group IS '触发器组';
COMMENT ON COLUMN whale_qrtz_triggers.job_name IS '关联作业名称';
COMMENT ON COLUMN whale_qrtz_triggers.job_group IS '关联作业组';
COMMENT ON COLUMN whale_qrtz_triggers.description IS '描述';
COMMENT ON COLUMN whale_qrtz_triggers.next_fire_time IS '下次触发时间（毫秒）';
COMMENT ON COLUMN whale_qrtz_triggers.prev_fire_time IS '上次触发时间（毫秒）';
COMMENT ON COLUMN whale_qrtz_triggers.priority IS '优先级';
COMMENT ON COLUMN whale_qrtz_triggers.trigger_state IS '触发器状态';
COMMENT ON COLUMN whale_qrtz_triggers.trigger_type IS '触发器类型';
COMMENT ON COLUMN whale_qrtz_triggers.start_time IS '开始时间（毫秒）';
COMMENT ON COLUMN whale_qrtz_triggers.end_time IS '结束时间（毫秒）';
COMMENT ON COLUMN whale_qrtz_triggers.calendar_name IS '日历名称';
COMMENT ON COLUMN whale_qrtz_triggers.misfire_instr IS '错失触发策略';
COMMENT ON COLUMN whale_qrtz_triggers.job_data IS '触发器数据（BLOB）';

COMMENT ON TABLE whale_qrtz_cron_triggers IS 'Quartz Cron 触发器';
COMMENT ON COLUMN whale_qrtz_cron_triggers.sched_name IS '调度器名称';
COMMENT ON COLUMN whale_qrtz_cron_triggers.trigger_name IS '触发器名称';
COMMENT ON COLUMN whale_qrtz_cron_triggers.trigger_group IS '触发器组';
COMMENT ON COLUMN whale_qrtz_cron_triggers.cron_expression IS 'Cron 表达式';
COMMENT ON COLUMN whale_qrtz_cron_triggers.time_zone_id IS '时区 ID';

COMMENT ON TABLE whale_qrtz_simple_triggers IS 'Quartz 简单触发器';
COMMENT ON COLUMN whale_qrtz_simple_triggers.sched_name IS '调度器名称';
COMMENT ON COLUMN whale_qrtz_simple_triggers.trigger_name IS '触发器名称';
COMMENT ON COLUMN whale_qrtz_simple_triggers.trigger_group IS '触发器组';
COMMENT ON COLUMN whale_qrtz_simple_triggers.repeat_count IS '重复次数';
COMMENT ON COLUMN whale_qrtz_simple_triggers.repeat_interval IS '重复间隔（毫秒）';
COMMENT ON COLUMN whale_qrtz_simple_triggers.times_triggered IS '已触发次数';

COMMENT ON TABLE whale_qrtz_blob_triggers IS 'Quartz BLOB 触发器';
COMMENT ON COLUMN whale_qrtz_blob_triggers.sched_name IS '调度器名称';
COMMENT ON COLUMN whale_qrtz_blob_triggers.trigger_name IS '触发器名称';
COMMENT ON COLUMN whale_qrtz_blob_triggers.trigger_group IS '触发器组';
COMMENT ON COLUMN whale_qrtz_blob_triggers.blob_data IS '触发器 BLOB 数据';

COMMENT ON TABLE whale_qrtz_calendars IS 'Quartz 日历';
COMMENT ON COLUMN whale_qrtz_calendars.sched_name IS '调度器名称';
COMMENT ON COLUMN whale_qrtz_calendars.calendar_name IS '日历名称';
COMMENT ON COLUMN whale_qrtz_calendars.calendar IS '日历对象（BLOB）';

COMMENT ON TABLE whale_qrtz_paused_trigger_grps IS 'Quartz 暂停触发器组';
COMMENT ON COLUMN whale_qrtz_paused_trigger_grps.sched_name IS '调度器名称';
COMMENT ON COLUMN whale_qrtz_paused_trigger_grps.trigger_group IS '触发器组';

COMMENT ON TABLE whale_qrtz_scheduler_state IS 'Quartz 调度器状态';
COMMENT ON COLUMN whale_qrtz_scheduler_state.sched_name IS '调度器名称';
COMMENT ON COLUMN whale_qrtz_scheduler_state.instance_name IS '实例名称';
COMMENT ON COLUMN whale_qrtz_scheduler_state.last_checkin_time IS '最后检入时间（毫秒）';
COMMENT ON COLUMN whale_qrtz_scheduler_state.checkin_interval IS '检入间隔（毫秒）';

COMMENT ON TABLE whale_qrtz_fired_triggers IS 'Quartz 已触发记录';
COMMENT ON COLUMN whale_qrtz_fired_triggers.sched_name IS '调度器名称';
COMMENT ON COLUMN whale_qrtz_fired_triggers.entry_id IS '条目 ID';
COMMENT ON COLUMN whale_qrtz_fired_triggers.trigger_name IS '触发器名称';
COMMENT ON COLUMN whale_qrtz_fired_triggers.trigger_group IS '触发器组';
COMMENT ON COLUMN whale_qrtz_fired_triggers.instance_name IS '实例名称';
COMMENT ON COLUMN whale_qrtz_fired_triggers.fired_time IS '触发时间（毫秒）';
COMMENT ON COLUMN whale_qrtz_fired_triggers.sched_time IS '计划时间（毫秒）';
COMMENT ON COLUMN whale_qrtz_fired_triggers.priority IS '优先级';
COMMENT ON COLUMN whale_qrtz_fired_triggers.state IS '状态';
COMMENT ON COLUMN whale_qrtz_fired_triggers.job_name IS '作业名称';
COMMENT ON COLUMN whale_qrtz_fired_triggers.job_group IS '作业组';
COMMENT ON COLUMN whale_qrtz_fired_triggers.is_nonconcurrent IS '是否禁止并发';
COMMENT ON COLUMN whale_qrtz_fired_triggers.requests_recovery IS '是否请求恢复';

COMMENT ON TABLE whale_tenants IS '租户';
COMMENT ON COLUMN whale_tenants.id IS '主键';
COMMENT ON COLUMN whale_tenants.name IS '租户名称';
COMMENT ON COLUMN whale_tenants.code IS '租户代码';
COMMENT ON COLUMN whale_tenants.description IS '租户描述';
COMMENT ON COLUMN whale_tenants.is_active IS '是否启用';
COMMENT ON COLUMN whale_tenants.extra_properties IS '扩展属性';
COMMENT ON COLUMN whale_tenants.created_at IS '创建时间';
COMMENT ON COLUMN whale_tenants.updated_at IS '最后更新时间';
COMMENT ON COLUMN whale_tenants.created_by IS '创建人';
COMMENT ON COLUMN whale_tenants.updated_by IS '最后更新人';
COMMENT ON COLUMN whale_tenants.is_deleted IS '软删标记';
COMMENT ON COLUMN whale_tenants.deleted_at IS '删除时间';
COMMENT ON COLUMN whale_tenants.deleted_by IS '删除人';

COMMENT ON TABLE whale_user_tenants IS '用户租户关联';
COMMENT ON COLUMN whale_user_tenants.id IS '主键';
COMMENT ON COLUMN whale_user_tenants.user_id IS '用户标识';
COMMENT ON COLUMN whale_user_tenants.tenant_id IS '租户标识';
COMMENT ON COLUMN whale_user_tenants.created_at IS '创建时间';
COMMENT ON COLUMN whale_user_tenants.updated_at IS '最后更新时间';
COMMENT ON COLUMN whale_user_tenants.created_by IS '创建人';
COMMENT ON COLUMN whale_user_tenants.updated_by IS '最后更新人';
COMMENT ON COLUMN whale_user_tenants.is_deleted IS '软删标记';
COMMENT ON COLUMN whale_user_tenants.deleted_at IS '删除时间';
COMMENT ON COLUMN whale_user_tenants.deleted_by IS '删除人';

COMMENT ON TABLE whale_settings IS '系统设置';
COMMENT ON COLUMN whale_settings.id IS '主键';
COMMENT ON COLUMN whale_settings.name IS '设置键';
COMMENT ON COLUMN whale_settings.value IS '设置值';
COMMENT ON COLUMN whale_settings.provider_name IS '提供者类型';
COMMENT ON COLUMN whale_settings.provider_key IS '提供者键';
COMMENT ON COLUMN whale_settings.tenant_id IS '租户标识';
COMMENT ON COLUMN whale_settings.description IS '描述';
COMMENT ON COLUMN whale_settings.created_at IS '创建时间';
COMMENT ON COLUMN whale_settings.updated_at IS '最后更新时间';
COMMENT ON COLUMN whale_settings.created_by IS '创建人';
COMMENT ON COLUMN whale_settings.updated_by IS '最后更新人';
COMMENT ON COLUMN whale_settings.is_deleted IS '软删标记';
COMMENT ON COLUMN whale_settings.deleted_at IS '删除时间';
COMMENT ON COLUMN whale_settings.deleted_by IS '删除人';

COMMENT ON TABLE whale_menus IS '菜单';
COMMENT ON COLUMN whale_menus.id IS '主键';
COMMENT ON COLUMN whale_menus.code IS '菜单编码';
COMMENT ON COLUMN whale_menus.name IS '菜单名称';
COMMENT ON COLUMN whale_menus.display_name IS '显示名称';
COMMENT ON COLUMN whale_menus.icon IS '图标';
COMMENT ON COLUMN whale_menus.path IS '路由路径';
COMMENT ON COLUMN whale_menus.component IS '前端组件路径';
COMMENT ON COLUMN whale_menus.permission_name IS '关联权限名称';
COMMENT ON COLUMN whale_menus.parent_id IS '父菜单 ID';
COMMENT ON COLUMN whale_menus.sort_order IS '排序号';
COMMENT ON COLUMN whale_menus.is_enabled IS '是否启用';
COMMENT ON COLUMN whale_menus.is_hidden IS '是否隐藏';
COMMENT ON COLUMN whale_menus.is_external IS '是否外链';
COMMENT ON COLUMN whale_menus.external_url IS '外链 URL';
COMMENT ON COLUMN whale_menus.menu_type IS '菜单类型（menu/directory）';
COMMENT ON COLUMN whale_menus.remark IS '备注';
COMMENT ON COLUMN whale_menus.tenant_id IS '租户标识';
COMMENT ON COLUMN whale_menus.created_at IS '创建时间';
COMMENT ON COLUMN whale_menus.updated_at IS '最后更新时间';
COMMENT ON COLUMN whale_menus.created_by IS '创建人';
COMMENT ON COLUMN whale_menus.updated_by IS '最后更新人';
COMMENT ON COLUMN whale_menus.is_deleted IS '软删标记';
COMMENT ON COLUMN whale_menus.deleted_at IS '删除时间';
COMMENT ON COLUMN whale_menus.deleted_by IS '删除人';
COMMENT ON COLUMN whale_menus.keep_alive IS '是否缓存页面';

COMMENT ON TABLE whale_sequence IS '业务序号表，按 key 记录当前序号';
COMMENT ON COLUMN whale_sequence.seq_key IS '序号键（如 ORDER、PATIENT、或 ORDER:2025-01-30 表示按天）';
COMMENT ON COLUMN whale_sequence.current_value IS '当前序号值（递增后返回）';
COMMENT ON COLUMN whale_sequence.updated_at IS '最后更新时间';

COMMENT ON TABLE whale_audit_logs IS '实体变更审计日志';
COMMENT ON COLUMN whale_audit_logs.id IS '主键';
COMMENT ON COLUMN whale_audit_logs.entity_type IS '实体类型';
COMMENT ON COLUMN whale_audit_logs.entity_id IS '实体 ID';
COMMENT ON COLUMN whale_audit_logs.operation_type IS '操作类型';
COMMENT ON COLUMN whale_audit_logs.user_id IS '操作人';
COMMENT ON COLUMN whale_audit_logs.tenant_id IS '租户标识';
COMMENT ON COLUMN whale_audit_logs.ip_address IS 'IP 地址';
COMMENT ON COLUMN whale_audit_logs.user_agent IS '用户agent';
COMMENT ON COLUMN whale_audit_logs.changes IS '变更内容';
COMMENT ON COLUMN whale_audit_logs.created_at IS '创建时间';

COMMENT ON TABLE whale_framework_runtime_state IS '框架运行时状态';
COMMENT ON COLUMN whale_framework_runtime_state.id IS '主键';
COMMENT ON COLUMN whale_framework_runtime_state.insight_code IS '洞察编码';
COMMENT ON COLUMN whale_framework_runtime_state.category IS '分类';
COMMENT ON COLUMN whale_framework_runtime_state.runtime_version IS '运行时版本';
COMMENT ON COLUMN whale_framework_runtime_state.operator_user_id IS '操作人用户标识';
COMMENT ON COLUMN whale_framework_runtime_state.session_started_at IS '会话开始时间';
COMMENT ON COLUMN whale_framework_runtime_state.org_category IS '机构类别';
COMMENT ON COLUMN whale_framework_runtime_state.org_name IS '机构名称';
COMMENT ON COLUMN whale_framework_runtime_state.deployment_fingerprint IS '部署指纹';
COMMENT ON COLUMN whale_framework_runtime_state.license_customer IS '许可证客户';
COMMENT ON COLUMN whale_framework_runtime_state.license_not_after IS '许可证失效时间';
COMMENT ON COLUMN whale_framework_runtime_state.payload_json IS '载荷 JSON';
COMMENT ON COLUMN whale_framework_runtime_state.observed_at IS '观测时间';
COMMENT ON COLUMN whale_framework_runtime_state.received_at IS '接收时间';

























































-- ========== 发版合并：原 V74–V87 菜单/权限种子（whale_menus / whale_permissions）==========

-- ----- V74__report_menus.sql -----
-- V74：whale-print/report 菜单 + Report.* 权限 + 管理员角色授权
-- 说明：V1 已写入菜单但缺少 Print.Templates.View 等权限及 role=1 授权，导致 UI 不显示
-- 结构：顶级「报表打印」目录下集中 whale-print/report 全部入口

-- ========== 1. Report 权限定义 ==========
INSERT INTO whale_permissions (
    id, name, display_name, parent_name, description,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by
)
VALUES
    ('310700000000000001', 'Report', '打印与报表', NULL,
     'whale-print/report 模块入口权限分组', NULL,
     TIMESTAMP '2026-06-11 00:00:00+00', TIMESTAMP '2026-06-11 00:00:00+00', 1, 1, FALSE, NULL, NULL),
    ('310700000000000002', 'Print.Templates.View', '打印模板', 'BasicData',
     '菜单入口: PrintTemplate', NULL,
     TIMESTAMP '2026-06-11 00:00:00+00', TIMESTAMP '2026-06-22 00:00:00+00', 1, 1, FALSE, NULL, NULL),
    ('310700000000000003', 'Print.Templates.Design', '打印设计器', 'BasicData',
     '独立 hiprint 画布设计器菜单；与 PrintBinding / 业务发印无关', NULL,
     TIMESTAMP '2026-06-11 00:00:00+00', TIMESTAMP '2026-06-25 00:00:00+00', 1, 1, FALSE, NULL, NULL),
    ('310700000000000004', 'Print.PrintLog.View', '打印日志', 'BasicData',
     '菜单入口: ReportPrintLog', NULL,
     TIMESTAMP '2026-06-11 00:00:00+00', TIMESTAMP '2026-06-22 00:00:00+00', 1, 1, FALSE, NULL, NULL),
    ('310700000000000005', 'Report.Center.View', '动态报表', 'Report',
     '菜单入口: ReportCenter', NULL,
     TIMESTAMP '2026-06-11 00:00:00+00', TIMESTAMP '2026-06-11 00:00:00+00', 1, 1, FALSE, NULL, NULL),
    ('310700000000000006', 'Health.ExternalDocument.View', '外系统报告', 'Report',
     '菜单入口: ExternalDocument', NULL,
     TIMESTAMP '2026-06-11 00:00:00+00', TIMESTAMP '2026-06-11 00:00:00+00', 1, 1, FALSE, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET
    name          = EXCLUDED.name,
    display_name  = EXCLUDED.display_name,
    parent_name   = EXCLUDED.parent_name,
    description   = EXCLUDED.description,
    is_deleted    = FALSE,
    deleted_at    = NULL,
    deleted_by    = NULL,
    updated_at    = EXCLUDED.updated_at;

-- ========== 2. 菜单（upsert，兼容已跑过旧 V1 的库） ==========
INSERT INTO whale_menus (
    id, code, name, display_name, icon, path, component, permission_name, parent_id,
    sort_order, is_enabled, is_hidden, is_external, external_url, menu_type, remark,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by, keep_alive
)
VALUES
    ('310400000000000100', 'ReportManagement', 'ReportManagement', '报表管理', 'lucide:layout-dashboard',
     '/reports', '', NULL, NULL, 11, TRUE, FALSE, FALSE, '', 'directory',
     '报表中心 / 报表设计器 / 外系统报告（PRT 打印菜单在基础数据）', NULL,
     TIMESTAMP '2026-06-03 16:00:00+00', TIMESTAMP '2026-06-22 00:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE),

    ('310600000000000111', 'ReportDesigner', 'ReportDesigner', '报表设计器', 'lucide:pen-line',
     '/reports/designer', '/report/designer/index', 'Report.Designer.View', '310400000000000100', 4,
     TRUE, TRUE, FALSE, '', 'menu', 'C 类报表配置、PDF 模板绑定与发布（draft → published）', NULL,
     TIMESTAMP '2026-06-03 16:00:00+00', TIMESTAMP '2026-06-11 00:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE),

    ('310600000000000112', 'ExternalDocument', 'ExternalDocument', '外系统报告', 'lucide:file-search',
     '/health/external-document', '/health/external-document/index', 'Health.ExternalDocument.View', '310400000000000100', 5,
     TRUE, FALSE, FALSE, '', 'menu', 'D 类 LIS/PACS 外系统报告查看', NULL,
     TIMESTAMP '2026-06-11 00:00:00+00', TIMESTAMP '2026-06-11 00:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE)
ON CONFLICT (id) DO UPDATE SET
    code            = EXCLUDED.code,
    name            = EXCLUDED.name,
    display_name    = EXCLUDED.display_name,
    icon            = EXCLUDED.icon,
    path            = EXCLUDED.path,
    component       = EXCLUDED.component,
    permission_name = EXCLUDED.permission_name,
    parent_id       = EXCLUDED.parent_id,
    sort_order      = EXCLUDED.sort_order,
    is_enabled      = EXCLUDED.is_enabled,
    is_hidden       = EXCLUDED.is_hidden,
    remark          = EXCLUDED.remark,
    is_deleted      = FALSE,
    deleted_at      = NULL,
    deleted_by      = NULL,
    updated_at      = EXCLUDED.updated_at;

-- V1 占位报表页（首页/院长/临床/财务）移出「报表打印」，避免与 whale-print/report 入口混排
UPDATE whale_menus
SET parent_id   = NULL,
    sort_order  = 12,
    is_hidden   = TRUE,
    remark      = COALESCE(remark, '') || '；V74 已迁至隐藏，whale-print/report 统一在「报表打印」',
    updated_at  = TIMESTAMP '2026-06-11 00:00:00+00'
WHERE id IN (
    '310400000000000101',
    '310400000000000102',
    '310400000000000103',
    '301699662818054144'
)
  AND is_deleted = FALSE;

-- ========== 3. 管理员角色（R / 1）授权 ==========
INSERT INTO whale_permission_grants (
    id, permission_name, provider_name, provider_key,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by
)
VALUES
    ('310800000000000001', 'Print.Templates.View', 'R', '1', NULL, TIMESTAMP '2026-06-11 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('310800000000000002', 'Print.Templates.Design', 'R', '1', NULL, TIMESTAMP '2026-06-11 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('310800000000000003', 'Print.PrintLog.View', 'R', '1', NULL, TIMESTAMP '2026-06-11 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('310800000000000004', 'Report.Center.View', 'R', '1', NULL, TIMESTAMP '2026-06-11 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('310800000000000005', 'Health.ExternalDocument.View', 'R', '1', NULL, TIMESTAMP '2026-06-11 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL)
ON CONFLICT (permission_name, provider_name, provider_key) DO UPDATE SET
    is_deleted = FALSE,
    deleted_at = NULL,
    deleted_by = NULL,
    updated_at = TIMESTAMP '2026-06-11 00:00:00+00';

-- ----- V75__report_print_binding_menu.sql -----
-- V75：院点打印模板配置菜单

INSERT INTO whale_permissions (
    id, name, display_name, parent_name, description,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by
)
VALUES (
    '310700000000000007', 'Print.PrintBinding.View', '打印模板配置', 'BasicData',
    '菜单入口: ReportPrintBinding — bindingCode→busNo', NULL,
    TIMESTAMP '2026-06-11 12:00:00+00', TIMESTAMP '2026-06-11 12:00:00+00', 1, 1, FALSE, NULL, NULL
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

INSERT INTO whale_menus (
    id, code, name, display_name, icon, path, component, permission_name, parent_id,
    sort_order, is_enabled, is_hidden, is_external, external_url, menu_type, remark,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by, keep_alive
)
VALUES (
    '310600000000000104', 'ReportPrintBinding', 'ReportPrintBinding', '打印模板配置', 'lucide:link-2',
    '/basic/print-binding', '/basic/print-binding/index', 'Print.PrintBinding.View', '2000', 26,
    TRUE, FALSE, FALSE, '', 'menu', 'PRT bindingCode→busNo 租户绑定', NULL,
    TIMESTAMP '2026-06-11 12:00:00+00', TIMESTAMP '2026-06-11 12:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE
)
ON CONFLICT (id) DO UPDATE SET
    code            = EXCLUDED.code,
    name            = EXCLUDED.name,
    display_name    = EXCLUDED.display_name,
    icon            = EXCLUDED.icon,
    path            = EXCLUDED.path,
    component       = EXCLUDED.component,
    permission_name = EXCLUDED.permission_name,
    parent_id       = EXCLUDED.parent_id,
    sort_order      = EXCLUDED.sort_order,
    is_enabled      = EXCLUDED.is_enabled,
    is_hidden       = EXCLUDED.is_hidden,
    remark          = EXCLUDED.remark,
    is_deleted      = FALSE,
    deleted_at      = NULL,
    deleted_by      = NULL,
    updated_at      = EXCLUDED.updated_at;

DELETE FROM whale_permission_grants
WHERE permission_name = 'Print.PrintBinding.View'
  AND provider_name = 'R'
  AND provider_key = '1'
  AND id <> '310800000000000006';

INSERT INTO whale_permission_grants (
    id, permission_name, provider_name, provider_key,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by
)
VALUES (
    '310800000000000006', 'Print.PrintBinding.View', 'R', '1', NULL,
    TIMESTAMP '2026-06-11 12:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL
)
ON CONFLICT (id) DO UPDATE SET
    permission_name = EXCLUDED.permission_name,
    provider_name   = EXCLUDED.provider_name,
    provider_key    = EXCLUDED.provider_key,
    is_deleted      = FALSE,
    deleted_at      = NULL,
    deleted_by      = NULL,
    updated_at      = EXCLUDED.updated_at;

-- ----- V78__report_designer_and_center_menus.sql -----
-- V78：动态报表拆分为「报表设计器」+「报表中心」

INSERT INTO whale_permissions (
    id, name, display_name, parent_name, description,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by
)
VALUES (
    '310700000000000008', 'Report.Designer.View', '报表设计器', 'Report',
    '菜单入口: ReportDesigner — C 类报表配置与发布', NULL,
    TIMESTAMP '2026-06-13 00:00:00+00', TIMESTAMP '2026-06-13 00:00:00+00', 1, 1, FALSE, NULL, NULL
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
SET display_name = '报表中心',
    description  = '菜单入口: ReportCenter — 已发布 C 类报表查询/导出/打印',
    updated_at   = TIMESTAMP '2026-06-13 00:00:00+00'
WHERE name = 'Report.Center.View'
  AND is_deleted = FALSE;

UPDATE whale_menus
SET code            = 'ReportDesigner',
    name            = 'ReportDesigner',
    display_name    = '报表设计器',
    icon            = 'lucide:pen-line',
    path            = '/reports/designer',
    component       = '/report/designer/index',
    permission_name = 'Report.Designer.View',
    sort_order      = 4,
    remark          = 'C 类报表配置、PDF 模板绑定与发布（draft → published）',
    updated_at      = TIMESTAMP '2026-06-13 00:00:00+00'
WHERE id = '310600000000000111'
  AND is_deleted = FALSE;

INSERT INTO whale_menus (
    id, code, name, display_name, icon, path, component, permission_name, parent_id,
    sort_order, is_enabled, is_hidden, is_external, external_url, menu_type, remark,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by, keep_alive
)
VALUES (
    '310600000000000113', 'ReportCenter', 'ReportCenter', '报表中心', 'lucide:table-2',
    '/reports/center', '/report/center/index', 'Report.Center.View', '310400000000000100', 5,
    TRUE, FALSE, FALSE, '', 'menu', '已发布 C 类报表：查询、Excel/PDF 导出、打印预览与打印', NULL,
    TIMESTAMP '2026-06-13 00:00:00+00', TIMESTAMP '2026-06-13 00:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE
)
ON CONFLICT (id) DO UPDATE SET
    code            = EXCLUDED.code,
    name            = EXCLUDED.name,
    display_name    = EXCLUDED.display_name,
    icon            = EXCLUDED.icon,
    path            = EXCLUDED.path,
    component       = EXCLUDED.component,
    permission_name = EXCLUDED.permission_name,
    parent_id       = EXCLUDED.parent_id,
    sort_order      = EXCLUDED.sort_order,
    is_enabled      = EXCLUDED.is_enabled,
    is_hidden       = EXCLUDED.is_hidden,
    remark          = EXCLUDED.remark,
    is_deleted      = FALSE,
    deleted_at      = NULL,
    deleted_by      = NULL,
    updated_at      = EXCLUDED.updated_at;

UPDATE whale_menus
SET sort_order = 6,
    updated_at = TIMESTAMP '2026-06-13 00:00:00+00'
WHERE id = '310600000000000112'
  AND is_deleted = FALSE;

UPDATE whale_menus
SET remark = 'whale-print/report 打印模板 / 日志 / 报表设计器 / 报表中心 / 外系统报告',
    updated_at = TIMESTAMP '2026-06-13 00:00:00+00'
WHERE id = '310400000000000100'
  AND is_deleted = FALSE;

DELETE FROM whale_permission_grants
WHERE permission_name = 'Report.Designer.View'
  AND provider_name = 'R'
  AND provider_key = '1'
  AND id <> '310800000000000007';

INSERT INTO whale_permission_grants (
    id, permission_name, provider_name, provider_key,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by
)
VALUES (
    '310800000000000007', 'Report.Designer.View', 'R', '1', NULL,
    TIMESTAMP '2026-06-13 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL
)
ON CONFLICT (id) DO UPDATE SET
    permission_name = EXCLUDED.permission_name,
    provider_name   = EXCLUDED.provider_name,
    provider_key    = EXCLUDED.provider_key,
    is_deleted      = FALSE,
    deleted_at      = NULL,
    deleted_by      = NULL,
    updated_at      = EXCLUDED.updated_at;

-- ----- V79__report_template_designer_center_menus.sql -----
-- V79：C 类三层菜单 — 报表模板 / 报表设计器 / 报表中心

INSERT INTO whale_permissions (
    id, name, display_name, parent_name, description,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by
)
VALUES (
    '310700000000000009', 'Report.Definition.View', '报表模板', 'Report',
    '菜单入口: ReportTemplate — C 类报表 CRUD', NULL,
    TIMESTAMP '2026-06-13 12:00:00+00', TIMESTAMP '2026-06-13 12:00:00+00', 1, 1, FALSE, NULL, NULL
)
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name, display_name = EXCLUDED.display_name,
    description = EXCLUDED.description, is_deleted = FALSE, updated_at = EXCLUDED.updated_at;

UPDATE whale_permissions
SET display_name = '报表设计器',
    description = '菜单入口: ReportDesigner — SQL/表头/表底/筛选器设计',
    updated_at = TIMESTAMP '2026-06-13 12:00:00+00'
WHERE name = 'Report.Designer.View' AND is_deleted = FALSE;

INSERT INTO whale_menus (
    id, code, name, display_name, icon, path, component, permission_name, parent_id,
    sort_order, is_enabled, is_hidden, is_external, external_url, menu_type, remark,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by, keep_alive
)
VALUES (
    '310600000000000114', 'ReportTemplate', 'ReportTemplate', '报表模板', 'lucide:file-spreadsheet',
    '/reports/report-template', '/report/template/index', 'Report.Definition.View', '310400000000000100', 4,
    TRUE, FALSE, FALSE, '', 'menu', 'C 类报表增删改查（类比打印模板列表）', NULL,
    TIMESTAMP '2026-06-13 12:00:00+00', TIMESTAMP '2026-06-13 12:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE
)
ON CONFLICT (id) DO UPDATE SET
    code = EXCLUDED.code, display_name = EXCLUDED.display_name, path = EXCLUDED.path,
    component = EXCLUDED.component, permission_name = EXCLUDED.permission_name,
    sort_order = EXCLUDED.sort_order, remark = EXCLUDED.remark, is_deleted = FALSE,
    updated_at = EXCLUDED.updated_at;

UPDATE whale_menus
SET sort_order = 5,
    is_hidden  = TRUE,
    remark     = 'C 类报表设计编辑页（可从报表模板「设计」进入）',
    updated_at = TIMESTAMP '2026-06-13 12:00:00+00'
WHERE id = '310600000000000111' AND is_deleted = FALSE;

UPDATE whale_menus
SET sort_order = 6, updated_at = TIMESTAMP '2026-06-13 12:00:00+00'
WHERE id = '310600000000000113' AND is_deleted = FALSE;

UPDATE whale_menus
SET sort_order = 7, updated_at = TIMESTAMP '2026-06-13 12:00:00+00'
WHERE id = '310600000000000112' AND is_deleted = FALSE;

UPDATE whale_menus
SET remark = 'whale-print/report 打印模板 / 报表模板 / 报表中心 / 外系统报告',
    updated_at = TIMESTAMP '2026-06-13 12:00:00+00'
WHERE id = '310400000000000100' AND is_deleted = FALSE;

DELETE FROM whale_permission_grants
WHERE permission_name = 'Report.Definition.View'
  AND provider_name = 'R'
  AND provider_key = '1'
  AND id <> '310800000000000008';

INSERT INTO whale_permission_grants (
    id, permission_name, provider_name, provider_key,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by
)
VALUES (
    '310800000000000008', 'Report.Definition.View', 'R', '1', NULL,
    TIMESTAMP '2026-06-13 12:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL
)
ON CONFLICT (id) DO UPDATE SET
    permission_name = EXCLUDED.permission_name,
    provider_name   = EXCLUDED.provider_name,
    provider_key    = EXCLUDED.provider_key,
    is_deleted      = FALSE,
    deleted_at      = NULL,
    deleted_by      = NULL,
    updated_at      = EXCLUDED.updated_at;

-- ----- V81__chs_reconciliation_menu.sql -----
-- V81：医保对账清算菜单（3201 / 3203A–3206A）

INSERT INTO whale_menus (
    id, code, name, display_name, icon, path, component, permission_name, parent_id,
    sort_order, is_enabled, is_hidden, is_external, external_url, menu_type, remark,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by, keep_alive
)
VALUES (
    '310500000000000103', 'ChsReconciliation', 'ChsReconciliation', '对账清算', 'lucide:scale',
    '/chs/reconciliation', '/chs/reconciliation/index', '', '310500000000000100', 3,
    TRUE, FALSE, FALSE, '', 'menu', '3201 日对账 + 3206A→3203A→3205A→3204A 月清算', NULL,
    TIMESTAMP '2026-06-13 12:00:00+00', TIMESTAMP '2026-06-13 12:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE
)
ON CONFLICT (id) DO UPDATE SET
    code            = EXCLUDED.code,
    name            = EXCLUDED.name,
    display_name    = EXCLUDED.display_name,
    icon            = EXCLUDED.icon,
    path            = EXCLUDED.path,
    component       = EXCLUDED.component,
    permission_name = EXCLUDED.permission_name,
    parent_id       = EXCLUDED.parent_id,
    sort_order      = EXCLUDED.sort_order,
    is_enabled      = EXCLUDED.is_enabled,
    is_hidden       = EXCLUDED.is_hidden,
    remark          = EXCLUDED.remark,
    is_deleted      = FALSE,
    deleted_at      = NULL,
    deleted_by      = NULL,
    updated_at      = EXCLUDED.updated_at;

-- ----- V82__chs_catalog_mapping_workbench_menu.sql -----
-- V82：医保目录对码 Workbench 菜单

INSERT INTO whale_menus (
    id, code, name, display_name, icon, path, component, permission_name, parent_id,
    sort_order, is_enabled, is_hidden, is_external, external_url, menu_type, remark,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by, keep_alive
)
VALUES (
    '310500000000000104', 'ChsCatalogMappingWorkbench', 'ChsCatalogMappingWorkbench', '目录对码工作台', 'lucide:git-compare',
    '/chs/catalog-mapping-workbench', '/chs/catalog-mapping-workbench/index', '', '310500000000000100', 4,
    TRUE, FALSE, FALSE, '', 'menu', '待对码分页 + 3301 批量上传进度', NULL,
    TIMESTAMP '2026-06-13 14:00:00+00', TIMESTAMP '2026-06-13 14:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE
)
ON CONFLICT (id) DO UPDATE SET
    code            = EXCLUDED.code,
    name            = EXCLUDED.name,
    display_name    = EXCLUDED.display_name,
    icon            = EXCLUDED.icon,
    path            = EXCLUDED.path,
    component       = EXCLUDED.component,
    permission_name = EXCLUDED.permission_name,
    parent_id       = EXCLUDED.parent_id,
    sort_order      = EXCLUDED.sort_order,
    is_enabled      = EXCLUDED.is_enabled,
    is_hidden       = EXCLUDED.is_hidden,
    remark          = EXCLUDED.remark,
    is_deleted      = FALSE,
    deleted_at      = NULL,
    deleted_by      = NULL,
    updated_at      = EXCLUDED.updated_at;

-- ----- V83__chs_inventory_ops_menu.sql -----
-- V83：医保进销存运维菜单（本地上传流水 + 3508–3513 平台查询）

INSERT INTO whale_menus (
    id, code, name, display_name, icon, path, component, permission_name, parent_id,
    sort_order, is_enabled, is_hidden, is_external, external_url, menu_type, remark,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by, keep_alive
)
VALUES (
    '310500000000000105', 'ChsInventoryOps', 'ChsInventoryOps', '进销存运维', 'lucide:package-search',
    '/chs/inventory', '/chs/inventory/index', '', '310500000000000100', 5,
    TRUE, FALSE, FALSE, '', 'menu', '3503–3506 本地上传流水 + 3508–3513 平台查询', NULL,
    TIMESTAMP '2026-06-14 12:00:00+00', TIMESTAMP '2026-06-14 12:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE
)
ON CONFLICT (id) DO UPDATE SET
    code            = EXCLUDED.code,
    name            = EXCLUDED.name,
    display_name    = EXCLUDED.display_name,
    icon            = EXCLUDED.icon,
    path            = EXCLUDED.path,
    component       = EXCLUDED.component,
    permission_name = EXCLUDED.permission_name,
    parent_id       = EXCLUDED.parent_id,
    sort_order      = EXCLUDED.sort_order,
    is_enabled      = EXCLUDED.is_enabled,
    is_hidden       = EXCLUDED.is_hidden,
    remark          = EXCLUDED.remark,
    is_deleted      = FALSE,
    deleted_at      = NULL,
    deleted_by      = NULL,
    updated_at      = EXCLUDED.updated_at;

-- ----- V84__wh_return_to_supplier_menu.sql -----
-- V84：药库退货单（向供应商退货）菜单

INSERT INTO whale_menus (
    id, code, name, display_name, icon, path, component, permission_name, parent_id,
    sort_order, is_enabled, is_hidden, is_external, external_url, menu_type, remark,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by, keep_alive
)
VALUES (
    '310400000000000106', 'WhReturnToSupplier', 'WhReturnToSupplier', '药库退货单', 'lucide:package-x',
    '/inventory/warehouse/return-to-supplier', '/inventory/warehouse/return-to-supplier/index', '', '284503862625177600', 6,
    TRUE, FALSE, FALSE, '', 'menu', '药库向供应商退货 + 医保3504', NULL,
    TIMESTAMP '2026-06-14 14:00:00+00', TIMESTAMP '2026-06-14 14:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE
)
ON CONFLICT (id) DO UPDATE SET
    code            = EXCLUDED.code,
    name            = EXCLUDED.name,
    display_name    = EXCLUDED.display_name,
    icon            = EXCLUDED.icon,
    path            = EXCLUDED.path,
    component       = EXCLUDED.component,
    permission_name = EXCLUDED.permission_name,
    parent_id       = EXCLUDED.parent_id,
    sort_order      = EXCLUDED.sort_order,
    is_enabled      = EXCLUDED.is_enabled,
    is_hidden       = EXCLUDED.is_hidden,
    remark          = EXCLUDED.remark,
    is_deleted      = FALSE,
    deleted_at      = NULL,
    deleted_by      = NULL,
    updated_at      = EXCLUDED.updated_at;

-- ----- V85__dept_stocktake_menu.sql -----
-- V85：科室盘点单菜单

INSERT INTO whale_menus (
    id, code, name, display_name, icon, path, component, permission_name, parent_id,
    sort_order, is_enabled, is_hidden, is_external, external_url, menu_type, remark,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by, keep_alive
)
VALUES (
    '310400000000000107', 'DeptStocktakeOrder', 'DeptStocktakeOrder', '科室盘点单', 'lucide:clipboard-check',
    '/inventory/department/stocktake', '/inventory/department/stocktake/index', '', '284503862625177600', 7,
    TRUE, FALSE, FALSE, '', 'menu', '科室盘点 + 医保3501/3502', NULL,
    TIMESTAMP '2026-06-14 16:00:00+00', TIMESTAMP '2026-06-14 16:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE
)
ON CONFLICT (id) DO UPDATE SET
    code            = EXCLUDED.code,
    name            = EXCLUDED.name,
    display_name    = EXCLUDED.display_name,
    icon            = EXCLUDED.icon,
    path            = EXCLUDED.path,
    component       = EXCLUDED.component,
    permission_name = EXCLUDED.permission_name,
    parent_id       = EXCLUDED.parent_id,
    sort_order      = EXCLUDED.sort_order,
    is_enabled      = EXCLUDED.is_enabled,
    is_hidden       = EXCLUDED.is_hidden,
    remark          = EXCLUDED.remark,
    is_deleted      = FALSE,
    deleted_at      = NULL,
    deleted_by      = NULL,
    updated_at      = EXCLUDED.updated_at;

-- ----- V86__inventory_gainloss_transfer_menu.sql -----
-- V86：损益单与调拨单菜单（药库/药房/科室损益 + 调拨入/出库）

INSERT INTO whale_menus (
    id, code, name, display_name, icon, path, component, permission_name, parent_id,
    sort_order, is_enabled, is_hidden, is_external, external_url, menu_type, remark,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by, keep_alive
)
VALUES
(
    '310400000000000108', 'WhGainLoss', 'WhGainLoss', '药库损益单', 'lucide:scale',
    '/inventory/warehouse/gain-loss', '/inventory/warehouse/gain-loss/index', '', '284503862625177600', 8,
    TRUE, FALSE, FALSE, '', 'menu', '药库损益 + 医保3502', NULL,
    TIMESTAMP '2026-06-14 18:00:00+00', TIMESTAMP '2026-06-14 18:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE
),
(
    '310400000000000109', 'PhaGainLoss', 'PhaGainLoss', '药房损益单', 'lucide:scale',
    '/inventory/pharmacy/gain-loss', '/inventory/pharmacy/gain-loss/index', '', '284503862625177600', 9,
    TRUE, FALSE, FALSE, '', 'menu', '药房损益 + 医保3502', NULL,
    TIMESTAMP '2026-06-14 18:00:00+00', TIMESTAMP '2026-06-14 18:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE
),
(
    '310400000000000110', 'DeptGainLoss', 'DeptGainLoss', '科室损益单', 'lucide:scale',
    '/inventory/department/gain-loss', '/inventory/department/gain-loss/index', '', '284503862625177600', 10,
    TRUE, FALSE, FALSE, '', 'menu', '科室损益 + 医保3502', NULL,
    TIMESTAMP '2026-06-14 18:00:00+00', TIMESTAMP '2026-06-14 18:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE
),
(
    '310400000000000111', 'WhTransferOut', 'WhTransferOut', '药库调拨出库', 'lucide:arrow-right-left',
    '/inventory/warehouse/transfer-out', '/inventory/warehouse/transfer-out/index', '', '284503862625177600', 11,
    TRUE, FALSE, FALSE, '', 'menu', '药库调拨出库 + 医保3502(102)', NULL,
    TIMESTAMP '2026-06-14 18:00:00+00', TIMESTAMP '2026-06-14 18:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE
),
(
    '310400000000000112', 'WhTransferIn', 'WhTransferIn', '药库调拨入库', 'lucide:arrow-right-left',
    '/inventory/warehouse/transfer-in', '/inventory/warehouse/transfer-in/index', '', '284503862625177600', 12,
    TRUE, FALSE, FALSE, '', 'menu', '药房调拨入库 + 医保3502(101)', NULL,
    TIMESTAMP '2026-06-14 18:00:00+00', TIMESTAMP '2026-06-14 18:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE
)
ON CONFLICT (id) DO UPDATE SET
    code            = EXCLUDED.code,
    name            = EXCLUDED.name,
    display_name    = EXCLUDED.display_name,
    icon            = EXCLUDED.icon,
    path            = EXCLUDED.path,
    component       = EXCLUDED.component,
    permission_name = EXCLUDED.permission_name,
    parent_id       = EXCLUDED.parent_id,
    sort_order      = EXCLUDED.sort_order,
    is_enabled      = EXCLUDED.is_enabled,
    is_hidden       = EXCLUDED.is_hidden,
    remark          = EXCLUDED.remark,
    is_deleted      = FALSE,
    deleted_at      = NULL,
    deleted_by      = NULL,
    updated_at      = EXCLUDED.updated_at;

-- ----- V87__inventory_return_menu.sql -----
-- V87：退库单菜单（药房退库 / 药库退库 / 科室退库）

INSERT INTO whale_menus (
    id, code, name, display_name, icon, path, component, permission_name, parent_id,
    sort_order, is_enabled, is_hidden, is_external, external_url, menu_type, remark,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by, keep_alive
)
VALUES
(
    '310400000000000114', 'PhaReturnUpstream', 'PhaReturnUpstream', '药房退库', 'lucide:undo-2',
    '/inventory/pharmacy/return-upstream', '/inventory/pharmacy/return-upstream/index', '', '284503862625177600', 14,
    TRUE, FALSE, FALSE, '', 'menu', '药房退库至药库', NULL,
    TIMESTAMP '2026-06-14 18:00:00+00', TIMESTAMP '2026-06-14 18:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE
),
(
    '310400000000000115', 'WhReturnFromDownstream', 'WhReturnFromDownstream', '药库退库', 'lucide:undo-2',
    '/inventory/warehouse/return-from-downstream', '/inventory/warehouse/return-from-downstream/index', '', '284503862625177600', 15,
    TRUE, FALSE, FALSE, '', 'menu', '下游退回药库', NULL,
    TIMESTAMP '2026-06-14 18:00:00+00', TIMESTAMP '2026-06-14 18:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE
),
(
    '310400000000000116', 'DeptReturn', 'DeptReturn', '科室退库', 'lucide:undo-2',
    '/inventory/department/dept-return', '/inventory/department/dept-return/index', '', '284503862625177600', 16,
    TRUE, FALSE, FALSE, '', 'menu', '科室退库至药库', NULL,
    TIMESTAMP '2026-06-14 18:00:00+00', TIMESTAMP '2026-06-14 18:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE
)
ON CONFLICT (id) DO UPDATE SET
    code            = EXCLUDED.code,
    name            = EXCLUDED.name,
    display_name    = EXCLUDED.display_name,
    icon            = EXCLUDED.icon,
    path            = EXCLUDED.path,
    component       = EXCLUDED.component,
    permission_name = EXCLUDED.permission_name,
    parent_id       = EXCLUDED.parent_id,
    sort_order      = EXCLUDED.sort_order,
    is_enabled      = EXCLUDED.is_enabled,
    is_hidden       = EXCLUDED.is_hidden,
    remark          = EXCLUDED.remark,
    is_deleted      = FALSE,
    deleted_at      = NULL,
    deleted_by      = NULL,
    updated_at      = EXCLUDED.updated_at;

-- ========== V81–V87 补全 Navigation 权限与管理员角色（R/1）授权 ==========
-- 原 V81–V87 仅写菜单且 permission_name 为空；非 admin 用户无法看到（MenuAppService 按 permission_name 匹配授权）

INSERT INTO whale_permissions (
    id, name, display_name, parent_name, description,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by
)
VALUES
    ('58', 'Navigation.Chsreconciliation', '对账清算', 'Navigation', '菜单入口: ChsReconciliation', NULL, TIMESTAMP '2026-06-17 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('59', 'Navigation.Chscatalogmappingworkbench', '目录对码工作台', 'Navigation', '菜单入口: ChsCatalogMappingWorkbench', NULL, TIMESTAMP '2026-06-17 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('60', 'Navigation.Chsinventoryops', '进销存运维', 'Navigation', '菜单入口: ChsInventoryOps', NULL, TIMESTAMP '2026-06-17 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('61', 'Navigation.Whreturntosupplier', '药库退货单', 'Navigation', '菜单入口: WhReturnToSupplier', NULL, TIMESTAMP '2026-06-17 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('62', 'Navigation.Deptstocktakeorder', '科室盘点单', 'Navigation', '菜单入口: DeptStocktakeOrder', NULL, TIMESTAMP '2026-06-17 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('63', 'Navigation.Whgainloss', '药库损益单', 'Navigation', '菜单入口: WhGainLoss', NULL, TIMESTAMP '2026-06-17 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('64', 'Navigation.Phagainloss', '药房损益单', 'Navigation', '菜单入口: PhaGainLoss', NULL, TIMESTAMP '2026-06-17 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('65', 'Navigation.Deptgainloss', '科室损益单', 'Navigation', '菜单入口: DeptGainLoss', NULL, TIMESTAMP '2026-06-17 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('66', 'Navigation.Whtransferout', '药库调拨出库', 'Navigation', '菜单入口: WhTransferOut', NULL, TIMESTAMP '2026-06-17 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('67', 'Navigation.Whtransferin', '药库调拨入库', 'Navigation', '菜单入口: WhTransferIn', NULL, TIMESTAMP '2026-06-17 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('68', 'Navigation.Phareturnupstream', '药房退库', 'Navigation', '菜单入口: PhaReturnUpstream', NULL, TIMESTAMP '2026-06-17 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('69', 'Navigation.Whreturnfromdownstream', '药库退库', 'Navigation', '菜单入口: WhReturnFromDownstream', NULL, TIMESTAMP '2026-06-17 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('70', 'Navigation.Deptreturn', '科室退库', 'Navigation', '菜单入口: DeptReturn', NULL, TIMESTAMP '2026-06-17 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    display_name = EXCLUDED.display_name,
    parent_name = EXCLUDED.parent_name,
    description = EXCLUDED.description,
    is_deleted = FALSE,
    updated_at = EXCLUDED.updated_at;

UPDATE whale_menus SET permission_name = 'Navigation.Chsreconciliation', updated_at = TIMESTAMP '2026-06-17 00:00:00+00' WHERE id = '310500000000000103' AND is_deleted = FALSE;
UPDATE whale_menus SET permission_name = 'Navigation.Chscatalogmappingworkbench', updated_at = TIMESTAMP '2026-06-17 00:00:00+00' WHERE id = '310500000000000104' AND is_deleted = FALSE;
UPDATE whale_menus SET permission_name = 'Navigation.Chsinventoryops', updated_at = TIMESTAMP '2026-06-17 00:00:00+00' WHERE id = '310500000000000105' AND is_deleted = FALSE;
UPDATE whale_menus SET permission_name = 'Navigation.Whreturntosupplier', updated_at = TIMESTAMP '2026-06-17 00:00:00+00' WHERE id = '310400000000000106' AND is_deleted = FALSE;
UPDATE whale_menus SET permission_name = 'Navigation.Deptstocktakeorder', updated_at = TIMESTAMP '2026-06-17 00:00:00+00' WHERE id = '310400000000000107' AND is_deleted = FALSE;
UPDATE whale_menus SET permission_name = 'Navigation.Whgainloss', updated_at = TIMESTAMP '2026-06-17 00:00:00+00' WHERE id = '310400000000000108' AND is_deleted = FALSE;
UPDATE whale_menus SET permission_name = 'Navigation.Phagainloss', updated_at = TIMESTAMP '2026-06-17 00:00:00+00' WHERE id = '310400000000000109' AND is_deleted = FALSE;
UPDATE whale_menus SET permission_name = 'Navigation.Deptgainloss', updated_at = TIMESTAMP '2026-06-17 00:00:00+00' WHERE id = '310400000000000110' AND is_deleted = FALSE;
UPDATE whale_menus SET permission_name = 'Navigation.Whtransferout', updated_at = TIMESTAMP '2026-06-17 00:00:00+00' WHERE id = '310400000000000111' AND is_deleted = FALSE;
UPDATE whale_menus SET permission_name = 'Navigation.Whtransferin', updated_at = TIMESTAMP '2026-06-17 00:00:00+00' WHERE id = '310400000000000112' AND is_deleted = FALSE;
UPDATE whale_menus SET permission_name = 'Navigation.Phareturnupstream', updated_at = TIMESTAMP '2026-06-17 00:00:00+00' WHERE id = '310400000000000114' AND is_deleted = FALSE;
UPDATE whale_menus SET permission_name = 'Navigation.Whreturnfromdownstream', updated_at = TIMESTAMP '2026-06-17 00:00:00+00' WHERE id = '310400000000000115' AND is_deleted = FALSE;
UPDATE whale_menus SET permission_name = 'Navigation.Deptreturn', updated_at = TIMESTAMP '2026-06-17 00:00:00+00' WHERE id = '310400000000000116' AND is_deleted = FALSE;

INSERT INTO whale_permission_grants (
    id, permission_name, provider_name, provider_key,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by
)
VALUES
    ('10057', 'Navigation.Chsreconciliation', 'R', '1', NULL, TIMESTAMP '2026-06-17 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('10058', 'Navigation.Chscatalogmappingworkbench', 'R', '1', NULL, TIMESTAMP '2026-06-17 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('10059', 'Navigation.Chsinventoryops', 'R', '1', NULL, TIMESTAMP '2026-06-17 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('10060', 'Navigation.Whreturntosupplier', 'R', '1', NULL, TIMESTAMP '2026-06-17 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('10061', 'Navigation.Deptstocktakeorder', 'R', '1', NULL, TIMESTAMP '2026-06-17 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('10062', 'Navigation.Whgainloss', 'R', '1', NULL, TIMESTAMP '2026-06-17 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('10063', 'Navigation.Phagainloss', 'R', '1', NULL, TIMESTAMP '2026-06-17 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('10064', 'Navigation.Deptgainloss', 'R', '1', NULL, TIMESTAMP '2026-06-17 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('10065', 'Navigation.Whtransferout', 'R', '1', NULL, TIMESTAMP '2026-06-17 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('10066', 'Navigation.Whtransferin', 'R', '1', NULL, TIMESTAMP '2026-06-17 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('10067', 'Navigation.Phareturnupstream', 'R', '1', NULL, TIMESTAMP '2026-06-17 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('10068', 'Navigation.Whreturnfromdownstream', 'R', '1', NULL, TIMESTAMP '2026-06-17 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('10069', 'Navigation.Deptreturn', 'R', '1', NULL, TIMESTAMP '2026-06-17 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('10070', 'Navigation.Medicinereturnip', 'R', '1', NULL, TIMESTAMP '2026-06-17 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL)
ON CONFLICT (permission_name, provider_name, provider_key) DO UPDATE SET
    is_deleted = FALSE,
    deleted_at = NULL,
    deleted_by = NULL,
    updated_at = TIMESTAMP '2026-06-17 00:00:00+00';

-- 代理登录：系统管理员（R / 1）授予用户代理权限
INSERT INTO whale_permission_grants (
    id, permission_name, provider_name, provider_key,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by
)
VALUES (
    '10074', 'Identity.Users.Impersonate', 'R', '1', NULL,
    TIMESTAMP '2026-06-09 04:17:09.507+00', NULL, 1, NULL, FALSE, NULL, NULL
)
ON CONFLICT (permission_name, provider_name, provider_key) DO UPDATE SET
    is_deleted = FALSE,
    deleted_at = NULL,
    deleted_by = NULL,
    updated_at = TIMESTAMP '2026-06-09 04:17:09.507+00';

-- Report 模块：补管理员角色对 Report 分组权限（与 Booking 模块 Booking 分组授权同构）
INSERT INTO whale_permission_grants (
    id, permission_name, provider_name, provider_key,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by
)
VALUES (
    '310800000000000009', 'Report', 'R', '1', NULL,
    TIMESTAMP '2026-06-17 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL
)
ON CONFLICT (permission_name, provider_name, provider_key) DO UPDATE SET
    is_deleted = FALSE,
    deleted_at = NULL,
    deleted_by = NULL,
    updated_at = TIMESTAMP '2026-06-17 00:00:00+00';

-- ========== V88：Booking（原 booking V6）+ 样本 / CHS 目录权限（对齐现网 98 条菜单） ==========

-- ----- 1. Booking 权限（原 booking-application V6__Booking_Menus.sql） -----
INSERT INTO whale_permissions (
    id, name, display_name, parent_name, description,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by
)
VALUES
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

-- ----- 2. Booking 菜单 -----
INSERT INTO whale_menus (
    id, code, name, display_name, icon, path, component, permission_name, parent_id,
    sort_order, is_enabled, is_hidden, is_external, external_url, menu_type, remark,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by, keep_alive
)
VALUES
    ('310900000000000100', 'BookingHub', 'BookingHub', '统一预约', 'lucide:calendar-range',
     '/booking', '', NULL, NULL, 8, TRUE, FALSE, FALSE, '', 'directory',
     '门诊排班 / 号源 / 预约 / 候诊叫号 / 策略 / 模板', NULL,
     TIMESTAMP '2026-06-13 00:00:00+00', TIMESTAMP '2026-06-13 00:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE),
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

-- ----- 3. 样本管理 + CHS 国家目录 / 目录下载 Navigation 权限 -----
INSERT INTO whale_permissions (
    id, name, display_name, parent_name, description,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by
)
VALUES
    ('317000463887929349', 'Navigation.SpecimenDefinition', '样本管理', 'Navigation',
     '菜单入口: specimen_definition', NULL,
     TIMESTAMP '2026-06-12 08:18:12.580+00', TIMESTAMP '2026-06-12 08:18:12.580+00', 1, 1, FALSE, NULL, NULL),
    ('71', 'Navigation.Chsnationalcatalog', '国家目录查询', 'Navigation',
     '菜单入口: ChsNationalCatalog', NULL,
     TIMESTAMP '2026-06-17 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('72', 'Navigation.Chscatalogdownload', '目录文件下载', 'Navigation',
     '菜单入口: ChsCatalogDownload', NULL,
     TIMESTAMP '2026-06-17 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    display_name = EXCLUDED.display_name,
    parent_name = EXCLUDED.parent_name,
    description = EXCLUDED.description,
    is_deleted = FALSE,
    updated_at = EXCLUDED.updated_at;

INSERT INTO whale_menus (
    id, code, name, display_name, icon, path, component, permission_name, parent_id,
    sort_order, is_enabled, is_hidden, is_external, external_url, menu_type, remark,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by, keep_alive
)
VALUES (
    '317000463887929350', 'specimen_definition', 'SpecimenDefinition', '样本管理', 'lucide:test-tubes',
    '/basic/specimendefinition', '/basic/specimendefinition/index', 'Navigation.SpecimenDefinition', '2000', 24,
    TRUE, FALSE, FALSE, '', 'menu', '基础数据-样本定义维护（SpecimenDefinitionAppService）', NULL,
    TIMESTAMP '2026-06-12 08:18:12.580+00', TIMESTAMP '2026-06-12 08:18:12.580+00', 1, 1, FALSE, NULL, NULL, TRUE
)
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

UPDATE whale_menus SET permission_name = 'Navigation.Chsnationalcatalog', updated_at = TIMESTAMP '2026-06-17 00:00:00+00' WHERE id = '310500000000000101' AND is_deleted = FALSE;
UPDATE whale_menus SET permission_name = 'Navigation.Chscatalogdownload', updated_at = TIMESTAMP '2026-06-17 00:00:00+00' WHERE id = '310500000000000102' AND is_deleted = FALSE;

-- ----- 4. 管理员角色（R / 1）授权：Booking + 样本 + CHS 目录 -----
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
    ('310900000000000021', 'Booking.Template.View', 'R', '1', NULL, TIMESTAMP '2026-06-14 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('317000463887929351', 'Navigation.SpecimenDefinition', 'R', '1', NULL, TIMESTAMP '2026-06-12 08:18:12.580+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('10071', 'Navigation.Chsnationalcatalog', 'R', '1', NULL, TIMESTAMP '2026-06-17 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('10072', 'Navigation.Chscatalogdownload', 'R', '1', NULL, TIMESTAMP '2026-06-17 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('10073', 'Navigation.Reporttemplatedesigner', 'R', '1', NULL, TIMESTAMP '2026-06-17 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL)
ON CONFLICT (permission_name, provider_name, provider_key) DO UPDATE SET
    is_deleted = FALSE,
    deleted_at = NULL,
    deleted_by = NULL,
    updated_at = TIMESTAMP '2026-06-17 00:00:00+00';


-- ----- incremental whale seeds (from git HEAD V77–V98, INSERT only) -----

INSERT INTO whale_menus (
    id, code, name, display_name, icon, path, component, permission_name, parent_id,
    sort_order, is_enabled, is_hidden, is_external, external_url, menu_type, remark,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by, keep_alive
)
VALUES (
    '310500000000000106', 'ChsMedicalInfoQuery', 'ChsMedicalInfoQuery', '医保信息查询', 'lucide:search',
    '/chs/medical-info-query', '/chs/medical-info-query/index', '', '310500000000000100', 4,
    TRUE, FALSE, FALSE, '', 'menu', '5201–5206 就诊/结算/慢特病/累计查询', NULL,
    TIMESTAMP '2026-06-15 12:00:00+00', TIMESTAMP '2026-06-15 12:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO whale_permissions (
    id, name, display_name, parent_name, description,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by
)
VALUES
    ('310700000000000010', 'Navigation.ChsNationalCatalog', '国家目录查询', 'Navigation',
     '菜单入口: ChsNationalCatalog', NULL,
     TIMESTAMP '2026-06-15 12:00:00+00', TIMESTAMP '2026-06-15 12:00:00+00', 1, 1, FALSE, NULL, NULL),
    ('310700000000000011', 'Navigation.ChsCatalogDownload', '目录文件下载', 'Navigation',
     '菜单入口: ChsCatalogDownload', NULL,
     TIMESTAMP '2026-06-15 12:00:00+00', TIMESTAMP '2026-06-15 12:00:00+00', 1, 1, FALSE, NULL, NULL),
    ('310700000000000012', 'Navigation.ChsReconciliation', '对账清算', 'Navigation',
     '菜单入口: ChsReconciliation', NULL,
     TIMESTAMP '2026-06-15 12:00:00+00', TIMESTAMP '2026-06-15 12:00:00+00', 1, 1, FALSE, NULL, NULL),
    ('310700000000000013', 'Navigation.ChsCatalogMappingWorkbench', '目录对码工作台', 'Navigation',
     '菜单入口: ChsCatalogMappingWorkbench', NULL,
     TIMESTAMP '2026-06-15 12:00:00+00', TIMESTAMP '2026-06-15 12:00:00+00', 1, 1, FALSE, NULL, NULL),
    ('310700000000000014', 'Navigation.ChsInventoryOps', '进销存运维', 'Navigation',
     '菜单入口: ChsInventoryOps', NULL,
     TIMESTAMP '2026-06-15 12:00:00+00', TIMESTAMP '2026-06-15 12:00:00+00', 1, 1, FALSE, NULL, NULL),
    ('310700000000000015', 'Navigation.ChsMedicalInfoQuery', '医保信息查询', 'Navigation',
     '菜单入口: ChsMedicalInfoQuery — 5201–5206', NULL,
     TIMESTAMP '2026-06-15 12:00:00+00', TIMESTAMP '2026-06-15 12:00:00+00', 1, 1, FALSE, NULL, NULL)
ON CONFLICT (id) DO NOTHING;

INSERT INTO whale_permission_grants (
    id, permission_name, provider_name, provider_key,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by
)
VALUES
    ('310800000000000010', 'Navigation.ChsNationalCatalog', 'R', '1', NULL,
     TIMESTAMP '2026-06-15 12:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('310800000000000011', 'Navigation.ChsCatalogDownload', 'R', '1', NULL,
     TIMESTAMP '2026-06-15 12:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('310800000000000012', 'Navigation.ChsReconciliation', 'R', '1', NULL,
     TIMESTAMP '2026-06-15 12:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('310800000000000013', 'Navigation.ChsCatalogMappingWorkbench', 'R', '1', NULL,
     TIMESTAMP '2026-06-15 12:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('310800000000000014', 'Navigation.ChsInventoryOps', 'R', '1', NULL,
     TIMESTAMP '2026-06-15 12:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('310800000000000015', 'Navigation.ChsMedicalInfoQuery', 'R', '1', NULL,
     TIMESTAMP '2026-06-15 12:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL)
ON CONFLICT (permission_name, provider_name, provider_key) DO NOTHING;

INSERT INTO whale_menus (
    id, code, name, display_name, icon, path, component, permission_name, parent_id,
    sort_order, is_enabled, is_hidden, is_external, external_url, menu_type, remark,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by, keep_alive
)
VALUES (
    '1010', 'job_management', 'JobManagement', '定时任务', 'lucide:timer',
    '/system/job', '/system/job/index', 'Navigation.JobManagement', '1000', 7,
    TRUE, FALSE, FALSE, '', 'menu', 'Quartz 定时任务管理（whale_qrtz_*）', NULL,
    TIMESTAMP '2026-06-18 12:00:00+00', TIMESTAMP '2026-06-18 12:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO whale_permissions (
    id, name, display_name, parent_name, description,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by
)
VALUES (
    '330000000000000001', 'Navigation.JobManagement', '定时任务', 'Navigation',
    '菜单入口: job_management', NULL,
    TIMESTAMP '2026-06-18 12:00:00+00', TIMESTAMP '2026-06-18 12:00:00+00',
    1, 1, FALSE, NULL, NULL
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO whale_permission_grants (
    id, permission_name, provider_name, provider_key,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by
)
VALUES (
    '330000000000000002', 'Navigation.JobManagement', 'R', '1', NULL,
    TIMESTAMP '2026-06-18 12:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL
)
ON CONFLICT (permission_name, provider_name, provider_key) DO NOTHING;

INSERT INTO whale_qrtz_job_details (
    sched_name, job_name, job_group, description, job_class_name,
    is_durable, is_nonconcurrent, is_update_data, requests_recovery, job_data
) VALUES (
    'WhaleScheduler',
    '住院滚费',
    'DEFAULT',
    '住院滚费',
    'com.openhis.whale.core.infrastructure.job.QuartzDisallowConcurrentJobAdapter',
    false,
    true,
    false,
    false,
    '\x230d0a23547565204a756e2032332031343a32323a32302043535420323032360d0a6a6f624265616e4e616d653d6e7572736553746174696f6e496e70617469656e7442696c6c696e672f65786563757465496e70617469656e744175746f526f6c6c4a6f620d0a646973706c61794e616d653d5c75344634465c75393636325c75364544415c75384433390d0a74656e616e7449643d310d0a6d697366697265506f6c6963793d44454641554c540d0a636f6e63757272656e74506f6c6963793d444953414c4c4f570d0a'::bytea
) ON CONFLICT (sched_name, job_name, job_group) DO NOTHING;

INSERT INTO whale_qrtz_triggers (
    sched_name, trigger_name, trigger_group, job_name, job_group,
    description, next_fire_time, prev_fire_time, priority,
    trigger_state, trigger_type, start_time, end_time,
    calendar_name, misfire_instr, job_data
) VALUES (
    'WhaleScheduler',
    '住院滚费_trigger',
    'DEFAULT',
    '住院滚费',
    'DEFAULT',
    NULL,
    0,
    -1,
    5,
    'PAUSED',
    'CRON',
    (EXTRACT(EPOCH FROM TIMESTAMP '2026-01-01 00:00:00+08') * 1000)::bigint,
    0,
    NULL,
    0,
    NULL
) ON CONFLICT (sched_name, trigger_name, trigger_group) DO NOTHING;

INSERT INTO whale_qrtz_cron_triggers (
    sched_name, trigger_name, trigger_group, cron_expression, time_zone_id
) VALUES (
    'WhaleScheduler',
    '住院滚费_trigger',
    'DEFAULT',
    '0 5 0 * * ?',
    'Asia/Shanghai'
) ON CONFLICT (sched_name, trigger_name, trigger_group) DO NOTHING;

INSERT INTO whale_menus (
    id, code, name, display_name, icon, path, component, permission_name, parent_id,
    sort_order, is_enabled, is_hidden, is_external, external_url, menu_type, remark,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by, keep_alive
)
VALUES (
    '310400000000000122', 'ReportCenterRoot', 'ReportCenterRoot', '报表中心', 'lucide:layout-dashboard',
    '/report-center', '', NULL, NULL, 11,
    TRUE, FALSE, FALSE, '', 'directory', 'HIS 报表中心（门诊费用明细等）', NULL,
    TIMESTAMP '2026-06-25 00:00:00+00', TIMESTAMP '2026-06-25 00:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO whale_menus (
    id, code, name, display_name, icon, path, component, permission_name, parent_id,
    sort_order, is_enabled, is_hidden, is_external, external_url, menu_type, remark,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by, keep_alive
)
VALUES (
    '310400000000000123', 'ConfigManagementRoot', 'ConfigManagementRoot', '配置管理', 'lucide:settings-2',
    '/config', '', NULL, NULL, 12,
    TRUE, FALSE, FALSE, '', 'directory', 'HIS 配置管理（病历/执行地点/组套/取药科室/耗材绑定等）', NULL,
    TIMESTAMP '2026-06-25 00:00:00+00', TIMESTAMP '2026-06-25 00:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO whale_menus (
    id, code, name, display_name, icon, path, component, permission_name, parent_id,
    sort_order, is_enabled, is_hidden, is_external, external_url, menu_type, remark,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by, keep_alive
)
VALUES (
    '310400000000000124', 'PrintManagementRoot', 'PrintManagementRoot', '打印管理', 'lucide:printer',
    '/print', '', NULL, NULL, 16,
    TRUE, FALSE, FALSE, '', 'directory', 'HIS 打印管理（模板/绑定/日志）', NULL,
    TIMESTAMP '2026-06-25 00:00:00+00', TIMESTAMP '2026-06-25 00:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO whale_permissions (
    id, name, display_name, parent_name, description,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by
)
VALUES (
    '310900000000000012', 'Booking.SlotCalendar.View', '号源日历', 'Booking',
    '菜单入口: BookingSlotCalendar', NULL,
    TIMESTAMP '2026-06-25 00:00:00+00', TIMESTAMP '2026-06-25 00:00:00+00', 1, 1, FALSE, NULL, NULL
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO whale_permissions (
    id, name, display_name, parent_name, description,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by
)
VALUES
    ('310700000000000016', 'ConfigManagement', '配置管理', NULL,
     'HIS 配置管理入口权限分组（病历/执行地点/组套/取药科室/耗材绑定）', NULL,
     TIMESTAMP '2026-06-25 00:00:00+00', TIMESTAMP '2026-06-25 00:00:00+00', 1, 1, FALSE, NULL, NULL),
    ('310700000000000017', 'PrintManagement', '打印管理', NULL,
     'HIS 打印管理入口权限分组（PRT 模板/绑定/日志）', NULL,
     TIMESTAMP '2026-06-25 00:00:00+00', TIMESTAMP '2026-06-25 00:00:00+00', 1, 1, FALSE, NULL, NULL),
    ('310700000000000018', 'ReportCenter', '报表中心', NULL,
     'HIS 报表中心入口权限分组（门诊费用明细等）', NULL,
     TIMESTAMP '2026-06-25 00:00:00+00', TIMESTAMP '2026-06-25 00:00:00+00', 1, 1, FALSE, NULL, NULL)
ON CONFLICT (id) DO NOTHING;

INSERT INTO whale_permission_grants (
    id, permission_name, provider_name, provider_key,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by
)
SELECT 310800000000000037, 'Booking.SlotCalendar.View', 'R', '1', NULL,
       TIMESTAMP '2026-06-25 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL
WHERE NOT EXISTS (
    SELECT 1 FROM whale_permission_grants
    WHERE permission_name = 'Booking.SlotCalendar.View'
      AND provider_name = 'R'
      AND provider_key = '1'
      AND is_deleted = FALSE
);

INSERT INTO whale_permission_grants (
    id, permission_name, provider_name, provider_key,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by
)
VALUES
    ('310800000000000016', 'ConfigManagement', 'R', '1', NULL,
     TIMESTAMP '2026-06-25 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('310800000000000017', 'PrintManagement', 'R', '1', NULL,
     TIMESTAMP '2026-06-25 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('310800000000000018', 'ReportCenter', 'R', '1', NULL,
     TIMESTAMP '2026-06-25 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL)
ON CONFLICT (permission_name, provider_name, provider_key) DO NOTHING;

INSERT INTO whale_permission_grants (
    id, permission_name, provider_name, provider_key,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by
)
VALUES
    ('310800000000000020', 'Navigation.WarehouseManagement', 'R', '13', '1',
     TIMESTAMP '2026-06-25 00:00:00+00', TIMESTAMP '2026-06-25 00:00:00+00', 1, 1, FALSE, NULL, NULL)
ON CONFLICT (permission_name, provider_name, provider_key) DO NOTHING;

INSERT INTO whale_permission_grants (
    id, permission_name, provider_name, provider_key,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by
)
SELECT 310800000000000019, 'Booking.OutpatientSchedule.View', 'R', '10', '1',
       TIMESTAMP '2026-06-25 00:00:00+00', TIMESTAMP '2026-06-25 00:00:00+00', 1, 1, FALSE, NULL, NULL
WHERE NOT EXISTS (
    SELECT 1 FROM whale_permission_grants
    WHERE permission_name = 'Booking.OutpatientSchedule.View'
      AND provider_name = 'R'
      AND provider_key = '10'
      AND is_deleted = FALSE
);

INSERT INTO whale_permission_grants (
    id, permission_name, provider_name, provider_key,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by
)
SELECT 310800000000000036, 'Booking.SlotCalendar.View', 'R', '10', '1',
       TIMESTAMP '2026-06-25 00:00:00+00', TIMESTAMP '2026-06-25 00:00:00+00', 1, 1, FALSE, NULL, NULL
WHERE NOT EXISTS (
    SELECT 1 FROM whale_permission_grants
    WHERE permission_name = 'Booking.SlotCalendar.View'
      AND provider_name = 'R'
      AND provider_key = '10'
      AND is_deleted = FALSE
);

INSERT INTO whale_permission_grants (
    id, permission_name, provider_name, provider_key,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by
)
VALUES
    ('310800000000000022', 'Booking.Template.View', 'R', '323022177255297024', '1',
     TIMESTAMP '2026-06-25 00:00:00+00', TIMESTAMP '2026-06-25 00:00:00+00', 1, 1, FALSE, NULL, NULL),
    ('310800000000000023', 'Navigation.ChsCatalogDownload', 'R', '323022177255297024', '1',
     TIMESTAMP '2026-06-25 00:00:00+00', TIMESTAMP '2026-06-25 00:00:00+00', 1, 1, FALSE, NULL, NULL),
    ('310800000000000024', 'Navigation.ChsCatalogMappingWorkbench', 'R', '323022177255297024', '1',
     TIMESTAMP '2026-06-25 00:00:00+00', TIMESTAMP '2026-06-25 00:00:00+00', 1, 1, FALSE, NULL, NULL),
    ('310800000000000025', 'Navigation.ChsInventoryOps', 'R', '323022177255297024', '1',
     TIMESTAMP '2026-06-25 00:00:00+00', TIMESTAMP '2026-06-25 00:00:00+00', 1, 1, FALSE, NULL, NULL),
    ('310800000000000026', 'Navigation.ChsMedicalInfoQuery', 'R', '323022177255297024', '1',
     TIMESTAMP '2026-06-25 00:00:00+00', TIMESTAMP '2026-06-25 00:00:00+00', 1, 1, FALSE, NULL, NULL),
    ('310800000000000027', 'Navigation.ChsNationalCatalog', 'R', '323022177255297024', '1',
     TIMESTAMP '2026-06-25 00:00:00+00', TIMESTAMP '2026-06-25 00:00:00+00', 1, 1, FALSE, NULL, NULL),
    ('310800000000000028', 'Navigation.ChsReconciliation', 'R', '323022177255297024', '1',
     TIMESTAMP '2026-06-25 00:00:00+00', TIMESTAMP '2026-06-25 00:00:00+00', 1, 1, FALSE, NULL, NULL),
    ('310800000000000029', 'Navigation.JobManagement', 'R', '323022177255297024', '1',
     TIMESTAMP '2026-06-25 00:00:00+00', TIMESTAMP '2026-06-25 00:00:00+00', 1, 1, FALSE, NULL, NULL),
    ('310800000000000030', 'Navigation.Opfeedetail', 'R', '323022177255297024', '1',
     TIMESTAMP '2026-06-25 00:00:00+00', TIMESTAMP '2026-06-25 00:00:00+00', 1, 1, FALSE, NULL, NULL),
    ('310800000000000031', 'Navigation.SpecimenDefinition', 'R', '323022177255297024', '1',
     TIMESTAMP '2026-06-25 00:00:00+00', TIMESTAMP '2026-06-25 00:00:00+00', 1, 1, FALSE, NULL, NULL),
    ('310800000000000032', 'Print.PrintBinding.View', 'R', '323022177255297024', '1',
     TIMESTAMP '2026-06-25 00:00:00+00', TIMESTAMP '2026-06-25 00:00:00+00', 1, 1, FALSE, NULL, NULL),
    ('310800000000000033', 'Print.PrintLog.View', 'R', '323022177255297024', '1',
     TIMESTAMP '2026-06-25 00:00:00+00', TIMESTAMP '2026-06-25 00:00:00+00', 1, 1, FALSE, NULL, NULL),
    ('310800000000000034', 'Print.Templates.View', 'R', '323022177255297024', '1',
     TIMESTAMP '2026-06-25 00:00:00+00', TIMESTAMP '2026-06-25 00:00:00+00', 1, 1, FALSE, NULL, NULL)
ON CONFLICT (permission_name, provider_name, provider_key) DO NOTHING;

INSERT INTO whale_permission_grants (
    id, permission_name, provider_name, provider_key,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by
)
SELECT 310800000000000035, 'Booking.SlotCalendar.View', 'R', '323022177255297024', '1',
       TIMESTAMP '2026-06-25 00:00:00+00', TIMESTAMP '2026-06-25 00:00:00+00', 1, 1, FALSE, NULL, NULL
WHERE NOT EXISTS (
    SELECT 1 FROM whale_permission_grants
    WHERE permission_name = 'Booking.SlotCalendar.View'
      AND provider_name = 'R'
      AND provider_key = '323022177255297024'
      AND is_deleted = FALSE
);

INSERT INTO whale_menus (
    id, code, name, display_name, icon, path, component, permission_name, parent_id,
    sort_order, is_enabled, is_hidden, is_external, external_url, menu_type, remark,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by, keep_alive
)
VALUES (
    '310500000000000107', 'ChsPlatformDict', 'ChsPlatformDict', '平台字典(1901)', 'lucide:map',
    '/chs/platform-dict', '/chs/platform-dict/index', 'Navigation.ChsPlatformDict', '310500000000000100', 7,
    TRUE, FALSE, FALSE, '', 'menu', '1901 医保区划等字典手动同步与本地镜像查询', NULL,
    TIMESTAMP '2026-06-24 12:00:00+00', TIMESTAMP '2026-06-24 12:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO whale_permissions (
    id, name, display_name, parent_name, description,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by
)
VALUES (
    '310700000000000019', 'Navigation.ChsPlatformDict', '平台字典(1901)', 'Navigation',
    '菜单入口: ChsPlatformDict — 1901 参保地医保区划字典', NULL,
    TIMESTAMP '2026-06-24 12:00:00+00', TIMESTAMP '2026-06-24 12:00:00+00', 1, 1, FALSE, NULL, NULL
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO whale_permission_grants (
    id, permission_name, provider_name, provider_key,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by
)
VALUES (
    '310800000000000038', 'Navigation.ChsPlatformDict', 'R', '1', NULL,
    TIMESTAMP '2026-06-24 12:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL
)
ON CONFLICT (permission_name, provider_name, provider_key) DO NOTHING;

INSERT INTO whale_roles (
    id, name, display_name, code, description, is_default, is_static,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by
) VALUES
    ('15', 'medical_technician', '医技', 'ROLE_MEDICAL_TECH', '医技人员，负责检验检查等医技工作', true, true, NULL,
     '2026-06-25 12:00:00.000+00', '2026-06-25 12:00:00.000+00', NULL, NULL, false, NULL, NULL),
    ('16', 'staff_other', '其他', 'ROLE_STAFF_OTHER', '其他岗位人员（老库 STAFF_TYPE=1）', true, true, NULL,
     '2026-06-25 12:00:00.000+00', '2026-06-25 12:00:00.000+00', NULL, NULL, false, NULL, NULL)
ON CONFLICT (id) DO NOTHING;

INSERT INTO whale_role_claims (
    id, role_id, claim_type, claim_value, tenant_id,
    created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by
) VALUES
    ('105023', '15', 'AssignmentScope', 'ORG', NULL,
     '2026-06-25 12:00:00.000+00', '2026-06-25 12:00:00.000+00', NULL, NULL, false, NULL, NULL),
    ('105024', '15', 'StaffScope', 'DOCTOR_VISIT_ORG', NULL,
     '2026-06-25 12:00:00.000+00', '2026-06-25 12:00:00.000+00', NULL, NULL, false, NULL, NULL),
    ('105025', '16', 'AssignmentScope', 'ORG', NULL,
     '2026-06-25 12:00:00.000+00', '2026-06-25 12:00:00.000+00', NULL, NULL, false, NULL, NULL),
    ('105026', '11', 'StaffScope', 'NURSE_ORG', NULL,
     '2026-06-25 12:00:00.000+00', '2026-06-25 12:00:00.000+00', NULL, NULL, false, NULL, NULL)
ON CONFLICT (id) DO NOTHING;

