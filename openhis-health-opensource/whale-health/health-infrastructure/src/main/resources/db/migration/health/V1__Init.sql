-- V1: whale_* 框架表（建表 + 种子数据）
-- 由 reorganize-flyway-migrations.mjs 从旧版 V1–V21 合并生成

-- ========== whale_users ==========
CREATE TABLE IF NOT EXISTS whale_users (
    id BIGINT PRIMARY KEY,
    user_name VARCHAR(256) NOT NULL UNIQUE,
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
    deleted_by BIGINT
);

CREATE INDEX IF NOT EXISTS idx_users_email ON whale_users(email);

CREATE UNIQUE INDEX idx_users_email_unique ON whale_users(email) WHERE email IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_whale_users_is_deleted ON whale_users(is_deleted);

CREATE INDEX IF NOT EXISTS idx_whale_users_is_active ON whale_users(is_active) WHERE is_active = true;

CREATE INDEX IF NOT EXISTS idx_whale_users_is_locked_out ON whale_users(is_locked_out) WHERE is_locked_out = true;

CREATE INDEX IF NOT EXISTS idx_whale_users_created_at ON whale_users(created_at DESC);

ALTER TABLE whale_users ADD COLUMN IF NOT EXISTS ukey_id VARCHAR(128);

ALTER TABLE whale_users ADD COLUMN IF NOT EXISTS ukey_src_id VARCHAR(128);

CREATE INDEX IF NOT EXISTS idx_users_ukey_id ON whale_users(ukey_id);

CREATE INDEX IF NOT EXISTS idx_users_ukey_src_id ON whale_users(ukey_src_id);

-- seed: whale_users
INSERT INTO "whale_users" ("id", "user_name", "email", "phone_number", "password_hash", "name", "surname", "is_active", "is_locked_out", "lockout_end", "access_failed_count", "extra_properties", "created_at", "updated_at", "created_by", "updated_by", "is_deleted", "deleted_at", "deleted_by", "ukey_id", "ukey_src_id")
VALUES
(1, 'admin', 'admin@example.com', NULL, '$2a$10$ywn6Rm2sbAwnc/lEDLFWd.AqUkltBaKI4qNsuvyrHFMrgBT.I0vke', NULL, NULL, true, false, NULL, 0, NULL, '2026-05-03 06:07:04.503+00', '2026-05-30 02:20:21.934+00', NULL, NULL, false, NULL, NULL, NULL, NULL),
(2, 'zhangsan', 'zhangsan@example.com', 13800001001, '$2a$10$mAWgcvulgwoZjHCZkfLd7uQtU5E2TlFTofP2qYqn7PSG3ail2Vf0e', '张', '三', true, false, NULL, 0, NULL, '2026-05-03 06:07:04.503+00', '2026-05-03 06:15:49.336+00', 1, 1, false, NULL, NULL, NULL, NULL),
(3, 'lisi', 'lisi@example.com', 13800001002, '$2a$10$N9qo8uLOickgx2MRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '李', '四', true, false, NULL, 0, NULL, '2026-05-03 06:07:04.503+00', '2026-05-03 06:07:04.503+00', 1, NULL, false, NULL, NULL, NULL, NULL),
(4, 'wangwu', 'wangwu@example.com', 13800001003, '$2a$10$N9qo8uLOickgx2MRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '王', '五', true, false, NULL, 0, NULL, '2026-05-03 06:07:04.503+00', '2026-05-03 06:07:04.503+00', 1, NULL, false, NULL, NULL, NULL, NULL),
(5, 'zhaoliu', 'zhaoliu@example.com', 13800001004, '$2a$10$N9qo8uLOickgx2MRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '赵', '六', true, false, NULL, 0, NULL, '2026-05-03 06:07:04.503+00', '2026-05-03 06:07:04.503+00', 1, NULL, false, NULL, NULL, NULL, NULL),
(315330627751645184, 'test', NULL, NULL, '$2a$10$cLaXGOTmxDNkaXIvEP5uoubB2ozirs5lV7P8RdpkxBYecNhpyMqDm', '测试', NULL, true, false, NULL, 0, NULL, '2026-05-20 03:31:20.215+00', '2026-05-20 03:31:20.215+00', 1, 1, false, NULL, NULL, NULL, NULL),
(318594694756438016, 'zwh', NULL, NULL, '$2a$10$.Eoeg8cWGQ5e7Kaxtd3pOOu4JEHjn.Dz9h5LfVaZlBzKqswyyHlOG', '', NULL, true, false, NULL, 0, NULL, '2026-05-29 03:41:34.433+00', '2026-05-29 03:41:34.433+00', 1, 1, false, NULL, NULL, NULL, NULL)
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
(1, 'admin', '系统管理员', 'ROLE_ADMIN', '系统管理员，拥有所有权限', false, true, NULL, '2026-05-03 06:07:04.508+00', NULL, NULL, NULL, false, NULL, NULL),
(2, 'user', '普通用户', 'USER', '普通用户角色', true, false, NULL, '2026-05-03 06:07:04.508+00', NULL, NULL, NULL, false, NULL, NULL),
(10, 'doctor', '医生', 'ROLE_DOCTOR', '医生角色，负责诊疗相关工作', true, true, NULL, '2026-05-03 06:07:04.508+00', NULL, NULL, NULL, false, NULL, NULL),
(11, 'nurse', '护士', 'ROLE_NURSE', '护士角色，负责护理相关工作', true, true, NULL, '2026-05-03 06:07:04.508+00', NULL, NULL, NULL, false, NULL, NULL),
(12, 'pharmacist', '药师', 'ROLE_PHARMACIST', '药师角色，负责药房药品管理', true, true, NULL, '2026-05-03 06:07:04.508+00', NULL, NULL, NULL, false, NULL, NULL),
(13, 'warehouse_keeper', '库管', 'ROLE_WAREHOUSE', '库管角色，负责库存物资管理', true, true, NULL, '2026-05-03 06:07:04.508+00', NULL, NULL, NULL, false, NULL, NULL),
(14, 'accountant', '财务', 'ROLE_ACCOUNTANT', '财务角色，负责财务收费管理', true, true, NULL, '2026-05-03 06:07:04.508+00', NULL, NULL, NULL, false, NULL, NULL)
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
(1, 1, 1, NULL, '2026-05-03 06:07:04.512+00', NULL, NULL, NULL, false, NULL, NULL),
(2, 2, 10, 1, '2026-05-03 06:07:04.512+00', NULL, NULL, NULL, false, NULL, NULL),
(3, 3, 11, 1, '2026-05-03 06:07:04.512+00', NULL, NULL, NULL, false, NULL, NULL),
(4, 4, 2, 1, '2026-05-03 06:07:04.512+00', NULL, NULL, NULL, false, NULL, NULL),
(5, 5, 14, 2, '2026-05-03 06:07:04.512+00', NULL, NULL, NULL, false, NULL, NULL),
(315330627848114176, 315330627751645184, 1, 1, '2026-05-20 03:31:20.238+00', '2026-05-20 03:31:20.238+00', 1, 1, false, NULL, NULL),
(318594694873878528, 318594694756438016, 1, 1, '2026-05-29 03:41:34.461+00', '2026-05-29 03:41:34.461+00', 1, 1, false, NULL, NULL)
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
(100, 'Identity.Users', '用户管理', NULL, '用户管理模块', NULL, '2026-05-03 06:07:04.519+00', NULL, NULL, NULL, false, NULL, NULL),
(101, 'Identity.Users.Create', '创建用户', 'Identity.Users', '创建用户权限', NULL, '2026-05-03 06:07:04.519+00', NULL, NULL, NULL, false, NULL, NULL),
(102, 'Identity.Users.Update', '更新用户', 'Identity.Users', '更新用户权限', NULL, '2026-05-03 06:07:04.519+00', NULL, NULL, NULL, false, NULL, NULL),
(103, 'Identity.Users.Delete', '删除用户', 'Identity.Users', '删除用户权限', NULL, '2026-05-03 06:07:04.519+00', NULL, NULL, NULL, false, NULL, NULL),
(104, 'Identity.Users.View', '查看用户', 'Identity.Users', '查看用户权限', NULL, '2026-05-03 06:07:04.519+00', NULL, NULL, NULL, false, NULL, NULL),
(200, 'Identity.Roles', '角色管理', NULL, '角色管理模块', NULL, '2026-05-03 06:07:04.519+00', NULL, NULL, NULL, false, NULL, NULL),
(201, 'Identity.Roles.Create', '创建角色', 'Identity.Roles', '创建角色权限', NULL, '2026-05-03 06:07:04.519+00', NULL, NULL, NULL, false, NULL, NULL),
(202, 'Identity.Roles.Update', '更新角色', 'Identity.Roles', '更新角色权限', NULL, '2026-05-03 06:07:04.519+00', NULL, NULL, NULL, false, NULL, NULL),
(203, 'Identity.Roles.Delete', '删除角色', 'Identity.Roles', '删除角色权限', NULL, '2026-05-03 06:07:04.519+00', NULL, NULL, NULL, false, NULL, NULL),
(204, 'Identity.Roles.View', '查看角色', 'Identity.Roles', '查看角色权限', NULL, '2026-05-03 06:07:04.519+00', NULL, NULL, NULL, false, NULL, NULL),
(300, 'Identity.Permissions', '权限管理', NULL, '权限管理模块', NULL, '2026-05-03 06:07:04.519+00', NULL, NULL, NULL, false, NULL, NULL),
(301, 'Identity.Permissions.Grant', '授予权限', 'Identity.Permissions', '授予权限权限', NULL, '2026-05-03 06:07:04.519+00', NULL, NULL, NULL, false, NULL, NULL),
(302, 'Identity.Permissions.Revoke', '撤销权限', 'Identity.Permissions', '撤销权限权限', NULL, '2026-05-03 06:07:04.519+00', NULL, NULL, NULL, false, NULL, NULL),
(303, 'Identity.Permissions.View', '查看权限', 'Identity.Permissions', '查看权限权限', NULL, '2026-05-03 06:07:04.519+00', NULL, NULL, NULL, false, NULL, NULL),
(400, 'Identity.OrganizationUnits', '组织单元管理', NULL, '组织单元管理模块', NULL, '2026-05-03 06:07:04.519+00', NULL, NULL, NULL, false, NULL, NULL),
(401, 'Identity.OrganizationUnits.Create', '创建组织单元', 'Identity.OrganizationUnits', '创建组织单元权限', NULL, '2026-05-03 06:07:04.519+00', NULL, NULL, NULL, false, NULL, NULL),
(402, 'Identity.OrganizationUnits.Update', '更新组织单元', 'Identity.OrganizationUnits', '更新组织单元权限', NULL, '2026-05-03 06:07:04.519+00', NULL, NULL, NULL, false, NULL, NULL),
(403, 'Identity.OrganizationUnits.Delete', '删除组织单元', 'Identity.OrganizationUnits', '删除组织单元权限', NULL, '2026-05-03 06:07:04.519+00', NULL, NULL, NULL, false, NULL, NULL),
(404, 'Identity.OrganizationUnits.View', '查看组织单元', 'Identity.OrganizationUnits', '查看组织单元权限', NULL, '2026-05-03 06:07:04.519+00', NULL, NULL, NULL, false, NULL, NULL),
(500, 'Identity.Users.Impersonate', '代理登录', 'Identity.Users', '代理登录权限', NULL, '2026-05-03 06:07:04.519+00', NULL, NULL, NULL, false, NULL, NULL),
(600, 'TenantManagement.Tenants', '租户管理', NULL, '租户管理模块', NULL, '2026-05-03 06:07:04.519+00', NULL, NULL, NULL, false, NULL, NULL),
(601, 'TenantManagement.Tenants.Create', '创建租户', 'TenantManagement.Tenants', '创建租户权限', NULL, '2026-05-03 06:07:04.519+00', NULL, NULL, NULL, false, NULL, NULL),
(602, 'TenantManagement.Tenants.Update', '更新租户', 'TenantManagement.Tenants', '更新租户权限', NULL, '2026-05-03 06:07:04.519+00', NULL, NULL, NULL, false, NULL, NULL),
(603, 'TenantManagement.Tenants.Delete', '删除租户', 'TenantManagement.Tenants', '删除租户权限', NULL, '2026-05-03 06:07:04.519+00', NULL, NULL, NULL, false, NULL, NULL),
(604, 'TenantManagement.Tenants.View', '查看租户', 'TenantManagement.Tenants', '查看租户权限', NULL, '2026-05-03 06:07:04.519+00', NULL, NULL, NULL, false, NULL, NULL),
(700, 'SettingManagement.Settings', '设置管理', NULL, '设置管理模块', NULL, '2026-05-03 06:07:04.519+00', NULL, NULL, NULL, false, NULL, NULL),
(701, 'SettingManagement.Settings.Create', '创建设置', 'SettingManagement.Settings', '创建设置权限', NULL, '2026-05-03 06:07:04.519+00', NULL, NULL, NULL, false, NULL, NULL),
(702, 'SettingManagement.Settings.Update', '更新设置', 'SettingManagement.Settings', '更新设置权限', NULL, '2026-05-03 06:07:04.519+00', NULL, NULL, NULL, false, NULL, NULL),
(703, 'SettingManagement.Settings.Delete', '删除设置', 'SettingManagement.Settings', '删除设置权限', NULL, '2026-05-03 06:07:04.519+00', NULL, NULL, NULL, false, NULL, NULL),
(704, 'SettingManagement.Settings.View', '查看设置', 'SettingManagement.Settings', '查看设置权限', NULL, '2026-05-03 06:07:04.519+00', NULL, NULL, NULL, false, NULL, NULL),
(800, 'Menu.Menus', '菜单管理', NULL, '菜单管理模块', NULL, '2026-05-03 06:07:04.519+00', NULL, NULL, NULL, false, NULL, NULL),
(801, 'Menu.Menus.Create', '创建菜单', 'Menu.Menus', '创建菜单权限', NULL, '2026-05-03 06:07:04.519+00', NULL, NULL, NULL, false, NULL, NULL),
(802, 'Menu.Menus.Update', '更新菜单', 'Menu.Menus', '更新菜单权限', NULL, '2026-05-03 06:07:04.519+00', NULL, NULL, NULL, false, NULL, NULL),
(803, 'Menu.Menus.Delete', '删除菜单', 'Menu.Menus', '删除菜单权限', NULL, '2026-05-03 06:07:04.519+00', NULL, NULL, NULL, false, NULL, NULL),
(804, 'Menu.Menus.View', '查看菜单', 'Menu.Menus', '查看菜单权限', NULL, '2026-05-03 06:07:04.519+00', NULL, NULL, NULL, false, NULL, NULL),
(850, 'Auditing.AuditLogs', '审计日志', NULL, '审计日志模块', NULL, '2026-05-03 06:07:04.519+00', NULL, NULL, NULL, false, NULL, NULL),
(851, 'Auditing.AuditLogs.View', '查看审计日志', 'Auditing.AuditLogs', '查看审计日志权限', NULL, '2026-05-03 06:07:04.519+00', NULL, NULL, NULL, false, NULL, NULL),
(900, 'Terminology.CodeSystems', '编码系统管理', NULL, '编码系统管理模块', NULL, '2026-05-03 06:07:04.519+00', NULL, NULL, NULL, false, NULL, NULL),
(901, 'Terminology.CodeSystems.Create', '创建编码系统', 'Terminology.CodeSystems', '创建编码系统权限', NULL, '2026-05-03 06:07:04.519+00', NULL, NULL, NULL, false, NULL, NULL),
(902, 'Terminology.CodeSystems.Update', '更新编码系统', 'Terminology.CodeSystems', '更新编码系统权限', NULL, '2026-05-03 06:07:04.519+00', NULL, NULL, NULL, false, NULL, NULL),
(903, 'Terminology.CodeSystems.Delete', '删除编码系统', 'Terminology.CodeSystems', '删除编码系统权限', NULL, '2026-05-03 06:07:04.519+00', NULL, NULL, NULL, false, NULL, NULL),
(904, 'Terminology.CodeSystems.View', '查看编码系统', 'Terminology.CodeSystems', '查看编码系统权限', NULL, '2026-05-03 06:07:04.519+00', NULL, NULL, NULL, false, NULL, NULL),
(910, 'Administration.BodyStructures', '部位管理', NULL, '部位管理模块', NULL, '2026-05-03 06:07:04.519+00', NULL, NULL, NULL, false, NULL, NULL),
(911, 'Administration.BodyStructures.Create', '创建部位', 'Administration.BodyStructures', '创建部位权限', NULL, '2026-05-03 06:07:04.519+00', NULL, NULL, NULL, false, NULL, NULL),
(912, 'Administration.BodyStructures.Update', '更新部位', 'Administration.BodyStructures', '更新部位权限', NULL, '2026-05-03 06:07:04.519+00', NULL, NULL, NULL, false, NULL, NULL),
(913, 'Administration.BodyStructures.Delete', '删除部位', 'Administration.BodyStructures', '删除部位权限', NULL, '2026-05-03 06:07:04.519+00', NULL, NULL, NULL, false, NULL, NULL),
(914, 'Administration.BodyStructures.View', '查看部位', 'Administration.BodyStructures', '查看部位权限', NULL, '2026-05-03 06:07:04.519+00', NULL, NULL, NULL, false, NULL, NULL),
(920, 'Administration.Locations', '位置管理', NULL, '位置管理模块（FHIR Location）', NULL, '2026-05-03 06:07:04.519+00', NULL, NULL, NULL, false, NULL, NULL),
(921, 'Administration.Locations.Create', '创建位置', 'Administration.Locations', '创建位置权限', NULL, '2026-05-03 06:07:04.519+00', NULL, NULL, NULL, false, NULL, NULL),
(922, 'Administration.Locations.Update', '更新位置', 'Administration.Locations', '更新位置权限', NULL, '2026-05-03 06:07:04.519+00', NULL, NULL, NULL, false, NULL, NULL),
(923, 'Administration.Locations.Delete', '删除位置', 'Administration.Locations', '删除位置权限', NULL, '2026-05-03 06:07:04.519+00', NULL, NULL, NULL, false, NULL, NULL),
(924, 'Administration.Locations.View', '查看位置', 'Administration.Locations', '查看位置权限', NULL, '2026-05-03 06:07:04.519+00', NULL, NULL, NULL, false, NULL, NULL),
(925, 'Administration.WardLocations', '病区病房', NULL, '病区/病房位置查看', NULL, '2026-05-03 06:07:04.519+00', NULL, NULL, NULL, false, NULL, NULL),
(926, 'Administration.WardLocations.View', '查看病区病房', 'Administration.WardLocations', '查看病区病房权限', NULL, '2026-05-03 06:07:04.519+00', NULL, NULL, NULL, false, NULL, NULL),
(930, 'Administration.Patients', '患者管理', NULL, '患者管理模块（FHIR Patient）', NULL, '2026-05-03 06:07:04.519+00', NULL, NULL, NULL, false, NULL, NULL),
(931, 'Administration.Patients.View', '查看患者', 'Administration.Patients', '查看患者权限', NULL, '2026-05-03 06:07:04.519+00', NULL, NULL, NULL, false, NULL, NULL),
(932, 'Administration.Patients.Create', '创建患者', 'Administration.Patients', '创建患者权限', NULL, '2026-05-03 06:07:04.519+00', NULL, NULL, NULL, false, NULL, NULL),
(933, 'Administration.Patients.Update', '更新患者', 'Administration.Patients', '更新患者权限', NULL, '2026-05-03 06:07:04.519+00', NULL, NULL, NULL, false, NULL, NULL),
(934, 'Administration.Patients.Delete', '删除患者', 'Administration.Patients', '删除患者权限', NULL, '2026-05-03 06:07:04.519+00', NULL, NULL, NULL, false, NULL, NULL),
(935, 'Administration.Positions', '岗位管理', NULL, '岗位管理（医生/护士/药师/库管）', NULL, '2026-05-03 06:07:04.519+00', NULL, NULL, NULL, false, NULL, NULL),
(936, 'Administration.Positions.View', '查看岗位', 'Administration.Positions', '查看岗位管理', NULL, '2026-05-03 06:07:04.519+00', NULL, NULL, NULL, false, NULL, NULL),
(937, 'Administration.Organizations', '科室管理', NULL, '科室管理模块（健康域，与 OU 同步）', NULL, '2026-05-03 06:07:04.519+00', NULL, NULL, NULL, false, NULL, NULL),
(938, 'Administration.Organizations.View', '查看科室', 'Administration.Organizations', '查看科室管理', NULL, '2026-05-03 06:07:04.519+00', NULL, NULL, NULL, false, NULL, NULL),
(939, 'Administration.HealthcareServices', '挂号类型管理', NULL, '挂号类型管理模块（HealthcareService）', NULL, '2026-05-03 06:07:04.519+00', NULL, NULL, NULL, false, NULL, NULL),
(940, 'Administration.HealthcareServices.View', '查看挂号类型', 'Administration.HealthcareServices', '查看挂号类型', NULL, '2026-05-03 06:07:04.519+00', NULL, NULL, NULL, false, NULL, NULL),
(941, 'ChargeManagement.OutpatientRegistration', '门诊挂号', NULL, '收费管理-门诊挂号', NULL, '2026-05-03 06:07:04.519+00', NULL, NULL, NULL, false, NULL, NULL),
(942, 'ChargeManagement.OutpatientRegistration.View', '查看门诊挂号', 'ChargeManagement.OutpatientRegistration', '查看门诊挂号权限', NULL, '2026-05-03 06:07:04.519+00', NULL, NULL, NULL, false, NULL, NULL),
(943, 'ChargeManagement.OutpatientDoctorStation', '门诊医生站', NULL, '收费管理-门诊医生站', NULL, '2026-05-03 06:07:04.519+00', NULL, NULL, NULL, false, NULL, NULL),
(944, 'ChargeManagement.OutpatientDoctorStation.View', '查看门诊医生站', 'ChargeManagement.OutpatientDoctorStation', '查看门诊医生站权限', NULL, '2026-05-03 06:07:04.519+00', NULL, NULL, NULL, false, NULL, NULL)
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
(1001, 'Identity.Users', 'R', 1, NULL, '2026-05-03 06:07:04.528+00', NULL, NULL, NULL, false, NULL, NULL),
(1002, 'Identity.Users.Create', 'R', 1, NULL, '2026-05-03 06:07:04.528+00', NULL, NULL, NULL, false, NULL, NULL),
(1003, 'Identity.Users.Update', 'R', 1, NULL, '2026-05-03 06:07:04.528+00', NULL, NULL, NULL, false, NULL, NULL),
(1004, 'Identity.Users.Delete', 'R', 1, NULL, '2026-05-03 06:07:04.528+00', NULL, NULL, NULL, false, NULL, NULL),
(1005, 'Identity.Users.View', 'R', 1, NULL, '2026-05-03 06:07:04.528+00', NULL, NULL, NULL, false, NULL, NULL),
(1006, 'Identity.Users.Impersonate', 'R', 1, NULL, '2026-05-03 06:07:04.528+00', NULL, NULL, NULL, false, NULL, NULL),
(1007, 'Identity.Roles', 'R', 1, NULL, '2026-05-03 06:07:04.528+00', NULL, NULL, NULL, false, NULL, NULL),
(1008, 'Identity.Roles.Create', 'R', 1, NULL, '2026-05-03 06:07:04.528+00', NULL, NULL, NULL, false, NULL, NULL),
(1009, 'Identity.Roles.Update', 'R', 1, NULL, '2026-05-03 06:07:04.528+00', NULL, NULL, NULL, false, NULL, NULL),
(1010, 'Identity.Roles.Delete', 'R', 1, NULL, '2026-05-03 06:07:04.528+00', NULL, NULL, NULL, false, NULL, NULL),
(1011, 'Identity.Roles.View', 'R', 1, NULL, '2026-05-03 06:07:04.528+00', NULL, NULL, NULL, false, NULL, NULL),
(1012, 'Identity.Permissions', 'R', 1, NULL, '2026-05-03 06:07:04.528+00', NULL, NULL, NULL, false, NULL, NULL),
(1013, 'Identity.Permissions.Grant', 'R', 1, NULL, '2026-05-03 06:07:04.528+00', NULL, NULL, NULL, false, NULL, NULL),
(1014, 'Identity.Permissions.Revoke', 'R', 1, NULL, '2026-05-03 06:07:04.528+00', NULL, NULL, NULL, false, NULL, NULL),
(1015, 'Identity.Permissions.View', 'R', 1, NULL, '2026-05-03 06:07:04.528+00', NULL, NULL, NULL, false, NULL, NULL),
(1016, 'Identity.OrganizationUnits', 'R', 1, NULL, '2026-05-03 06:07:04.528+00', NULL, NULL, NULL, false, NULL, NULL),
(1017, 'Identity.OrganizationUnits.Create', 'R', 1, NULL, '2026-05-03 06:07:04.528+00', NULL, NULL, NULL, false, NULL, NULL),
(1018, 'Identity.OrganizationUnits.Update', 'R', 1, NULL, '2026-05-03 06:07:04.528+00', NULL, NULL, NULL, false, NULL, NULL),
(1019, 'Identity.OrganizationUnits.Delete', 'R', 1, NULL, '2026-05-03 06:07:04.528+00', NULL, NULL, NULL, false, NULL, NULL),
(1020, 'Identity.OrganizationUnits.View', 'R', 1, NULL, '2026-05-03 06:07:04.528+00', NULL, NULL, NULL, false, NULL, NULL),
(2001, 'TenantManagement.Tenants', 'R', 1, NULL, '2026-05-03 06:07:04.528+00', NULL, NULL, NULL, false, NULL, NULL),
(2002, 'TenantManagement.Tenants.Create', 'R', 1, NULL, '2026-05-03 06:07:04.528+00', NULL, NULL, NULL, false, NULL, NULL),
(2003, 'TenantManagement.Tenants.Update', 'R', 1, NULL, '2026-05-03 06:07:04.528+00', NULL, NULL, NULL, false, NULL, NULL),
(2004, 'TenantManagement.Tenants.Delete', 'R', 1, NULL, '2026-05-03 06:07:04.528+00', NULL, NULL, NULL, false, NULL, NULL),
(2005, 'TenantManagement.Tenants.View', 'R', 1, NULL, '2026-05-03 06:07:04.528+00', NULL, NULL, NULL, false, NULL, NULL),
(3001, 'SettingManagement.Settings', 'R', 1, NULL, '2026-05-03 06:07:04.528+00', NULL, NULL, NULL, false, NULL, NULL),
(3002, 'SettingManagement.Settings.Create', 'R', 1, NULL, '2026-05-03 06:07:04.528+00', NULL, NULL, NULL, false, NULL, NULL),
(3003, 'SettingManagement.Settings.Update', 'R', 1, NULL, '2026-05-03 06:07:04.528+00', NULL, NULL, NULL, false, NULL, NULL),
(3004, 'SettingManagement.Settings.Delete', 'R', 1, NULL, '2026-05-03 06:07:04.528+00', NULL, NULL, NULL, false, NULL, NULL),
(3005, 'SettingManagement.Settings.View', 'R', 1, NULL, '2026-05-03 06:07:04.528+00', NULL, NULL, NULL, false, NULL, NULL),
(4001, 'Menu.Menus', 'R', 1, NULL, '2026-05-03 06:07:04.528+00', NULL, NULL, NULL, false, NULL, NULL),
(4002, 'Menu.Menus.Create', 'R', 1, NULL, '2026-05-03 06:07:04.528+00', NULL, NULL, NULL, false, NULL, NULL),
(4003, 'Menu.Menus.Update', 'R', 1, NULL, '2026-05-03 06:07:04.528+00', NULL, NULL, NULL, false, NULL, NULL),
(4004, 'Menu.Menus.Delete', 'R', 1, NULL, '2026-05-03 06:07:04.528+00', NULL, NULL, NULL, false, NULL, NULL),
(4005, 'Menu.Menus.View', 'R', 1, NULL, '2026-05-03 06:07:04.528+00', NULL, NULL, NULL, false, NULL, NULL),
(4501, 'Auditing.AuditLogs', 'R', 1, NULL, '2026-05-03 06:07:04.528+00', NULL, NULL, NULL, false, NULL, NULL),
(4502, 'Auditing.AuditLogs.View', 'R', 1, NULL, '2026-05-03 06:07:04.528+00', NULL, NULL, NULL, false, NULL, NULL),
(5001, 'Terminology.CodeSystems', 'R', 1, NULL, '2026-05-03 06:07:04.528+00', NULL, NULL, NULL, false, NULL, NULL),
(5002, 'Terminology.CodeSystems.Create', 'R', 1, NULL, '2026-05-03 06:07:04.528+00', NULL, NULL, NULL, false, NULL, NULL),
(5003, 'Terminology.CodeSystems.Update', 'R', 1, NULL, '2026-05-03 06:07:04.528+00', NULL, NULL, NULL, false, NULL, NULL),
(5004, 'Terminology.CodeSystems.Delete', 'R', 1, NULL, '2026-05-03 06:07:04.528+00', NULL, NULL, NULL, false, NULL, NULL),
(5005, 'Terminology.CodeSystems.View', 'R', 1, NULL, '2026-05-03 06:07:04.528+00', NULL, NULL, NULL, false, NULL, NULL),
(6001, 'Administration.BodyStructures', 'R', 1, NULL, '2026-05-03 06:07:04.528+00', NULL, NULL, NULL, false, NULL, NULL),
(6002, 'Administration.BodyStructures.Create', 'R', 1, NULL, '2026-05-03 06:07:04.528+00', NULL, NULL, NULL, false, NULL, NULL),
(6003, 'Administration.BodyStructures.Update', 'R', 1, NULL, '2026-05-03 06:07:04.528+00', NULL, NULL, NULL, false, NULL, NULL),
(6004, 'Administration.BodyStructures.Delete', 'R', 1, NULL, '2026-05-03 06:07:04.528+00', NULL, NULL, NULL, false, NULL, NULL),
(6005, 'Administration.BodyStructures.View', 'R', 1, NULL, '2026-05-03 06:07:04.528+00', NULL, NULL, NULL, false, NULL, NULL),
(6101, 'Administration.Locations', 'R', 1, NULL, '2026-05-03 06:07:04.528+00', NULL, NULL, NULL, false, NULL, NULL),
(6102, 'Administration.Locations.Create', 'R', 1, NULL, '2026-05-03 06:07:04.528+00', NULL, NULL, NULL, false, NULL, NULL),
(6103, 'Administration.Locations.Update', 'R', 1, NULL, '2026-05-03 06:07:04.528+00', NULL, NULL, NULL, false, NULL, NULL),
(6104, 'Administration.Locations.Delete', 'R', 1, NULL, '2026-05-03 06:07:04.528+00', NULL, NULL, NULL, false, NULL, NULL),
(6105, 'Administration.Locations.View', 'R', 1, NULL, '2026-05-03 06:07:04.528+00', NULL, NULL, NULL, false, NULL, NULL),
(6201, 'Administration.WardLocations', 'R', 1, NULL, '2026-05-03 06:07:04.528+00', NULL, NULL, NULL, false, NULL, NULL),
(6202, 'Administration.WardLocations.View', 'R', 1, NULL, '2026-05-03 06:07:04.528+00', NULL, NULL, NULL, false, NULL, NULL),
(6301, 'Administration.Patients', 'R', 1, NULL, '2026-05-03 06:07:04.528+00', NULL, NULL, NULL, false, NULL, NULL),
(6302, 'Administration.Patients.View', 'R', 1, NULL, '2026-05-03 06:07:04.528+00', NULL, NULL, NULL, false, NULL, NULL),
(6303, 'Administration.Patients.Create', 'R', 1, NULL, '2026-05-03 06:07:04.528+00', NULL, NULL, NULL, false, NULL, NULL),
(6304, 'Administration.Patients.Update', 'R', 1, NULL, '2026-05-03 06:07:04.528+00', NULL, NULL, NULL, false, NULL, NULL),
(6305, 'Administration.Patients.Delete', 'R', 1, NULL, '2026-05-03 06:07:04.528+00', NULL, NULL, NULL, false, NULL, NULL),
(6351, 'Administration.Positions', 'R', 1, NULL, '2026-05-03 06:07:04.528+00', NULL, NULL, NULL, false, NULL, NULL),
(6352, 'Administration.Positions.View', 'R', 1, NULL, '2026-05-03 06:07:04.528+00', NULL, NULL, NULL, false, NULL, NULL),
(6361, 'Administration.Organizations', 'R', 1, NULL, '2026-05-03 06:07:04.528+00', NULL, NULL, NULL, false, NULL, NULL),
(6362, 'Administration.Organizations.View', 'R', 1, NULL, '2026-05-03 06:07:04.528+00', NULL, NULL, NULL, false, NULL, NULL),
(6371, 'Administration.HealthcareServices', 'R', 1, NULL, '2026-05-03 06:07:04.528+00', NULL, NULL, NULL, false, NULL, NULL),
(6372, 'Administration.HealthcareServices.View', 'R', 1, NULL, '2026-05-03 06:07:04.528+00', NULL, NULL, NULL, false, NULL, NULL),
(6381, 'ChargeManagement.OutpatientRegistration', 'R', 1, NULL, '2026-05-03 06:07:04.528+00', NULL, NULL, NULL, false, NULL, NULL),
(6382, 'ChargeManagement.OutpatientRegistration.View', 'R', 1, NULL, '2026-05-03 06:07:04.528+00', NULL, NULL, NULL, false, NULL, NULL),
(6391, 'ChargeManagement.OutpatientDoctorStation', 'R', 1, NULL, '2026-05-03 06:07:04.528+00', NULL, NULL, NULL, false, NULL, NULL),
(6392, 'ChargeManagement.OutpatientDoctorStation.View', 'R', 1, NULL, '2026-05-03 06:07:04.528+00', NULL, NULL, NULL, false, NULL, NULL)
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

-- seed: whale_security_logs
INSERT INTO "whale_security_logs" ("id", "user_id", "user_name", "action", "client_ip", "user_agent", "result", "failure_reason", "tenant_id", "extra_properties", "created_at", "updated_at", "created_by", "updated_by", "is_deleted", "deleted_at", "deleted_by")
VALUES
(309211172049522688, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-03 06:14:48.282+00', '2026-05-03 06:14:48.282+00', NULL, NULL, false, NULL, NULL),
(309211354795347968, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-03 06:15:31.852+00', '2026-05-03 06:15:31.852+00', NULL, NULL, false, NULL, NULL),
(309211458268827648, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-03 06:15:56.522+00', '2026-05-03 06:15:56.522+00', NULL, NULL, false, NULL, NULL),
(309211481907924992, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-03 06:16:02.158+00', '2026-05-03 06:16:02.158+00', NULL, NULL, false, NULL, NULL),
(309211487024975872, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-03 06:16:03.378+00', '2026-05-03 06:16:03.378+00', NULL, NULL, false, NULL, NULL),
(309211524463333376, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-03 06:16:12.304+00', '2026-05-03 06:16:12.304+00', NULL, NULL, false, NULL, NULL),
(309211942660608000, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-03 06:17:52.010+00', '2026-05-03 06:17:52.010+00', NULL, NULL, false, NULL, NULL),
(309211946733277184, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-03 06:17:52.981+00', '2026-05-03 06:17:52.981+00', NULL, NULL, false, NULL, NULL),
(309215076577447936, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-03 06:30:19.194+00', '2026-05-03 06:30:19.194+00', NULL, NULL, false, NULL, NULL),
(309215082206203904, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-03 06:30:20.536+00', '2026-05-03 06:30:20.536+00', NULL, NULL, false, NULL, NULL),
(309234544707899392, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-03 07:47:40.758+00', '2026-05-03 07:47:40.758+00', NULL, NULL, false, NULL, NULL),
(309234563364163584, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-03 07:47:45.206+00', '2026-05-03 07:47:45.206+00', NULL, NULL, false, NULL, NULL),
(309234641160114176, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-03 07:48:03.754+00', '2026-05-03 07:48:03.754+00', NULL, NULL, false, NULL, NULL),
(309234712287121408, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-03 07:48:20.712+00', '2026-05-03 07:48:20.712+00', NULL, NULL, false, NULL, NULL),
(309265941803831296, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-03 09:52:26.409+00', '2026-05-03 09:52:26.409+00', NULL, NULL, false, NULL, NULL),
(309265948179173376, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-03 09:52:27.929+00', '2026-05-03 09:52:27.929+00', NULL, NULL, false, NULL, NULL),
(309299045954228224, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-03 12:03:59.053+00', '2026-05-03 12:03:59.053+00', NULL, NULL, false, NULL, NULL),
(309302975731863552, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-03 12:19:35.985+00', '2026-05-03 12:19:35.985+00', NULL, NULL, false, NULL, NULL),
(309306336633425920, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-03 12:32:57.287+00', '2026-05-03 12:32:57.287+00', NULL, NULL, false, NULL, NULL),
(309307913951776768, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-03 12:39:13.349+00', '2026-05-03 12:39:13.349+00', NULL, NULL, false, NULL, NULL),
(309307930934513664, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-03 12:39:17.398+00', '2026-05-03 12:39:17.398+00', NULL, NULL, false, NULL, NULL),
(309309948516372480, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-03 12:47:18.427+00', '2026-05-03 12:47:18.427+00', NULL, NULL, false, NULL, NULL),
(309309953352404992, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-03 12:47:19.580+00', '2026-05-03 12:47:19.580+00', NULL, NULL, false, NULL, NULL),
(309316142606323712, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-03 13:11:55.214+00', '2026-05-03 13:11:55.214+00', NULL, NULL, false, NULL, NULL),
(309316147916312576, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-03 13:11:56.479+00', '2026-05-03 13:11:56.479+00', NULL, NULL, false, NULL, NULL),
(309524091320668160, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-04 02:58:14.050+00', '2026-05-04 02:58:14.050+00', NULL, NULL, false, NULL, NULL),
(309524102565597184, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-04 02:58:16.731+00', '2026-05-04 02:58:16.731+00', NULL, NULL, false, NULL, NULL),
(309540562640834560, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-04 04:03:41.119+00', '2026-05-04 04:03:41.119+00', NULL, NULL, false, NULL, NULL),
(309540566902247424, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-04 04:03:42.135+00', '2026-05-04 04:03:42.135+00', NULL, NULL, false, NULL, NULL),
(309570906286985216, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-04 06:04:15.608+00', '2026-05-04 06:04:15.608+00', NULL, NULL, false, NULL, NULL),
(309570915271184384, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-04 06:04:17.750+00', '2026-05-04 06:04:17.750+00', NULL, NULL, false, NULL, NULL),
(309603170209763328, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-04 08:12:27.926+00', '2026-05-04 08:12:27.926+00', NULL, NULL, false, NULL, NULL),
(309603210680602624, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-04 08:12:37.575+00', '2026-05-04 08:12:37.575+00', NULL, NULL, false, NULL, NULL),
(309623185103917056, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-04 09:31:59.849+00', '2026-05-04 09:31:59.849+00', NULL, NULL, false, NULL, NULL),
(309623197682634752, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-04 09:32:02.848+00', '2026-05-04 09:32:02.848+00', NULL, NULL, false, NULL, NULL),
(309654034989977600, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-04 11:34:35.035+00', '2026-05-04 11:34:35.035+00', NULL, NULL, false, NULL, NULL),
(309654146092896256, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-04 11:35:01.524+00', '2026-05-04 11:35:01.524+00', NULL, NULL, false, NULL, NULL),
(309654146092896257, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-04 11:35:01.524+00', '2026-05-04 11:35:01.524+00', NULL, NULL, false, NULL, NULL),
(309654402666860544, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-04 11:36:02.696+00', '2026-05-04 11:36:02.696+00', NULL, NULL, false, NULL, NULL),
(309654402666860545, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-04 11:36:02.696+00', '2026-05-04 11:36:02.696+00', NULL, NULL, false, NULL, NULL),
(309654427337756672, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-04 11:36:08.578+00', '2026-05-04 11:36:08.578+00', NULL, NULL, false, NULL, NULL),
(309654427341950976, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-04 11:36:08.579+00', '2026-05-04 11:36:08.579+00', NULL, NULL, false, NULL, NULL),
(309654527686479872, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-04 11:36:32.503+00', '2026-05-04 11:36:32.503+00', NULL, NULL, false, NULL, NULL),
(309654527686479873, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-04 11:36:32.503+00', '2026-05-04 11:36:32.503+00', NULL, NULL, false, NULL, NULL),
(309654533642391552, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-04 11:36:33.923+00', '2026-05-04 11:36:33.923+00', NULL, NULL, false, NULL, NULL),
(309654533642391553, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-04 11:36:33.923+00', '2026-05-04 11:36:33.923+00', NULL, NULL, false, NULL, NULL),
(309654533835329536, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-04 11:36:33.969+00', '2026-05-04 11:36:33.969+00', NULL, NULL, false, NULL, NULL),
(309654533835329537, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-04 11:36:33.969+00', '2026-05-04 11:36:33.969+00', NULL, NULL, false, NULL, NULL),
(309654687674011648, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-04 11:37:10.647+00', '2026-05-04 11:37:10.647+00', NULL, NULL, false, NULL, NULL),
(309654687678205952, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-04 11:37:10.648+00', '2026-05-04 11:37:10.648+00', NULL, NULL, false, NULL, NULL),
(309655179963666432, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-04 11:39:08.018+00', '2026-05-04 11:39:08.018+00', NULL, NULL, false, NULL, NULL),
(309891216954626048, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-05 03:17:03.622+00', '2026-05-05 03:17:03.622+00', NULL, NULL, false, NULL, NULL),
(309891236923707392, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-05 03:17:08.383+00', '2026-05-05 03:17:08.383+00', NULL, NULL, false, NULL, NULL),
(309892034571276288, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-05 03:20:18.557+00', '2026-05-05 03:20:18.557+00', NULL, NULL, false, NULL, NULL),
(309892040241975296, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-05 03:20:19.909+00', '2026-05-05 03:20:19.909+00', NULL, NULL, false, NULL, NULL),
(309926580838207488, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-05 05:37:35.029+00', '2026-05-05 05:37:35.029+00', NULL, NULL, false, NULL, NULL),
(309926580842401792, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-05 05:37:35.030+00', '2026-05-05 05:37:35.030+00', NULL, NULL, false, NULL, NULL),
(309926586253053952, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-05 05:37:36.320+00', '2026-05-05 05:37:36.320+00', NULL, NULL, false, NULL, NULL),
(309926586257248256, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-05 05:37:36.321+00', '2026-05-05 05:37:36.321+00', NULL, NULL, false, NULL, NULL),
(309926591923752960, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-05 05:37:37.672+00', '2026-05-05 05:37:37.672+00', NULL, NULL, false, NULL, NULL),
(309926591923752961, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-05 05:37:37.672+00', '2026-05-05 05:37:37.672+00', NULL, NULL, false, NULL, NULL),
(309926598450089984, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-05 05:37:39.228+00', '2026-05-05 05:37:39.228+00', NULL, NULL, false, NULL, NULL),
(309926598450089985, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-05 05:37:39.228+00', '2026-05-05 05:37:39.228+00', NULL, NULL, false, NULL, NULL),
(309926608625471488, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-05 05:37:41.654+00', '2026-05-05 05:37:41.654+00', NULL, NULL, false, NULL, NULL),
(309926608625471489, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-05 05:37:41.654+00', '2026-05-05 05:37:41.654+00', NULL, NULL, false, NULL, NULL),
(309926617722916864, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-05 05:37:43.823+00', '2026-05-05 05:37:43.823+00', NULL, NULL, false, NULL, NULL),
(309926617722916865, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-05 05:37:43.824+00', '2026-05-05 05:37:43.824+00', NULL, NULL, false, NULL, NULL),
(309926621736865792, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-05 05:37:44.780+00', '2026-05-05 05:37:44.780+00', NULL, NULL, false, NULL, NULL),
(309926621736865793, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-05 05:37:44.780+00', '2026-05-05 05:37:44.780+00', NULL, NULL, false, NULL, NULL),
(309926652753743872, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-05 05:37:52.175+00', '2026-05-05 05:37:52.175+00', NULL, NULL, false, NULL, NULL),
(309926652753743873, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-05 05:37:52.175+00', '2026-05-05 05:37:52.175+00', NULL, NULL, false, NULL, NULL),
(309927121379659776, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-05 05:39:43.904+00', '2026-05-05 05:39:43.904+00', NULL, NULL, false, NULL, NULL),
(309927142577672192, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-05 05:39:48.958+00', '2026-05-05 05:39:48.958+00', NULL, NULL, false, NULL, NULL),
(309957348942680064, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-05 07:39:50.717+00', '2026-05-05 07:39:50.717+00', NULL, NULL, false, NULL, NULL),
(309957348942680065, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-05 07:39:50.717+00', '2026-05-05 07:39:50.717+00', NULL, NULL, false, NULL, NULL),
(309957348976234496, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-05 07:39:50.725+00', '2026-05-05 07:39:50.725+00', NULL, NULL, false, NULL, NULL),
(309957374448242688, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-05 07:39:56.798+00', '2026-05-05 07:39:56.798+00', NULL, NULL, false, NULL, NULL),
(309957390319489024, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-05 07:40:00.582+00', '2026-05-05 07:40:00.582+00', NULL, NULL, false, NULL, NULL),
(309981299089084416, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-05 09:15:00.876+00', '2026-05-05 09:15:00.876+00', NULL, NULL, false, NULL, NULL),
(309981304260661248, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-05 09:15:02.109+00', '2026-05-05 09:15:02.109+00', NULL, NULL, false, NULL, NULL),
(310027801519984640, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36 Edg/147.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-05 12:19:47.920+00', '2026-05-05 12:19:47.920+00', NULL, NULL, false, NULL, NULL),
(311000462178914304, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-08 04:44:48.292+00', '2026-05-08 04:44:48.292+00', NULL, NULL, false, NULL, NULL),
(311000618567733248, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-08 04:45:25.578+00', '2026-05-08 04:45:25.578+00', NULL, NULL, false, NULL, NULL),
(311002720526405632, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36 Edg/147.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-08 04:53:46.724+00', '2026-05-08 04:53:46.724+00', NULL, NULL, false, NULL, NULL),
(311045190677172224, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-08 07:42:32.397+00', '2026-05-08 07:42:32.397+00', NULL, NULL, false, NULL, NULL),
(311045197266423808, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-08 07:42:33.968+00', '2026-05-08 07:42:33.968+00', NULL, NULL, false, NULL, NULL),
(311089610241871872, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-08 10:39:02.848+00', '2026-05-08 10:39:02.848+00', NULL, NULL, false, NULL, NULL),
(311090031953973248, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-08 10:40:43.391+00', '2026-05-08 10:40:43.391+00', NULL, NULL, false, NULL, NULL),
(311107837417754624, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-08 11:51:28.544+00', '2026-05-08 11:51:28.544+00', NULL, NULL, false, NULL, NULL),
(311107853565825024, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-08 11:51:32.394+00', '2026-05-08 11:51:32.394+00', NULL, NULL, false, NULL, NULL),
(311112718796984320, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-08 12:10:52.356+00', '2026-05-08 12:10:52.356+00', NULL, NULL, false, NULL, NULL),
(311112723695931392, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-08 12:10:53.524+00', '2026-05-08 12:10:53.524+00', NULL, NULL, false, NULL, NULL),
(311327695821082624, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36 Edg/147.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-09 02:25:06.872+00', '2026-05-09 02:25:06.872+00', NULL, NULL, false, NULL, NULL),
(311327745787826176, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36 Edg/147.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-09 02:25:18.785+00', '2026-05-09 02:25:18.785+00', NULL, NULL, false, NULL, NULL),
(311328155181256704, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36 Edg/147.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-09 02:26:56.392+00', '2026-05-09 02:26:56.392+00', NULL, NULL, false, NULL, NULL),
(311343446271463424, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36 Edg/147.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-09 03:27:42.072+00', '2026-05-09 03:27:42.072+00', NULL, NULL, false, NULL, NULL),
(311343459395440640, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36 Edg/147.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-09 03:27:45.201+00', '2026-05-09 03:27:45.201+00', NULL, NULL, false, NULL, NULL),
(311343791986970624, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-09 03:29:04.497+00', '2026-05-09 03:29:04.497+00', NULL, NULL, false, NULL, NULL),
(311345782624620544, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36 Edg/147.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-09 03:36:59.102+00', '2026-05-09 03:36:59.102+00', NULL, NULL, false, NULL, NULL),
(311345788618280960, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36 Edg/147.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-09 03:37:00.531+00', '2026-05-09 03:37:00.531+00', NULL, NULL, false, NULL, NULL)
ON CONFLICT ("id") DO NOTHING;

INSERT INTO "whale_security_logs" ("id", "user_id", "user_name", "action", "client_ip", "user_agent", "result", "failure_reason", "tenant_id", "extra_properties", "created_at", "updated_at", "created_by", "updated_by", "is_deleted", "deleted_at", "deleted_by")
VALUES
(311345871812300800, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36 Edg/147.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-09 03:37:20.366+00', '2026-05-09 03:37:20.366+00', NULL, NULL, false, NULL, NULL),
(311345876954517504, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36 Edg/147.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-09 03:37:21.592+00', '2026-05-09 03:37:21.592+00', NULL, NULL, false, NULL, NULL),
(311345970525245440, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36 Edg/147.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-09 03:37:43.902+00', '2026-05-09 03:37:43.902+00', NULL, NULL, false, NULL, NULL),
(311345975449358336, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36 Edg/147.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-09 03:37:45.075+00', '2026-05-09 03:37:45.075+00', NULL, NULL, false, NULL, NULL),
(311346261379256320, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36 Edg/147.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-09 03:38:53.246+00', '2026-05-09 03:38:53.246+00', NULL, NULL, false, NULL, NULL),
(311346265565171712, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36 Edg/147.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-09 03:38:54.244+00', '2026-05-09 03:38:54.244+00', NULL, NULL, false, NULL, NULL),
(311346428778123264, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36 QQBrowser/21.0.8377.400', 'SUCCESS', NULL, NULL, NULL, '2026-05-09 03:39:33.157+00', '2026-05-09 03:39:33.157+00', NULL, NULL, false, NULL, NULL),
(311346435518369792, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36 QQBrowser/21.0.8377.400', 'SUCCESS', NULL, NULL, NULL, '2026-05-09 03:39:34.764+00', '2026-05-09 03:39:34.764+00', NULL, NULL, false, NULL, NULL),
(311346658294632448, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36 Edg/147.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-09 03:40:27.878+00', '2026-05-09 03:40:27.878+00', NULL, NULL, false, NULL, NULL),
(311347516080132096, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36 Edg/147.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-09 03:43:52.390+00', '2026-05-09 03:43:52.390+00', NULL, NULL, false, NULL, NULL),
(311347520731615232, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36 Edg/147.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-09 03:43:53.499+00', '2026-05-09 03:43:53.499+00', NULL, NULL, false, NULL, NULL),
(311348699314262016, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-09 03:48:34.495+00', '2026-05-09 03:48:34.495+00', NULL, NULL, false, NULL, NULL),
(311348709808410624, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-09 03:48:36.997+00', '2026-05-09 03:48:36.997+00', NULL, NULL, false, NULL, NULL),
(311348770206388224, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-09 03:48:51.397+00', '2026-05-09 03:48:51.397+00', NULL, NULL, false, NULL, NULL),
(311358594960855040, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36 Edg/147.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-09 04:27:53.801+00', '2026-05-09 04:27:53.801+00', NULL, NULL, false, NULL, NULL),
(311358598052057088, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36 Edg/147.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-09 04:27:54.538+00', '2026-05-09 04:27:54.538+00', NULL, NULL, false, NULL, NULL),
(311382411347234816, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-09 06:02:32.070+00', '2026-05-09 06:02:32.070+00', NULL, NULL, false, NULL, NULL),
(311382411380789248, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-09 06:02:32.079+00', '2026-05-09 06:02:32.079+00', NULL, NULL, false, NULL, NULL),
(311382418892787712, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-09 06:02:33.869+00', '2026-05-09 06:02:33.869+00', NULL, NULL, false, NULL, NULL),
(311415320460398592, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-09 08:13:18.214+00', '2026-05-09 08:13:18.214+00', NULL, NULL, false, NULL, NULL),
(311415320485564416, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-09 08:13:18.220+00', '2026-05-09 08:13:18.220+00', NULL, NULL, false, NULL, NULL),
(311415320493953024, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-09 08:13:18.222+00', '2026-05-09 08:13:18.222+00', NULL, NULL, false, NULL, NULL),
(311419021702991872, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-09 08:28:00.659+00', '2026-05-09 08:28:00.659+00', NULL, NULL, false, NULL, NULL),
(311453203704057856, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-09 10:43:50.283+00', '2026-05-09 10:43:50.283+00', NULL, NULL, false, NULL, NULL),
(311453219168456704, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-09 10:43:53.970+00', '2026-05-09 10:43:53.970+00', NULL, NULL, false, NULL, NULL),
(311453239452110848, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-09 10:43:58.806+00', '2026-05-09 10:43:58.806+00', NULL, NULL, false, NULL, NULL),
(311477446749720576, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-09 12:20:10.276+00', '2026-05-09 12:20:10.276+00', NULL, NULL, false, NULL, NULL),
(311477472632770560, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-09 12:20:16.447+00', '2026-05-09 12:20:16.447+00', NULL, NULL, false, NULL, NULL),
(311694437910581248, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-10 02:42:25.000+00', '2026-05-10 02:42:25.000+00', NULL, NULL, false, NULL, NULL),
(311694438409703424, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-10 02:42:25.119+00', '2026-05-10 02:42:25.119+00', NULL, NULL, false, NULL, NULL),
(311694444541775872, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-10 02:42:26.581+00', '2026-05-10 02:42:26.581+00', NULL, NULL, false, NULL, NULL),
(311695468664983552, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36 Edg/147.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-10 02:46:30.751+00', '2026-05-10 02:46:30.751+00', NULL, NULL, false, NULL, NULL),
(311695719840878592, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36 Edg/147.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-10 02:47:30.636+00', '2026-05-10 02:47:30.636+00', NULL, NULL, false, NULL, NULL),
(311695734994898944, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36 Edg/147.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-10 02:47:34.249+00', '2026-05-10 02:47:34.249+00', NULL, NULL, false, NULL, NULL),
(311696207042842624, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36 Edg/147.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-10 02:49:26.794+00', '2026-05-10 02:49:26.794+00', NULL, NULL, false, NULL, NULL),
(311696626636820480, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36 Edg/147.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-10 02:51:06.833+00', '2026-05-10 02:51:06.833+00', NULL, NULL, false, NULL, NULL),
(311697359780188160, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-10 02:54:01.628+00', '2026-05-10 02:54:01.628+00', NULL, NULL, false, NULL, NULL),
(311697368516923392, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-10 02:54:03.711+00', '2026-05-10 02:54:03.711+00', NULL, NULL, false, NULL, NULL),
(311700366974521344, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36 Edg/147.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-10 03:05:58.599+00', '2026-05-10 03:05:58.599+00', NULL, NULL, false, NULL, NULL),
(311708142547243008, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36 QQBrowser/21.0.8377.400', 'SUCCESS', NULL, NULL, NULL, '2026-05-10 03:36:52.440+00', '2026-05-10 03:36:52.440+00', NULL, NULL, false, NULL, NULL),
(311708147337138176, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36 QQBrowser/21.0.8377.400', 'SUCCESS', NULL, NULL, NULL, '2026-05-10 03:36:53.582+00', '2026-05-10 03:36:53.582+00', NULL, NULL, false, NULL, NULL),
(311708184188293120, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36 QQBrowser/21.0.8377.400', 'SUCCESS', NULL, NULL, NULL, '2026-05-10 03:37:02.368+00', '2026-05-10 03:37:02.368+00', NULL, NULL, false, NULL, NULL),
(311708188084801536, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36 QQBrowser/21.0.8377.400', 'SUCCESS', NULL, NULL, NULL, '2026-05-10 03:37:03.297+00', '2026-05-10 03:37:03.297+00', NULL, NULL, false, NULL, NULL),
(311708246469513216, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36 QQBrowser/21.0.8377.400', 'SUCCESS', NULL, NULL, NULL, '2026-05-10 03:37:17.217+00', '2026-05-10 03:37:17.217+00', NULL, NULL, false, NULL, NULL),
(311708251108413440, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36 QQBrowser/21.0.8377.400', 'SUCCESS', NULL, NULL, NULL, '2026-05-10 03:37:18.323+00', '2026-05-10 03:37:18.323+00', NULL, NULL, false, NULL, NULL),
(311708355907293184, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36 QQBrowser/21.0.8377.400', 'SUCCESS', NULL, NULL, NULL, '2026-05-10 03:37:43.309+00', '2026-05-10 03:37:43.309+00', NULL, NULL, false, NULL, NULL),
(311708530239344640, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36 QQBrowser/21.0.8377.400', 'SUCCESS', NULL, NULL, NULL, '2026-05-10 03:38:24.873+00', '2026-05-10 03:38:24.873+00', NULL, NULL, false, NULL, NULL),
(311708534030995456, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36 QQBrowser/21.0.8377.400', 'SUCCESS', NULL, NULL, NULL, '2026-05-10 03:38:25.777+00', '2026-05-10 03:38:25.777+00', NULL, NULL, false, NULL, NULL),
(311708557229690880, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36 QQBrowser/21.0.8377.400', 'SUCCESS', NULL, NULL, NULL, '2026-05-10 03:38:31.308+00', '2026-05-10 03:38:31.308+00', NULL, NULL, false, NULL, NULL),
(311708560991981568, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36 QQBrowser/21.0.8377.400', 'SUCCESS', NULL, NULL, NULL, '2026-05-10 03:38:32.205+00', '2026-05-10 03:38:32.205+00', NULL, NULL, false, NULL, NULL),
(311708793427726336, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-10 03:39:27.623+00', '2026-05-10 03:39:27.623+00', NULL, NULL, false, NULL, NULL),
(311724845998018560, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-10 04:43:14.854+00', '2026-05-10 04:43:14.854+00', NULL, NULL, false, NULL, NULL),
(311724846501335040, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-10 04:43:14.973+00', '2026-05-10 04:43:14.973+00', NULL, NULL, false, NULL, NULL),
(311724855602974720, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-10 04:43:17.143+00', '2026-05-10 04:43:17.143+00', NULL, NULL, false, NULL, NULL),
(311725031147180032, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-10 04:43:58.997+00', '2026-05-10 04:43:58.997+00', NULL, NULL, false, NULL, NULL),
(311733363857690624, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36 Edg/147.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-10 05:17:05.669+00', '2026-05-10 05:17:05.669+00', NULL, NULL, false, NULL, NULL),
(311741163648323584, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-10 05:48:05.284+00', '2026-05-10 05:48:05.284+00', NULL, NULL, false, NULL, NULL),
(311741167830044672, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-10 05:48:06.281+00', '2026-05-10 05:48:06.281+00', NULL, NULL, false, NULL, NULL),
(311749174296580096, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-10 06:19:55.171+00', '2026-05-10 06:19:55.171+00', NULL, NULL, false, NULL, NULL),
(311749179816284160, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-10 06:19:56.487+00', '2026-05-10 06:19:56.487+00', NULL, NULL, false, NULL, NULL),
(311779481516773376, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-10 08:20:20.976+00', '2026-05-10 08:20:20.976+00', NULL, NULL, false, NULL, NULL),
(311779481520967680, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-10 08:20:20.977+00', '2026-05-10 08:20:20.977+00', NULL, NULL, false, NULL, NULL),
(311779504409284608, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-10 08:20:26.434+00', '2026-05-10 08:20:26.434+00', NULL, NULL, false, NULL, NULL),
(311779529147289600, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-10 08:20:32.332+00', '2026-05-10 08:20:32.332+00', NULL, NULL, false, NULL, NULL),
(311809750021967872, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-10 10:20:37.550+00', '2026-05-10 10:20:37.550+00', NULL, NULL, false, NULL, NULL),
(311809750059716608, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-10 10:20:37.559+00', '2026-05-10 10:20:37.559+00', NULL, NULL, false, NULL, NULL),
(311809798000611328, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-10 10:20:48.989+00', '2026-05-10 10:20:48.989+00', NULL, NULL, false, NULL, NULL),
(311811779057422336, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-10 10:28:41.310+00', '2026-05-10 10:28:41.310+00', NULL, NULL, false, NULL, NULL),
(311811789631262720, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-10 10:28:43.831+00', '2026-05-10 10:28:43.831+00', NULL, NULL, false, NULL, NULL),
(311838610150133760, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-10 12:15:18.341+00', '2026-05-10 12:15:18.341+00', NULL, NULL, false, NULL, NULL),
(312091378211360768, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-11 04:59:42.943+00', '2026-05-11 04:59:42.943+00', NULL, NULL, false, NULL, NULL),
(312091386721603584, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-11 04:59:44.972+00', '2026-05-11 04:59:44.972+00', NULL, NULL, false, NULL, NULL),
(312121991639339008, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-11 07:01:21.753+00', '2026-05-11 07:01:21.753+00', NULL, NULL, false, NULL, NULL),
(312121998891290624, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-11 07:01:23.482+00', '2026-05-11 07:01:23.482+00', NULL, NULL, false, NULL, NULL),
(312153720932536320, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-11 09:07:26.607+00', '2026-05-11 09:07:26.607+00', NULL, NULL, false, NULL, NULL),
(312153734354309120, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-11 09:07:29.806+00', '2026-05-11 09:07:29.806+00', NULL, NULL, false, NULL, NULL),
(312818679061417984, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-13 05:09:44.978+00', '2026-05-13 05:09:44.978+00', NULL, NULL, false, NULL, NULL),
(312859041587007488, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-13 07:50:08.155+00', '2026-05-13 07:50:08.155+00', NULL, NULL, false, NULL, NULL),
(312859049728151552, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-13 07:50:10.095+00', '2026-05-13 07:50:10.095+00', NULL, NULL, false, NULL, NULL),
(312906038033518592, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-13 10:56:52.980+00', '2026-05-13 10:56:52.980+00', NULL, NULL, false, NULL, NULL),
(312906046686367744, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-13 10:56:55.043+00', '2026-05-13 10:56:55.043+00', NULL, NULL, false, NULL, NULL),
(312920599326887936, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-13 11:54:44.663+00', '2026-05-13 11:54:44.663+00', NULL, NULL, false, NULL, NULL),
(312920605609955328, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-13 11:54:46.161+00', '2026-05-13 11:54:46.161+00', NULL, NULL, false, NULL, NULL),
(312923905776947200, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-13 12:07:52.982+00', '2026-05-13 12:07:52.982+00', NULL, NULL, false, NULL, NULL),
(312923910654922752, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-13 12:07:54.145+00', '2026-05-13 12:07:54.145+00', NULL, NULL, false, NULL, NULL),
(312927373626445824, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-13 12:21:39.782+00', '2026-05-13 12:21:39.782+00', NULL, NULL, false, NULL, NULL),
(312927378517004288, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-13 12:21:40.948+00', '2026-05-13 12:21:40.948+00', NULL, NULL, false, NULL, NULL),
(312936432182235136, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-13 12:57:39.510+00', '2026-05-13 12:57:39.510+00', NULL, NULL, false, NULL, NULL),
(312936436477202432, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-13 12:57:40.534+00', '2026-05-13 12:57:40.534+00', NULL, NULL, false, NULL, NULL),
(313155005283569664, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-14 03:26:11.398+00', '2026-05-14 03:26:11.398+00', NULL, NULL, false, NULL, NULL),
(313155311232880640, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-14 03:27:24.342+00', '2026-05-14 03:27:24.342+00', NULL, NULL, false, NULL, NULL),
(313185530178179072, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-14 05:27:29.100+00', '2026-05-14 05:27:29.100+00', NULL, NULL, false, NULL, NULL),
(313185536184422400, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-14 05:27:30.532+00', '2026-05-14 05:27:30.532+00', NULL, NULL, false, NULL, NULL),
(313205796138651648, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-14 06:48:00.881+00', '2026-05-14 06:48:00.881+00', NULL, NULL, false, NULL, NULL),
(313205801226342400, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-14 06:48:02.094+00', '2026-05-14 06:48:02.094+00', NULL, NULL, false, NULL, NULL),
(313205823284187136, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-14 06:48:07.353+00', '2026-05-14 06:48:07.353+00', NULL, NULL, false, NULL, NULL),
(313205828371877888, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-14 06:48:08.566+00', '2026-05-14 06:48:08.566+00', NULL, NULL, false, NULL, NULL),
(313238471780077568, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-14 08:57:51.362+00', '2026-05-14 08:57:51.362+00', NULL, NULL, false, NULL, NULL),
(313238479061389312, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-14 08:57:53.097+00', '2026-05-14 08:57:53.097+00', NULL, NULL, false, NULL, NULL),
(313269427102027776, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-14 11:00:51.685+00', '2026-05-14 11:00:51.685+00', NULL, NULL, false, NULL, NULL)
ON CONFLICT ("id") DO NOTHING;

INSERT INTO "whale_security_logs" ("id", "user_id", "user_name", "action", "client_ip", "user_agent", "result", "failure_reason", "tenant_id", "extra_properties", "created_at", "updated_at", "created_by", "updated_by", "is_deleted", "deleted_at", "deleted_by")
VALUES
(313269450330083328, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-14 11:00:57.223+00', '2026-05-14 11:00:57.223+00', NULL, NULL, false, NULL, NULL),
(313275166103703552, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-14 11:23:39.970+00', '2026-05-14 11:23:39.970+00', NULL, NULL, false, NULL, NULL),
(313275170671300608, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-14 11:23:41.059+00', '2026-05-14 11:23:41.059+00', NULL, NULL, false, NULL, NULL),
(313563105205424128, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-15 06:27:50.001+00', '2026-05-15 06:27:50.001+00', NULL, NULL, false, NULL, NULL),
(313563111454937088, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-15 06:27:51.491+00', '2026-05-15 06:27:51.491+00', NULL, NULL, false, NULL, NULL),
(313575163261620224, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-15 07:15:44.866+00', '2026-05-15 07:15:44.866+00', NULL, NULL, false, NULL, NULL),
(313575167359455232, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-15 07:15:45.843+00', '2026-05-15 07:15:45.843+00', NULL, NULL, false, NULL, NULL),
(313578054997774336, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-15 07:27:14.310+00', '2026-05-15 07:27:14.310+00', NULL, NULL, false, NULL, NULL),
(313578082420133888, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-15 07:27:20.848+00', '2026-05-15 07:27:20.848+00', NULL, NULL, false, NULL, NULL),
(313581122194247680, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-15 07:39:25.586+00', '2026-05-15 07:39:25.586+00', NULL, NULL, false, NULL, NULL),
(313586124233117696, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-15 07:59:18.165+00', '2026-05-15 07:59:18.165+00', NULL, NULL, false, NULL, NULL),
(313586129283059712, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-15 07:59:19.369+00', '2026-05-15 07:59:19.369+00', NULL, NULL, false, NULL, NULL),
(313622490322571264, 1, 'admin', 'LOGIN', NULL, NULL, 'SUCCESS', NULL, NULL, NULL, '2026-05-15 10:23:48.517+00', '2026-05-15 10:23:48.517+00', NULL, NULL, false, NULL, NULL),
(313623659530948608, 1, 'admin', 'LOGIN', NULL, NULL, 'SUCCESS', NULL, NULL, NULL, '2026-05-15 10:28:27.278+00', '2026-05-15 10:28:27.278+00', NULL, NULL, false, NULL, NULL),
(313623732969017344, 1, 'admin', 'LOGIN', NULL, NULL, 'SUCCESS', NULL, NULL, NULL, '2026-05-15 10:28:44.787+00', '2026-05-15 10:28:44.787+00', NULL, NULL, false, NULL, NULL),
(313625453321523200, 1, 'admin', 'LOGIN', NULL, NULL, 'SUCCESS', NULL, NULL, NULL, '2026-05-15 10:35:34.951+00', '2026-05-15 10:35:34.951+00', NULL, NULL, false, NULL, NULL),
(313628740541747200, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-15 10:48:38.685+00', '2026-05-15 10:48:38.685+00', NULL, NULL, false, NULL, NULL),
(313639786271739904, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-15 11:32:32.192+00', '2026-05-15 11:32:32.192+00', NULL, NULL, false, NULL, NULL),
(313652635819839488, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-15 12:23:35.763+00', '2026-05-15 12:23:35.763+00', NULL, NULL, false, NULL, NULL),
(313652782247186432, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-15 12:24:10.674+00', '2026-05-15 12:24:10.674+00', NULL, NULL, false, NULL, NULL),
(313667016351813632, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-15 13:20:44.349+00', '2026-05-15 13:20:44.349+00', NULL, NULL, false, NULL, NULL),
(313667022253199360, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-15 13:20:45.756+00', '2026-05-15 13:20:45.756+00', NULL, NULL, false, NULL, NULL),
(313670888117506048, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-15 13:36:07.450+00', '2026-05-15 13:36:07.450+00', NULL, NULL, false, NULL, NULL),
(313928172938203136, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-16 06:38:28.931+00', '2026-05-16 06:38:28.931+00', NULL, NULL, false, NULL, NULL),
(313958935762046976, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-16 08:40:43.360+00', '2026-05-16 08:40:43.360+00', NULL, NULL, false, NULL, NULL),
(313958936252780544, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-16 08:40:43.477+00', '2026-05-16 08:40:43.477+00', NULL, NULL, false, NULL, NULL),
(313958942179332096, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-16 08:40:44.890+00', '2026-05-16 08:40:44.890+00', NULL, NULL, false, NULL, NULL),
(313962861248909312, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-16 08:56:19.269+00', '2026-05-16 08:56:19.269+00', NULL, NULL, false, NULL, NULL),
(313973111289548800, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-16 09:37:03.069+00', '2026-05-16 09:37:03.069+00', NULL, NULL, false, NULL, NULL),
(313973131715809280, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-16 09:37:07.939+00', '2026-05-16 09:37:07.939+00', NULL, NULL, false, NULL, NULL),
(314006667575431168, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-16 11:50:23.511+00', '2026-05-16 11:50:23.511+00', NULL, NULL, false, NULL, NULL),
(314007507300257792, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-16 11:53:43.717+00', '2026-05-16 11:53:43.717+00', NULL, NULL, false, NULL, NULL),
(314613552070529024, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-18 04:01:56.050+00', '2026-05-18 04:01:56.050+00', NULL, NULL, false, NULL, NULL),
(314613558743666688, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-18 04:01:57.641+00', '2026-05-18 04:01:57.641+00', NULL, NULL, false, NULL, NULL),
(314654493255405568, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-18 06:44:37.189+00', '2026-05-18 06:44:37.189+00', NULL, NULL, false, NULL, NULL),
(314654520262529024, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-18 06:44:43.628+00', '2026-05-18 06:44:43.628+00', NULL, NULL, false, NULL, NULL),
(314656823098478592, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-18 06:53:52.667+00', '2026-05-18 06:53:52.667+00', NULL, NULL, false, NULL, NULL),
(314656830274932736, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-18 06:53:54.378+00', '2026-05-18 06:53:54.378+00', NULL, NULL, false, NULL, NULL),
(314976369290252288, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-19 04:03:38.417+00', '2026-05-19 04:03:38.417+00', NULL, NULL, false, NULL, NULL),
(314980954021040128, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-19 04:21:51.501+00', '2026-05-19 04:21:51.501+00', NULL, NULL, false, NULL, NULL),
(315002299329024000, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-19 05:46:40.619+00', '2026-05-19 05:46:40.619+00', NULL, NULL, false, NULL, NULL),
(315012656430321664, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-19 06:27:49.946+00', '2026-05-19 06:27:49.946+00', NULL, NULL, false, NULL, NULL),
(315016540804616192, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-19 06:43:16.052+00', '2026-05-19 06:43:16.052+00', NULL, NULL, false, NULL, NULL),
(315016562988290048, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-19 06:43:21.341+00', '2026-05-19 06:43:21.341+00', NULL, NULL, false, NULL, NULL),
(315038056177995776, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-19 08:08:45.716+00', '2026-05-19 08:08:45.716+00', NULL, NULL, false, NULL, NULL),
(315038070212136960, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-19 08:08:49.062+00', '2026-05-19 08:08:49.062+00', NULL, NULL, false, NULL, NULL),
(315063042167541760, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-19 09:48:02.841+00', '2026-05-19 09:48:02.841+00', NULL, NULL, false, NULL, NULL),
(315063069384380416, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-19 09:48:09.330+00', '2026-05-19 09:48:09.330+00', NULL, NULL, false, NULL, NULL),
(315068637811052544, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-19 10:10:16.946+00', '2026-05-19 10:10:16.946+00', NULL, NULL, false, NULL, NULL),
(315068651803250688, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-19 10:10:20.282+00', '2026-05-19 10:10:20.282+00', NULL, NULL, false, NULL, NULL),
(315321093172760576, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-20 02:53:26.995+00', '2026-05-20 02:53:26.995+00', NULL, NULL, false, NULL, NULL),
(315321175112683520, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-20 02:53:46.531+00', '2026-05-20 02:53:46.531+00', NULL, NULL, false, NULL, NULL),
(315322167925411840, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-20 02:57:43.236+00', '2026-05-20 02:57:43.236+00', NULL, NULL, false, NULL, NULL),
(315322185566654464, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-20 02:57:47.442+00', '2026-05-20 02:57:47.442+00', NULL, NULL, false, NULL, NULL),
(315322199584018432, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-20 02:57:50.784+00', '2026-05-20 02:57:50.784+00', NULL, NULL, false, NULL, NULL),
(315329424590376960, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36 SLBrowser/9.0.7.12011 SLBChan/10 SLBVPV/64-bit', 'SUCCESS', NULL, NULL, NULL, '2026-05-20 03:26:33.359+00', '2026-05-20 03:26:33.359+00', NULL, NULL, false, NULL, NULL),
(315331021283266560, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36 SLBrowser/9.0.7.12011 SLBChan/10 SLBVPV/64-bit', 'SUCCESS', NULL, NULL, NULL, '2026-05-20 03:32:54.041+00', '2026-05-20 03:32:54.041+00', NULL, NULL, false, NULL, NULL),
(315331094910078976, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36 SLBrowser/9.0.7.12011 SLBChan/10 SLBVPV/64-bit', 'SUCCESS', NULL, NULL, NULL, '2026-05-20 03:33:11.595+00', '2026-05-20 03:33:11.595+00', NULL, NULL, false, NULL, NULL),
(315331132491042816, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36 SLBrowser/9.0.7.12011 SLBChan/10 SLBVPV/64-bit', 'SUCCESS', NULL, NULL, NULL, '2026-05-20 03:33:20.555+00', '2026-05-20 03:33:20.555+00', NULL, NULL, false, NULL, NULL),
(315339705543561216, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-20 04:07:24.530+00', '2026-05-20 04:07:24.530+00', NULL, NULL, false, NULL, NULL),
(315349851912540160, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-20 04:47:43.612+00', '2026-05-20 04:47:43.612+00', NULL, NULL, false, NULL, NULL),
(315349870858211328, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-20 04:47:48.129+00', '2026-05-20 04:47:48.129+00', NULL, NULL, false, NULL, NULL),
(315354537302429696, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-20 05:06:20.696+00', '2026-05-20 05:06:20.696+00', NULL, NULL, false, NULL, NULL),
(315354546429235200, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-20 05:06:22.872+00', '2026-05-20 05:06:22.872+00', NULL, NULL, false, NULL, NULL),
(315354546471178240, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-20 05:06:22.882+00', '2026-05-20 05:06:22.882+00', NULL, NULL, false, NULL, NULL),
(315354551210741760, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-20 05:06:24.012+00', '2026-05-20 05:06:24.012+00', NULL, NULL, false, NULL, NULL),
(315354551210741761, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-20 05:06:24.012+00', '2026-05-20 05:06:24.012+00', NULL, NULL, false, NULL, NULL),
(315354554893340672, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-20 05:06:24.890+00', '2026-05-20 05:06:24.890+00', NULL, NULL, false, NULL, NULL),
(315354554893340673, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-20 05:06:24.890+00', '2026-05-20 05:06:24.890+00', NULL, NULL, false, NULL, NULL),
(315354557065990144, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-20 05:06:25.408+00', '2026-05-20 05:06:25.408+00', NULL, NULL, false, NULL, NULL),
(315354557065990145, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-20 05:06:25.408+00', '2026-05-20 05:06:25.408+00', NULL, NULL, false, NULL, NULL),
(315354575881637888, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-20 05:06:29.894+00', '2026-05-20 05:06:29.894+00', NULL, NULL, false, NULL, NULL),
(315354575881637889, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-20 05:06:29.894+00', '2026-05-20 05:06:29.894+00', NULL, NULL, false, NULL, NULL),
(315354577269952512, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-20 05:06:30.225+00', '2026-05-20 05:06:30.225+00', NULL, NULL, false, NULL, NULL),
(315354577269952513, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-20 05:06:30.225+00', '2026-05-20 05:06:30.225+00', NULL, NULL, false, NULL, NULL),
(315354592197480448, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-20 05:06:33.784+00', '2026-05-20 05:06:33.784+00', NULL, NULL, false, NULL, NULL),
(315370825242382336, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36 SLBrowser/9.0.7.12011 SLBChan/10 SLBVPV/64-bit', 'SUCCESS', NULL, NULL, NULL, '2026-05-20 06:11:04.044+00', '2026-05-20 06:11:04.044+00', NULL, NULL, false, NULL, NULL),
(315370834222387200, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36 SLBrowser/9.0.7.12011 SLBChan/10 SLBVPV/64-bit', 'SUCCESS', NULL, NULL, NULL, '2026-05-20 06:11:06.185+00', '2026-05-20 06:11:06.185+00', NULL, NULL, false, NULL, NULL),
(315371856076476416, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-20 06:15:09.814+00', '2026-05-20 06:15:09.814+00', NULL, NULL, false, NULL, NULL),
(315372040399360000, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-20 06:15:53.760+00', '2026-05-20 06:15:53.760+00', NULL, NULL, false, NULL, NULL),
(315372052680282112, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-20 06:15:56.688+00', '2026-05-20 06:15:56.688+00', NULL, NULL, false, NULL, NULL),
(315405195463364608, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-20 08:27:38.543+00', '2026-05-20 08:27:38.543+00', NULL, NULL, false, NULL, NULL),
(315405195538862080, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-20 08:27:38.561+00', '2026-05-20 08:27:38.561+00', NULL, NULL, false, NULL, NULL),
(315405232520040448, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-20 08:27:47.378+00', '2026-05-20 08:27:47.378+00', NULL, NULL, false, NULL, NULL),
(315406969995268096, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36 SLBrowser/9.0.7.12011 SLBChan/10 SLBVPV/64-bit', 'SUCCESS', NULL, NULL, NULL, '2026-05-20 08:34:41.626+00', '2026-05-20 08:34:41.626+00', NULL, NULL, false, NULL, NULL),
(315411818261516288, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-20 08:53:57.541+00', '2026-05-20 08:53:57.541+00', NULL, NULL, false, NULL, NULL),
(315411834627690496, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-20 08:54:01.443+00', '2026-05-20 08:54:01.443+00', NULL, NULL, false, NULL, NULL),
(315419729882779648, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-20 09:25:23.819+00', '2026-05-20 09:25:23.819+00', NULL, NULL, false, NULL, NULL),
(315422494305030144, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-20 09:36:22.908+00', '2026-05-20 09:36:22.908+00', NULL, NULL, false, NULL, NULL),
(315422506875359232, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-20 09:36:25.905+00', '2026-05-20 09:36:25.905+00', NULL, NULL, false, NULL, NULL),
(315431183552876544, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-20 10:10:54.587+00', '2026-05-20 10:10:54.587+00', NULL, NULL, false, NULL, NULL),
(315435398486560768, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-20 10:27:39.505+00', '2026-05-20 10:27:39.505+00', NULL, NULL, false, NULL, NULL),
(315435411107221504, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-20 10:27:42.514+00', '2026-05-20 10:27:42.514+00', NULL, NULL, false, NULL, NULL),
(315465628446625792, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-20 12:27:46.889+00', '2026-05-20 12:27:46.889+00', NULL, NULL, false, NULL, NULL),
(315465628455014400, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-20 12:27:46.891+00', '2026-05-20 12:27:46.891+00', NULL, NULL, false, NULL, NULL),
(315465628777975808, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-20 12:27:46.968+00', '2026-05-20 12:27:46.968+00', NULL, NULL, false, NULL, NULL),
(315465648717697024, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-20 12:27:51.722+00', '2026-05-20 12:27:51.722+00', NULL, NULL, false, NULL, NULL),
(315469479887245312, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-20 12:43:05.144+00', '2026-05-20 12:43:05.144+00', NULL, NULL, false, NULL, NULL),
(315469497440407552, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-20 12:43:09.329+00', '2026-05-20 12:43:09.329+00', NULL, NULL, false, NULL, NULL),
(315690011765248000, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-21 03:19:24.042+00', '2026-05-21 03:19:24.042+00', NULL, NULL, false, NULL, NULL)
ON CONFLICT ("id") DO NOTHING;

INSERT INTO "whale_security_logs" ("id", "user_id", "user_name", "action", "client_ip", "user_agent", "result", "failure_reason", "tenant_id", "extra_properties", "created_at", "updated_at", "created_by", "updated_by", "is_deleted", "deleted_at", "deleted_by")
VALUES
(315740044691771392, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-21 06:38:12.820+00', '2026-05-21 06:38:12.820+00', NULL, NULL, false, NULL, NULL),
(315765681145516032, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-21 08:20:05.027+00', '2026-05-21 08:20:05.027+00', NULL, NULL, false, NULL, NULL),
(315765691832602624, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-21 08:20:07.575+00', '2026-05-21 08:20:07.575+00', NULL, NULL, false, NULL, NULL),
(315796947001675776, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-21 10:24:19.388+00', '2026-05-21 10:24:19.388+00', NULL, NULL, false, NULL, NULL),
(315796947010064384, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-21 10:24:19.390+00', '2026-05-21 10:24:19.390+00', NULL, NULL, false, NULL, NULL),
(315797143005696000, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-21 10:25:06.119+00', '2026-05-21 10:25:06.119+00', NULL, NULL, false, NULL, NULL),
(315807415028682752, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-21 11:05:55.160+00', '2026-05-21 11:05:55.160+00', NULL, NULL, false, NULL, NULL),
(315807428848914432, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-21 11:05:58.455+00', '2026-05-21 11:05:58.455+00', NULL, NULL, false, NULL, NULL),
(315815384995860480, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-21 11:37:35.349+00', '2026-05-21 11:37:35.349+00', NULL, NULL, false, NULL, NULL),
(315815395695529984, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-21 11:37:37.900+00', '2026-05-21 11:37:37.900+00', NULL, NULL, false, NULL, NULL),
(315820558900531200, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-21 11:58:08.904+00', '2026-05-21 11:58:08.904+00', NULL, NULL, false, NULL, NULL),
(315820565976322048, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-21 11:58:10.591+00', '2026-05-21 11:58:10.591+00', NULL, NULL, false, NULL, NULL),
(315826439553814528, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-21 12:21:30.961+00', '2026-05-21 12:21:30.961+00', NULL, NULL, false, NULL, NULL),
(315826450467393536, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-21 12:21:33.563+00', '2026-05-21 12:21:33.563+00', NULL, NULL, false, NULL, NULL),
(315831607118794752, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-21 12:42:03.004+00', '2026-05-21 12:42:03.004+00', NULL, NULL, false, NULL, NULL),
(316055721234403328, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-22 03:32:35.973+00', '2026-05-22 03:32:35.973+00', NULL, NULL, false, NULL, NULL),
(316056293186473984, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-22 03:34:52.337+00', '2026-05-22 03:34:52.337+00', NULL, NULL, false, NULL, NULL),
(316099163163463680, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-22 06:25:13.336+00', '2026-05-22 06:25:13.336+00', NULL, NULL, false, NULL, NULL),
(316099163205406720, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-22 06:25:13.346+00', '2026-05-22 06:25:13.346+00', NULL, NULL, false, NULL, NULL),
(316099163209601024, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-22 06:25:13.347+00', '2026-05-22 06:25:13.347+00', NULL, NULL, false, NULL, NULL),
(316101840950792192, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-22 06:35:51.770+00', '2026-05-22 06:35:51.770+00', NULL, NULL, false, NULL, NULL),
(316121752075243520, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-22 07:54:58.952+00', '2026-05-22 07:54:58.952+00', NULL, NULL, false, NULL, NULL),
(316121764607823872, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-22 07:55:01.940+00', '2026-05-22 07:55:01.940+00', NULL, NULL, false, NULL, NULL),
(316126067376001024, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-22 08:12:07.800+00', '2026-05-22 08:12:07.800+00', NULL, NULL, false, NULL, NULL),
(316126083817672704, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-22 08:12:11.720+00', '2026-05-22 08:12:11.720+00', NULL, NULL, false, NULL, NULL),
(316139874934067200, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-22 09:06:59.779+00', '2026-05-22 09:06:59.779+00', NULL, NULL, false, NULL, NULL),
(316139890151002112, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-22 09:07:03.407+00', '2026-05-22 09:07:03.407+00', NULL, NULL, false, NULL, NULL),
(316144711820120064, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-22 09:26:12.982+00', '2026-05-22 09:26:12.982+00', NULL, NULL, false, NULL, NULL),
(316144728781885440, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-22 09:26:17.027+00', '2026-05-22 09:26:17.027+00', NULL, NULL, false, NULL, NULL),
(316173181651521536, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-22 11:19:20.719+00', '2026-05-22 11:19:20.719+00', NULL, NULL, false, NULL, NULL),
(316178858045345792, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-22 11:41:54.077+00', '2026-05-22 11:41:54.077+00', NULL, NULL, false, NULL, NULL),
(316178867306369024, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-22 11:41:56.285+00', '2026-05-22 11:41:56.285+00', NULL, NULL, false, NULL, NULL),
(316187155621744640, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-22 12:14:52.373+00', '2026-05-22 12:14:52.373+00', NULL, NULL, false, NULL, NULL),
(316187165058928640, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-22 12:14:54.623+00', '2026-05-22 12:14:54.623+00', NULL, NULL, false, NULL, NULL),
(316189159556386816, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-22 12:22:50.148+00', '2026-05-22 12:22:50.148+00', NULL, NULL, false, NULL, NULL),
(316190777717821440, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-22 12:29:15.948+00', '2026-05-22 12:29:15.948+00', NULL, NULL, false, NULL, NULL),
(316190791387058176, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-22 12:29:19.207+00', '2026-05-22 12:29:19.207+00', NULL, NULL, false, NULL, NULL),
(316192939949690880, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-22 12:37:51.464+00', '2026-05-22 12:37:51.464+00', NULL, NULL, false, NULL, NULL),
(316192954969493504, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-22 12:37:55.045+00', '2026-05-22 12:37:55.045+00', NULL, NULL, false, NULL, NULL),
(316194250163752960, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-22 12:43:03.844+00', '2026-05-22 12:43:03.844+00', NULL, NULL, false, NULL, NULL),
(316194261970718720, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-22 12:43:06.659+00', '2026-05-22 12:43:06.659+00', NULL, NULL, false, NULL, NULL),
(316753554253549568, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-24 01:45:32.330+00', '2026-05-24 01:45:32.330+00', NULL, NULL, false, NULL, NULL),
(316753562356944896, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-24 01:45:34.262+00', '2026-05-24 01:45:34.262+00', NULL, NULL, false, NULL, NULL),
(316791812446490624, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-24 04:17:33.795+00', '2026-05-24 04:17:33.795+00', NULL, NULL, false, NULL, NULL),
(316791834059739136, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-24 04:17:38.947+00', '2026-05-24 04:17:38.947+00', NULL, NULL, false, NULL, NULL),
(316798703156858880, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-24 04:44:56.667+00', '2026-05-24 04:44:56.667+00', NULL, NULL, false, NULL, NULL),
(316801751634153472, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-24 04:57:03.482+00', '2026-05-24 04:57:03.482+00', NULL, NULL, false, NULL, NULL),
(316801760333139968, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-24 04:57:05.555+00', '2026-05-24 04:57:05.555+00', NULL, NULL, false, NULL, NULL),
(316813834919088128, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-24 05:45:04.361+00', '2026-05-24 05:45:04.361+00', NULL, NULL, false, NULL, NULL),
(316813851163627520, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-24 05:45:08.234+00', '2026-05-24 05:45:08.234+00', NULL, NULL, false, NULL, NULL),
(316829879810134016, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-24 06:48:49.761+00', '2026-05-24 06:48:49.761+00', NULL, NULL, false, NULL, NULL),
(316830001008742400, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-24 06:49:18.657+00', '2026-05-24 06:49:18.657+00', NULL, NULL, false, NULL, NULL),
(316840674746437632, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36 SLBrowser/9.0.7.12011 SLBChan/10 SLBVPV/64-bit', 'SUCCESS', NULL, NULL, NULL, '2026-05-24 07:31:43.474+00', '2026-05-24 07:31:43.474+00', NULL, NULL, false, NULL, NULL),
(316840681755119616, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-24 07:31:45.145+00', '2026-05-24 07:31:45.145+00', NULL, NULL, false, NULL, NULL),
(316852228361555968, NULL, NULL, 'LOGIN', '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-24 08:17:38.071+00', '2026-05-24 08:17:38.071+00', NULL, NULL, false, NULL, NULL),
(316864155231981568, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-24 09:05:01.658+00', '2026-05-24 09:05:01.658+00', NULL, NULL, false, NULL, NULL),
(316865211594182656, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-24 09:09:13.515+00', '2026-05-24 09:09:13.515+00', NULL, NULL, false, NULL, NULL),
(316865225439580160, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-24 09:09:16.816+00', '2026-05-24 09:09:16.816+00', NULL, NULL, false, NULL, NULL),
(316879688306790400, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-24 10:06:45.032+00', '2026-05-24 10:06:45.032+00', NULL, NULL, false, NULL, NULL),
(316879701300744192, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-24 10:06:48.130+00', '2026-05-24 10:06:48.130+00', NULL, NULL, false, NULL, NULL),
(316884385383583744, NULL, NULL, 'LOGOUT', '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-24 10:25:24.902+00', '2026-05-24 10:25:24.902+00', NULL, NULL, false, NULL, NULL),
(316884389837934592, NULL, NULL, 'LOGOUT', '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-24 10:25:25.964+00', '2026-05-24 10:25:25.964+00', NULL, NULL, false, NULL, NULL),
(316884399077986304, NULL, NULL, 'LOGOUT', '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-24 10:25:28.167+00', '2026-05-24 10:25:28.167+00', NULL, NULL, false, NULL, NULL),
(316884417272877056, NULL, NULL, 'LOGOUT', '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-24 10:25:32.505+00', '2026-05-24 10:25:32.505+00', NULL, NULL, false, NULL, NULL),
(316884424268976128, NULL, NULL, 'LOGOUT', '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-24 10:25:34.173+00', '2026-05-24 10:25:34.173+00', NULL, NULL, false, NULL, NULL),
(316884432657584128, NULL, NULL, 'LOGOUT', '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-24 10:25:36.173+00', '2026-05-24 10:25:36.173+00', NULL, NULL, false, NULL, NULL),
(316884433592913920, NULL, NULL, 'LOGOUT', '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-24 10:25:36.396+00', '2026-05-24 10:25:36.396+00', NULL, NULL, false, NULL, NULL),
(316884442782633984, NULL, NULL, 'LOGOUT', '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-24 10:25:38.587+00', '2026-05-24 10:25:38.587+00', NULL, NULL, false, NULL, NULL),
(316884446767222784, NULL, NULL, 'LOGOUT', '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-24 10:25:39.537+00', '2026-05-24 10:25:39.537+00', NULL, NULL, false, NULL, NULL),
(316885572568748032, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-24 10:30:07.953+00', '2026-05-24 10:30:07.953+00', NULL, NULL, false, NULL, NULL),
(316885572673605632, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-24 10:30:07.975+00', '2026-05-24 10:30:07.975+00', NULL, NULL, false, NULL, NULL),
(316886395805765632, NULL, NULL, 'LOGIN', '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-24 10:33:24.250+00', '2026-05-24 10:33:24.250+00', NULL, NULL, false, NULL, NULL),
(316886439946620928, NULL, NULL, 'LOGIN', '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-24 10:33:34.748+00', '2026-05-24 10:33:34.748+00', NULL, NULL, false, NULL, NULL),
(316886879360258048, NULL, NULL, 'LOGIN', '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-24 10:35:19.513+00', '2026-05-24 10:35:19.513+00', NULL, NULL, false, NULL, NULL),
(316887757551046656, NULL, NULL, 'LOGIN', '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-24 10:38:48.892+00', '2026-05-24 10:38:48.892+00', NULL, NULL, false, NULL, NULL),
(316888861210841088, NULL, NULL, 'LOGIN', '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-24 10:43:12.023+00', '2026-05-24 10:43:12.023+00', NULL, NULL, false, NULL, NULL),
(316890286364037120, NULL, NULL, 'LOGOUT', '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-24 10:48:51.806+00', '2026-05-24 10:48:51.806+00', NULL, NULL, false, NULL, NULL),
(316890338130137088, NULL, NULL, 'LOGIN', '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-24 10:49:04.148+00', '2026-05-24 10:49:04.148+00', NULL, NULL, false, NULL, NULL),
(316903085977899008, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-24 11:39:43.471+00', '2026-05-24 11:39:43.471+00', NULL, NULL, false, NULL, NULL),
(316903120060813312, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-24 11:39:51.597+00', '2026-05-24 11:39:51.597+00', NULL, NULL, false, NULL, NULL),
(316919417926062080, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-24 12:44:37.311+00', '2026-05-24 12:44:37.311+00', NULL, NULL, false, NULL, NULL),
(316919439098908672, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-24 12:44:42.359+00', '2026-05-24 12:44:42.359+00', NULL, NULL, false, NULL, NULL),
(316921794825555968, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-24 12:54:04.008+00', '2026-05-24 12:54:04.008+00', NULL, NULL, false, NULL, NULL),
(316921808389935104, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-24 12:54:07.242+00', '2026-05-24 12:54:07.242+00', NULL, NULL, false, NULL, NULL),
(316923204265316352, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-24 12:59:40.045+00', '2026-05-24 12:59:40.045+00', NULL, NULL, false, NULL, NULL),
(316923215472496640, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-24 12:59:42.717+00', '2026-05-24 12:59:42.717+00', NULL, NULL, false, NULL, NULL),
(316924834872299520, NULL, NULL, 'LOGOUT', '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-24 13:06:08.819+00', '2026-05-24 13:06:08.819+00', NULL, NULL, false, NULL, NULL),
(316924834872299521, NULL, NULL, 'LOGOUT', '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-24 13:06:08.819+00', '2026-05-24 13:06:08.819+00', NULL, NULL, false, NULL, NULL),
(316924834884882432, NULL, NULL, 'LOGOUT', '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-24 13:06:08.819+00', '2026-05-24 13:06:08.819+00', NULL, NULL, false, NULL, NULL),
(316924835321090048, NULL, NULL, 'LOGOUT', '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-24 13:06:08.919+00', '2026-05-24 13:06:08.919+00', NULL, NULL, false, NULL, NULL),
(316924835438530560, NULL, NULL, 'LOGOUT', '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-24 13:06:08.947+00', '2026-05-24 13:06:08.947+00', NULL, NULL, false, NULL, NULL),
(316924835438530561, NULL, NULL, 'LOGOUT', '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-24 13:06:08.947+00', '2026-05-24 13:06:08.947+00', NULL, NULL, false, NULL, NULL),
(316936175595556864, NULL, NULL, 'LOGIN', '127.0.0.1', NULL, 'SUCCESS', NULL, NULL, NULL, '2026-05-24 13:51:12.651+00', '2026-05-24 13:51:12.651+00', NULL, NULL, false, NULL, NULL),
(316936808557973504, NULL, NULL, 'LOGIN', '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36 QQBrowser/21.1.8663.400', 'SUCCESS', NULL, NULL, NULL, '2026-05-24 13:53:43.561+00', '2026-05-24 13:53:43.561+00', NULL, NULL, false, NULL, NULL),
(317170093031690240, NULL, NULL, 'LOGOUT', '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36 QQBrowser/21.1.8663.400', 'SUCCESS', NULL, NULL, NULL, '2026-05-25 05:20:42.915+00', '2026-05-25 05:20:42.915+00', NULL, NULL, false, NULL, NULL),
(317170466031144960, NULL, NULL, 'LOGIN', '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36 QQBrowser/21.1.8663.400', 'SUCCESS', NULL, NULL, NULL, '2026-05-25 05:22:11.844+00', '2026-05-25 05:22:11.844+00', NULL, NULL, false, NULL, NULL),
(317554518856830976, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-26 06:48:17.173+00', '2026-05-26 06:48:17.173+00', NULL, NULL, false, NULL, NULL),
(317554562548895744, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-26 06:48:27.590+00', '2026-05-26 06:48:27.590+00', NULL, NULL, false, NULL, NULL),
(317585000126484480, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-26 08:49:24.474+00', '2026-05-26 08:49:24.474+00', NULL, NULL, false, NULL, NULL),
(317585007244218368, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-26 08:49:26.171+00', '2026-05-26 08:49:26.171+00', NULL, NULL, false, NULL, NULL)
ON CONFLICT ("id") DO NOTHING;

INSERT INTO "whale_security_logs" ("id", "user_id", "user_name", "action", "client_ip", "user_agent", "result", "failure_reason", "tenant_id", "extra_properties", "created_at", "updated_at", "created_by", "updated_by", "is_deleted", "deleted_at", "deleted_by")
VALUES
(317598828475715584, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-26 09:44:21.409+00', '2026-05-26 09:44:21.409+00', NULL, NULL, false, NULL, NULL),
(317630972620181504, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-26 11:52:05.170+00', '2026-05-26 11:52:05.170+00', NULL, NULL, false, NULL, NULL),
(317630979825995776, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-26 11:52:06.888+00', '2026-05-26 11:52:06.888+00', NULL, NULL, false, NULL, NULL),
(317875464875020288, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-27 04:03:36.666+00', '2026-05-27 04:03:36.666+00', NULL, NULL, false, NULL, NULL),
(317875464912769024, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-27 04:03:36.675+00', '2026-05-27 04:03:36.675+00', NULL, NULL, false, NULL, NULL),
(317875464933740544, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-27 04:03:36.680+00', '2026-05-27 04:03:36.680+00', NULL, NULL, false, NULL, NULL),
(317875480419110912, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-27 04:03:40.372+00', '2026-05-27 04:03:40.372+00', NULL, NULL, false, NULL, NULL),
(317877139060822016, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-27 04:10:15.823+00', '2026-05-27 04:10:15.823+00', NULL, NULL, false, NULL, NULL),
(317878503392415744, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-27 04:15:41.105+00', '2026-05-27 04:15:41.105+00', NULL, NULL, false, NULL, NULL),
(317878513672654848, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-27 04:15:43.556+00', '2026-05-27 04:15:43.556+00', NULL, NULL, false, NULL, NULL),
(317880888743432192, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-27 04:25:09.817+00', '2026-05-27 04:25:09.817+00', NULL, NULL, false, NULL, NULL),
(317893338238816256, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-27 05:14:38.008+00', '2026-05-27 05:14:38.008+00', NULL, NULL, false, NULL, NULL),
(317893372183318528, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-27 05:14:46.101+00', '2026-05-27 05:14:46.101+00', NULL, NULL, false, NULL, NULL),
(317893394773839872, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-27 05:14:51.487+00', '2026-05-27 05:14:51.487+00', NULL, NULL, false, NULL, NULL),
(317893469990293504, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-27 05:15:09.421+00', '2026-05-27 05:15:09.421+00', NULL, NULL, false, NULL, NULL),
(317893692724613120, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36 SLBrowser/9.0.7.12011 SLBChan/10 SLBVPV/64-bit', 'SUCCESS', NULL, NULL, NULL, '2026-05-27 05:16:02.524+00', '2026-05-27 05:16:02.524+00', NULL, NULL, false, NULL, NULL),
(317893701805281280, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36 SLBrowser/9.0.7.12011 SLBChan/10 SLBVPV/64-bit', 'SUCCESS', NULL, NULL, NULL, '2026-05-27 05:16:04.689+00', '2026-05-27 05:16:04.689+00', NULL, NULL, false, NULL, NULL),
(317894238890102784, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-27 05:18:12.740+00', '2026-05-27 05:18:12.740+00', NULL, NULL, false, NULL, NULL),
(317894251523346432, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-27 05:18:15.752+00', '2026-05-27 05:18:15.752+00', NULL, NULL, false, NULL, NULL),
(317894756844703744, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-27 05:20:16.230+00', '2026-05-27 05:20:16.230+00', NULL, NULL, false, NULL, NULL),
(317894761366163456, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-27 05:20:17.308+00', '2026-05-27 05:20:17.308+00', NULL, NULL, false, NULL, NULL),
(317894810246582272, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-27 05:20:28.962+00', '2026-05-27 05:20:28.962+00', NULL, NULL, false, NULL, NULL),
(317894813409087488, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-27 05:20:29.716+00', '2026-05-27 05:20:29.716+00', NULL, NULL, false, NULL, NULL),
(317895211520888832, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-27 05:22:04.634+00', '2026-05-27 05:22:04.634+00', NULL, NULL, false, NULL, NULL),
(317895312414871552, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-27 05:22:28.689+00', '2026-05-27 05:22:28.689+00', NULL, NULL, false, NULL, NULL),
(317895327413702656, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-27 05:22:32.265+00', '2026-05-27 05:22:32.265+00', NULL, NULL, false, NULL, NULL),
(317896867390164992, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-27 05:28:39.424+00', '2026-05-27 05:28:39.424+00', NULL, NULL, false, NULL, NULL),
(317896873421574144, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-27 05:28:40.862+00', '2026-05-27 05:28:40.862+00', NULL, NULL, false, NULL, NULL),
(317901130791129088, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36 SLBrowser/9.0.7.12011 SLBChan/10 SLBVPV/64-bit', 'SUCCESS', NULL, NULL, NULL, '2026-05-27 05:45:35.898+00', '2026-05-27 05:45:35.898+00', NULL, NULL, false, NULL, NULL),
(317903283274387456, NULL, NULL, 'LOGIN', '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-27 05:54:09.091+00', '2026-05-27 05:54:09.091+00', NULL, NULL, false, NULL, NULL),
(317907227979550720, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-27 06:09:49.581+00', '2026-05-27 06:09:49.581+00', NULL, NULL, false, NULL, NULL),
(317907375040237568, NULL, NULL, 'LOGIN', '192.168.31.159', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-27 06:10:24.643+00', '2026-05-27 06:10:24.643+00', NULL, NULL, false, NULL, NULL),
(317907885683642368, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-27 06:12:26.389+00', '2026-05-27 06:12:26.389+00', NULL, NULL, false, NULL, NULL),
(317908915456577536, NULL, NULL, 'LOGIN', '192.168.31.150', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36 SLBrowser/9.0.7.12011 SLBChan/10 SLBVPV/64-bit', 'SUCCESS', NULL, NULL, NULL, '2026-05-27 06:16:31.906+00', '2026-05-27 06:16:31.906+00', NULL, NULL, false, NULL, NULL),
(317915176512262144, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-27 06:41:24.658+00', '2026-05-27 06:41:24.658+00', NULL, NULL, false, NULL, NULL),
(317915181809668096, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-27 06:41:25.921+00', '2026-05-27 06:41:25.921+00', NULL, NULL, false, NULL, NULL),
(317930671995621376, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-27 07:42:59.070+00', '2026-05-27 07:42:59.070+00', NULL, NULL, false, NULL, NULL),
(317930680040296448, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-27 07:43:00.988+00', '2026-05-27 07:43:00.988+00', NULL, NULL, false, NULL, NULL),
(317934305898663936, NULL, NULL, 'LOGOUT', '192.168.31.150', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36 SLBrowser/9.0.7.12011 SLBChan/10 SLBVPV/64-bit', 'SUCCESS', NULL, NULL, NULL, '2026-05-27 07:57:25.460+00', '2026-05-27 07:57:25.460+00', NULL, NULL, false, NULL, NULL),
(317942363886456832, NULL, NULL, 'LOGIN', '192.168.31.150', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36 SLBrowser/9.0.7.12011 SLBChan/10 SLBVPV/64-bit', 'SUCCESS', NULL, NULL, NULL, '2026-05-27 08:29:26.634+00', '2026-05-27 08:29:26.634+00', NULL, NULL, false, NULL, NULL),
(317963015490113536, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-27 09:51:30.360+00', '2026-05-27 09:51:30.360+00', NULL, NULL, false, NULL, NULL),
(317963033328488448, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-27 09:51:34.613+00', '2026-05-27 09:51:34.613+00', NULL, NULL, false, NULL, NULL),
(317970917332357120, NULL, NULL, 'LOGOUT', '192.168.31.159', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-27 10:22:54.306+00', '2026-05-27 10:22:54.306+00', NULL, NULL, false, NULL, NULL),
(317970922973696000, NULL, NULL, 'LOGIN', '192.168.31.159', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-27 10:22:55.651+00', '2026-05-27 10:22:55.651+00', NULL, NULL, false, NULL, NULL),
(317986509267406848, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-27 11:24:51.713+00', '2026-05-27 11:24:51.713+00', NULL, NULL, false, NULL, NULL),
(317986529177767936, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-27 11:24:56.460+00', '2026-05-27 11:24:56.460+00', NULL, NULL, false, NULL, NULL),
(318228370703781888, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-28 03:25:55.973+00', '2026-05-28 03:25:55.973+00', NULL, NULL, false, NULL, NULL),
(318228370703781889, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-28 03:25:55.973+00', '2026-05-28 03:25:55.973+00', NULL, NULL, false, NULL, NULL),
(318228370703781890, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-28 03:25:55.973+00', '2026-05-28 03:25:55.973+00', NULL, NULL, false, NULL, NULL),
(318228392107315200, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-28 03:26:01.076+00', '2026-05-28 03:26:01.076+00', NULL, NULL, false, NULL, NULL),
(318228392124092416, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-28 03:26:01.080+00', '2026-05-28 03:26:01.080+00', NULL, NULL, false, NULL, NULL),
(318228395706028032, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-28 03:26:01.934+00', '2026-05-28 03:26:01.934+00', NULL, NULL, false, NULL, NULL),
(318228395706028033, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-28 03:26:01.934+00', '2026-05-28 03:26:01.934+00', NULL, NULL, false, NULL, NULL),
(318228411334004736, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-28 03:26:05.660+00', '2026-05-28 03:26:05.660+00', NULL, NULL, false, NULL, NULL),
(318228411334004737, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-28 03:26:05.660+00', '2026-05-28 03:26:05.660+00', NULL, NULL, false, NULL, NULL),
(318228421782016000, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-28 03:26:08.151+00', '2026-05-28 03:26:08.151+00', NULL, NULL, false, NULL, NULL),
(318228421782016001, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-28 03:26:08.151+00', '2026-05-28 03:26:08.151+00', NULL, NULL, false, NULL, NULL),
(318228421786210304, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-28 03:26:08.152+00', '2026-05-28 03:26:08.152+00', NULL, NULL, false, NULL, NULL),
(318228507240960000, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-28 03:26:28.526+00', '2026-05-28 03:26:28.526+00', NULL, NULL, false, NULL, NULL),
(318266218953838592, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-28 05:56:19.699+00', '2026-05-28 05:56:19.699+00', NULL, NULL, false, NULL, NULL),
(318266227434721280, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-28 05:56:21.721+00', '2026-05-28 05:56:21.721+00', NULL, NULL, false, NULL, NULL),
(318268453180936192, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-28 06:05:12.380+00', '2026-05-28 06:05:12.380+00', NULL, NULL, false, NULL, NULL),
(318268475217809408, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-28 06:05:17.634+00', '2026-05-28 06:05:17.634+00', NULL, NULL, false, NULL, NULL),
(318270117821026304, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-28 06:11:49.261+00', '2026-05-28 06:11:49.261+00', NULL, NULL, false, NULL, NULL),
(318270138444419072, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-28 06:11:54.178+00', '2026-05-28 06:11:54.178+00', NULL, NULL, false, NULL, NULL),
(318275737297620992, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-28 06:34:09.049+00', '2026-05-28 06:34:09.049+00', NULL, NULL, false, NULL, NULL),
(318275750270603264, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-28 06:34:12.143+00', '2026-05-28 06:34:12.143+00', NULL, NULL, false, NULL, NULL),
(318280190977380352, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-28 06:51:50.889+00', '2026-05-28 06:51:50.889+00', NULL, NULL, false, NULL, NULL),
(318280203468017664, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-28 06:51:53.867+00', '2026-05-28 06:51:53.867+00', NULL, NULL, false, NULL, NULL),
(318281093616242688, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-28 06:55:26.094+00', '2026-05-28 06:55:26.094+00', NULL, NULL, false, NULL, NULL),
(318281106203348992, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-28 06:55:29.095+00', '2026-05-28 06:55:29.095+00', NULL, NULL, false, NULL, NULL),
(318281129393655808, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-28 06:55:34.624+00', '2026-05-28 06:55:34.624+00', NULL, NULL, false, NULL, NULL),
(318281278803677184, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-28 06:56:10.247+00', '2026-05-28 06:56:10.247+00', NULL, NULL, false, NULL, NULL),
(318281289159413760, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-28 06:56:12.716+00', '2026-05-28 06:56:12.716+00', NULL, NULL, false, NULL, NULL),
(318282673732718592, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-28 07:01:42.824+00', '2026-05-28 07:01:42.824+00', NULL, NULL, false, NULL, NULL),
(318283295508926464, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-28 07:04:11.067+00', '2026-05-28 07:04:11.067+00', NULL, NULL, false, NULL, NULL),
(318283336545996800, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-28 07:04:20.851+00', '2026-05-28 07:04:20.851+00', NULL, NULL, false, NULL, NULL),
(318283734128267264, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-28 07:05:55.642+00', '2026-05-28 07:05:55.642+00', NULL, NULL, false, NULL, NULL),
(318284762588385280, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-28 07:10:00.846+00', '2026-05-28 07:10:00.846+00', NULL, NULL, false, NULL, NULL),
(318284788089753600, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-28 07:10:06.926+00', '2026-05-28 07:10:06.926+00', NULL, NULL, false, NULL, NULL),
(318284798630039552, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-28 07:10:09.439+00', '2026-05-28 07:10:09.439+00', NULL, NULL, false, NULL, NULL),
(318285117672919040, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-28 07:11:25.504+00', '2026-05-28 07:11:25.504+00', NULL, NULL, false, NULL, NULL),
(318285121909166080, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-28 07:11:26.514+00', '2026-05-28 07:11:26.514+00', NULL, NULL, false, NULL, NULL),
(318285721250041856, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-28 07:13:49.408+00', '2026-05-28 07:13:49.408+00', NULL, NULL, false, NULL, NULL),
(318290796655153152, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-28 07:33:59.479+00', '2026-05-28 07:33:59.479+00', NULL, NULL, false, NULL, NULL),
(318290827466510336, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-28 07:34:06.825+00', '2026-05-28 07:34:06.825+00', NULL, NULL, false, NULL, NULL),
(318290833229484032, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-28 07:34:08.199+00', '2026-05-28 07:34:08.199+00', NULL, NULL, false, NULL, NULL),
(318291309468061696, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-28 07:36:01.743+00', '2026-05-28 07:36:01.743+00', NULL, NULL, false, NULL, NULL),
(318291314518003712, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-28 07:36:02.947+00', '2026-05-28 07:36:02.947+00', NULL, NULL, false, NULL, NULL),
(318291502410240000, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-28 07:36:47.744+00', '2026-05-28 07:36:47.744+00', NULL, NULL, false, NULL, NULL),
(318291509414727680, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-28 07:36:49.414+00', '2026-05-28 07:36:49.414+00', NULL, NULL, false, NULL, NULL),
(318291509691551744, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-28 07:36:49.480+00', '2026-05-28 07:36:49.480+00', NULL, NULL, false, NULL, NULL),
(318291520072454144, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-28 07:36:51.955+00', '2026-05-28 07:36:51.955+00', NULL, NULL, false, NULL, NULL),
(318291816165150720, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-28 07:38:02.549+00', '2026-05-28 07:38:02.549+00', NULL, NULL, false, NULL, NULL),
(318291820011327488, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-28 07:38:03.466+00', '2026-05-28 07:38:03.466+00', NULL, NULL, false, NULL, NULL),
(318291893147406336, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-28 07:38:20.903+00', '2026-05-28 07:38:20.903+00', NULL, NULL, false, NULL, NULL),
(318291900340637696, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-28 07:38:22.618+00', '2026-05-28 07:38:22.618+00', NULL, NULL, false, NULL, NULL),
(318292993380454400, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-28 07:42:43.219+00', '2026-05-28 07:42:43.219+00', NULL, NULL, false, NULL, NULL),
(318293080605200384, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-28 07:43:04.015+00', '2026-05-28 07:43:04.015+00', NULL, NULL, false, NULL, NULL),
(318293084614955008, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-28 07:43:04.971+00', '2026-05-28 07:43:04.971+00', NULL, NULL, false, NULL, NULL)
ON CONFLICT ("id") DO NOTHING;

INSERT INTO "whale_security_logs" ("id", "user_id", "user_name", "action", "client_ip", "user_agent", "result", "failure_reason", "tenant_id", "extra_properties", "created_at", "updated_at", "created_by", "updated_by", "is_deleted", "deleted_at", "deleted_by")
VALUES
(318293570833879040, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-28 07:45:00.895+00', '2026-05-28 07:45:00.895+00', NULL, NULL, false, NULL, NULL),
(318293575611191296, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-28 07:45:02.034+00', '2026-05-28 07:45:02.034+00', NULL, NULL, false, NULL, NULL),
(318293863457886208, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-28 07:46:10.662+00', '2026-05-28 07:46:10.662+00', NULL, NULL, false, NULL, NULL),
(318295453182464000, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-28 07:52:29.682+00', '2026-05-28 07:52:29.682+00', NULL, NULL, false, NULL, NULL),
(318295473206071296, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-28 07:52:34.456+00', '2026-05-28 07:52:34.456+00', NULL, NULL, false, NULL, NULL),
(318295492290154496, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-28 07:52:39.006+00', '2026-05-28 07:52:39.006+00', NULL, NULL, false, NULL, NULL),
(318295985225732096, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-28 07:54:36.531+00', '2026-05-28 07:54:36.531+00', NULL, NULL, false, NULL, NULL),
(318295992226025472, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-28 07:54:38.200+00', '2026-05-28 07:54:38.200+00', NULL, NULL, false, NULL, NULL),
(318298458510331904, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-28 08:04:26.208+00', '2026-05-28 08:04:26.208+00', NULL, NULL, false, NULL, NULL),
(318298481331539968, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-28 08:04:31.649+00', '2026-05-28 08:04:31.649+00', NULL, NULL, false, NULL, NULL),
(318298486184349696, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-28 08:04:32.806+00', '2026-05-28 08:04:32.806+00', NULL, NULL, false, NULL, NULL),
(318301968672493568, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-28 08:18:23.096+00', '2026-05-28 08:18:23.096+00', NULL, NULL, false, NULL, NULL),
(318301973982482432, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-28 08:18:24.362+00', '2026-05-28 08:18:24.362+00', NULL, NULL, false, NULL, NULL),
(318302095181090816, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-28 08:18:53.258+00', '2026-05-28 08:18:53.258+00', NULL, NULL, false, NULL, NULL),
(318310487247294464, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-28 08:52:14.082+00', '2026-05-28 08:52:14.082+00', NULL, NULL, false, NULL, NULL),
(318310595389034496, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-28 08:52:39.865+00', '2026-05-28 08:52:39.865+00', NULL, NULL, false, NULL, NULL),
(318310814742745088, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-28 08:53:32.163+00', '2026-05-28 08:53:32.163+00', NULL, NULL, false, NULL, NULL),
(318310818630864896, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-28 08:53:33.090+00', '2026-05-28 08:53:33.090+00', NULL, NULL, false, NULL, NULL),
(318314058958770176, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-28 09:06:25.645+00', '2026-05-28 09:06:25.645+00', NULL, NULL, false, NULL, NULL),
(318314063568310272, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-28 09:06:26.744+00', '2026-05-28 09:06:26.744+00', NULL, NULL, false, NULL, NULL),
(318316535674834944, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-28 09:16:16.140+00', '2026-05-28 09:16:16.140+00', NULL, NULL, false, NULL, NULL),
(318316540355678208, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-28 09:16:17.256+00', '2026-05-28 09:16:17.256+00', NULL, NULL, false, NULL, NULL),
(318336736218845184, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-28 10:36:32.325+00', '2026-05-28 10:36:32.325+00', NULL, NULL, false, NULL, NULL),
(318337388361814016, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-28 10:39:07.808+00', '2026-05-28 10:39:07.808+00', NULL, NULL, false, NULL, NULL),
(318588840363692032, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-29 03:18:18.638+00', '2026-05-29 03:18:18.638+00', NULL, NULL, false, NULL, NULL),
(318588852002885632, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-29 03:18:21.412+00', '2026-05-29 03:18:21.412+00', NULL, NULL, false, NULL, NULL),
(318590738756014080, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-29 03:25:51.249+00', '2026-05-29 03:25:51.249+00', NULL, NULL, false, NULL, NULL),
(318590745768890368, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-29 03:25:52.921+00', '2026-05-29 03:25:52.921+00', NULL, NULL, false, NULL, NULL),
(318590785853853696, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-29 03:26:02.478+00', '2026-05-29 03:26:02.478+00', NULL, NULL, false, NULL, NULL),
(318590794460565504, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-29 03:26:04.530+00', '2026-05-29 03:26:04.530+00', NULL, NULL, false, NULL, NULL),
(318591108613935104, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-29 03:27:19.430+00', '2026-05-29 03:27:19.430+00', NULL, NULL, false, NULL, NULL),
(318594366396960768, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-29 03:40:16.146+00', '2026-05-29 03:40:16.146+00', NULL, NULL, false, NULL, NULL),
(318594377574780928, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-29 03:40:18.811+00', '2026-05-29 03:40:18.811+00', NULL, NULL, false, NULL, NULL),
(318594957433114624, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-29 03:42:37.060+00', '2026-05-29 03:42:37.060+00', NULL, NULL, false, NULL, NULL),
(318594967507832832, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-29 03:42:39.462+00', '2026-05-29 03:42:39.462+00', NULL, NULL, false, NULL, NULL),
(318595008704286720, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-29 03:42:49.284+00', '2026-05-29 03:42:49.284+00', NULL, NULL, false, NULL, NULL),
(318595028077776896, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-29 03:42:53.903+00', '2026-05-29 03:42:53.903+00', NULL, NULL, false, NULL, NULL),
(318595095442493440, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-29 03:43:09.964+00', '2026-05-29 03:43:09.964+00', NULL, NULL, false, NULL, NULL),
(318595105546571776, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-29 03:43:12.373+00', '2026-05-29 03:43:12.373+00', NULL, NULL, false, NULL, NULL),
(318596600987914240, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-29 03:49:08.914+00', '2026-05-29 03:49:08.914+00', NULL, NULL, false, NULL, NULL),
(318596613239476224, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-29 03:49:11.835+00', '2026-05-29 03:49:11.835+00', NULL, NULL, false, NULL, NULL),
(318596661905985536, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-29 03:49:23.438+00', '2026-05-29 03:49:23.438+00', NULL, NULL, false, NULL, NULL),
(318596681975730176, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-29 03:49:28.223+00', '2026-05-29 03:49:28.223+00', NULL, NULL, false, NULL, NULL),
(318606733759483904, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-29 04:29:24.755+00', '2026-05-29 04:29:24.755+00', NULL, NULL, false, NULL, NULL),
(318606741250510848, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-29 04:29:26.541+00', '2026-05-29 04:29:26.541+00', NULL, NULL, false, NULL, NULL),
(318613894488133632, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-29 04:57:52.005+00', '2026-05-29 04:57:52.005+00', NULL, NULL, false, NULL, NULL),
(318614072838328320, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-29 04:58:34.527+00', '2026-05-29 04:58:34.527+00', NULL, NULL, false, NULL, NULL),
(318614081847693312, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-29 04:58:36.675+00', '2026-05-29 04:58:36.675+00', NULL, NULL, false, NULL, NULL),
(318618710409482240, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-29 05:17:00.211+00', '2026-05-29 05:17:00.211+00', NULL, NULL, false, NULL, NULL),
(318618718798090240, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-29 05:17:02.211+00', '2026-05-29 05:17:02.211+00', NULL, NULL, false, NULL, NULL),
(318622371588608000, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-29 05:31:33.104+00', '2026-05-29 05:31:33.104+00', NULL, NULL, false, NULL, NULL),
(318622382770622464, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-29 05:31:35.770+00', '2026-05-29 05:31:35.770+00', NULL, NULL, false, NULL, NULL),
(318627064787243008, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-29 05:50:12.049+00', '2026-05-29 05:50:12.049+00', NULL, NULL, false, NULL, NULL),
(318627076279635968, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-29 05:50:14.789+00', '2026-05-29 05:50:14.789+00', NULL, NULL, false, NULL, NULL),
(318644623672086528, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-29 06:59:58.414+00', '2026-05-29 06:59:58.414+00', NULL, NULL, false, NULL, NULL),
(318644623927939072, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-29 06:59:58.475+00', '2026-05-29 06:59:58.475+00', NULL, NULL, false, NULL, NULL),
(318644633331568640, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-29 07:00:00.717+00', '2026-05-29 07:00:00.717+00', NULL, NULL, false, NULL, NULL),
(318644634627608576, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-29 07:00:01.026+00', '2026-05-29 07:00:01.026+00', NULL, NULL, false, NULL, NULL),
(318651296987484160, NULL, NULL, 'LOGOUT', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-29 07:26:29.456+00', '2026-05-29 07:26:29.456+00', NULL, NULL, false, NULL, NULL),
(318651310484754432, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-29 07:26:32.674+00', '2026-05-29 07:26:32.674+00', NULL, NULL, false, NULL, NULL),
(318661994281439232, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-29 08:08:59.890+00', '2026-05-29 08:08:59.890+00', NULL, NULL, false, NULL, NULL),
(318662008558850048, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-29 08:09:03.294+00', '2026-05-29 08:09:03.294+00', NULL, NULL, false, NULL, NULL),
(318666079508369408, NULL, NULL, 'LOGOUT', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-29 08:25:13.884+00', '2026-05-29 08:25:13.884+00', NULL, NULL, false, NULL, NULL),
(318666091965452288, NULL, NULL, 'LOGIN', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0', 'SUCCESS', NULL, NULL, NULL, '2026-05-29 08:25:16.854+00', '2026-05-29 08:25:16.854+00', NULL, NULL, false, NULL, NULL),
(318670632043679744, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-29 08:43:19.293+00', '2026-05-29 08:43:19.293+00', NULL, NULL, false, NULL, NULL),
(318936674883604480, NULL, NULL, 'LOGIN', '192.168.31.245', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 'SUCCESS', NULL, NULL, NULL, '2026-05-30 02:20:28.849+00', '2026-05-30 02:20:28.849+00', NULL, NULL, false, NULL, NULL)
ON CONFLICT ("id") DO NOTHING;


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
(1, 'ROOT', '总部', NULL, 0, true, '根组织单元', 1, '2026-05-03 06:07:04.560+00', '2026-05-03 06:07:04.560+00', 1, 1, false, NULL, NULL),
(6, 'DEPT_OUTPATIENT', '门诊部', NULL, 1, true, '第二分院-门诊部', 1, '2026-05-03 06:07:04.560+00', '2026-05-03 06:07:04.560+00', 1, 1, false, NULL, NULL),
(7, 'DEPT_PHARMACY', '药房', NULL, 2, true, '第二分院-药房', 1, '2026-05-03 06:07:04.560+00', '2026-05-03 06:07:04.560+00', 1, 1, false, NULL, NULL)
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
(3, 4, 6, 1, '2026-05-03 06:07:04.567+00', '2026-05-03 06:07:04.567+00', 1, NULL, false, NULL, NULL),
(5, 1, 1, 1, '2026-05-03 06:07:04.567+00', '2026-05-03 06:07:04.567+00', 1, NULL, false, NULL, NULL),
(10, 1, 6, 1, '2026-05-03 06:07:04.567+00', '2026-05-03 06:07:04.567+00', 1, NULL, false, NULL, NULL),
(11, 1, 7, 1, '2026-05-03 06:07:04.567+00', '2026-05-03 06:07:04.567+00', 1, NULL, false, NULL, NULL)
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

-- seed: whale_user_claims
-- (empty) no rows in whale_user_claims


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
-- (empty) no rows in whale_role_claims


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

-- seed: whale_qrtz_job_details
-- (empty) no rows in whale_qrtz_job_details


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

-- seed: whale_qrtz_triggers
-- (empty) no rows in whale_qrtz_triggers


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

-- seed: whale_qrtz_cron_triggers
-- (empty) no rows in whale_qrtz_cron_triggers


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

-- seed: whale_qrtz_simple_triggers
-- (empty) no rows in whale_qrtz_simple_triggers


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

-- seed: whale_qrtz_blob_triggers
-- (empty) no rows in whale_qrtz_blob_triggers


-- ========== whale_qrtz_calendars ==========
CREATE TABLE IF NOT EXISTS whale_qrtz_calendars (
    sched_name VARCHAR(120) NOT NULL,
    calendar_name VARCHAR(200) NOT NULL,
    calendar BYTEA NOT NULL,
    PRIMARY KEY (sched_name, calendar_name)
);

-- seed: whale_qrtz_calendars
-- (empty) no rows in whale_qrtz_calendars


-- ========== whale_qrtz_paused_trigger_grps ==========
CREATE TABLE IF NOT EXISTS whale_qrtz_paused_trigger_grps (
    sched_name VARCHAR(120) NOT NULL,
    trigger_group VARCHAR(200) NOT NULL,
    PRIMARY KEY (sched_name, trigger_group)
);

-- seed: whale_qrtz_paused_trigger_grps
-- (empty) no rows in whale_qrtz_paused_trigger_grps


-- ========== whale_qrtz_scheduler_state ==========
CREATE TABLE IF NOT EXISTS whale_qrtz_scheduler_state (
    sched_name VARCHAR(120) NOT NULL,
    instance_name VARCHAR(200) NOT NULL,
    last_checkin_time BIGINT NOT NULL,
    checkin_interval BIGINT NOT NULL,
    PRIMARY KEY (sched_name, instance_name)
);

-- seed: whale_qrtz_scheduler_state
-- (empty) no rows in whale_qrtz_scheduler_state


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

-- seed: whale_qrtz_fired_triggers
-- (empty) no rows in whale_qrtz_fired_triggers


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
(1, '默认租户', 'DEFAULT', '系统默认租户，用于单租户或主租户场景', true, NULL, '2026-05-03 06:07:04.556+00', '2026-05-03 06:07:04.556+00', 1, 1, false, NULL, NULL),
(318594536438239232, '测试租户', 'A001', '', true, NULL, '2026-05-29 03:40:56.687+00', '2026-05-29 03:40:56.687+00', 1, 1, false, NULL, NULL)
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
(1, 2, 1, '2026-05-03 06:07:04.564+00', '2026-05-03 06:07:04.564+00', 1, NULL, false, NULL, NULL),
(2, 3, 1, '2026-05-03 06:07:04.564+00', '2026-05-03 06:07:04.564+00', 1, NULL, false, NULL, NULL),
(3, 4, 1, '2026-05-03 06:07:04.564+00', '2026-05-03 06:07:04.564+00', 1, NULL, false, NULL, NULL),
(4, 5, 2, '2026-05-03 06:07:04.564+00', '2026-05-03 06:07:04.564+00', 1, NULL, false, NULL, NULL),
(5, 1, 1, '2026-05-03 06:07:04.564+00', '2026-05-03 06:07:04.564+00', 1, NULL, false, NULL, NULL),
(6, 1, 2, '2026-05-03 06:07:04.564+00', '2026-05-03 06:07:04.564+00', 1, NULL, false, NULL, NULL),
(7, 1, 3, '2026-05-03 06:07:04.564+00', '2026-05-03 06:07:04.564+00', 1, NULL, false, NULL, NULL),
(315330703718879232, 315330627751645184, 315329920793317376, '2026-05-20 03:31:38.327+00', '2026-05-20 03:31:38.327+00', 1, 1, false, NULL, NULL),
(315330951192252416, 1, 315329920793317376, '2026-05-20 03:32:37.330+00', '2026-05-20 03:32:37.330+00', 1, 1, false, NULL, NULL),
(318594939435356160, 318594694756438016, 318594536438239232, '2026-05-29 03:42:32.769+00', '2026-05-29 03:42:32.769+00', 1, 1, false, NULL, NULL),
(318595075771207680, 1, 318594536438239232, '2026-05-29 03:43:05.274+00', '2026-05-29 03:43:05.274+00', 1, 1, false, NULL, NULL)
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
-- (empty) no rows in whale_settings


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
(1000, 'system_management', 'SystemManagement', '系统管理', 'lucide:folder-tree', '/system', '', '', NULL, 1, true, false, false, '', 'directory', '系统管理目录', NULL, '2026-05-27 04:00:00.000+00', '2026-05-27 04:00:00.000+00', 1, 1, false, NULL, NULL, true),
(1001, 'user_management', 'UserManagement', '用户管理', 'lucide:user', '/system/user', '/system/user/index', 'Identity.Users.View', 1000, 1, true, false, false, '', 'menu', '', NULL, '2026-05-27 04:00:00.000+00', '2026-05-27 04:00:00.000+00', 1, 1, false, NULL, NULL, true),
(1002, 'role_management', 'RoleManagement', '角色管理', 'lucide:shield-check', '/system/user/role', '/system/user/role/index', 'Identity.Roles.View', 1000, 2, true, false, false, '', 'menu', '', NULL, '2026-05-27 04:00:00.000+00', '2026-05-27 04:00:00.000+00', 1, 1, false, NULL, NULL, true),
(1004, 'permission_management', 'PermissionManagement', '权限管理', 'lucide:fingerprint-pattern', '/system/user/permission', '/system/user/permission/index', 'Identity.Permissions.View', 1000, 3, true, false, false, '', 'menu', '', NULL, '2026-05-27 04:00:00.000+00', '2026-05-27 04:00:00.000+00', 1, 1, false, NULL, NULL, true),
(1005, 'tenant_management', 'TenantManagement', '租户管理', 'lucide:square-user-round', '/system/tenant', '/system/tenant/index', 'TenantManagement.Tenants.View', 1000, 4, true, false, false, '', 'menu', '', NULL, '2026-05-27 04:00:00.000+00', '2026-05-27 04:00:00.000+00', 1, 1, false, NULL, NULL, true),
(1006, 'menu_management', 'MenuManagement', '菜单管理', 'lucide:square-menu', '/system/menu', '/system/menu/index', 'Menu.Menus.View', 1000, 5, true, false, false, '', 'menu', '', NULL, '2026-05-27 04:00:00.000+00', '2026-05-27 04:00:00.000+00', 1, 1, false, NULL, NULL, true),
(1007, 'setting_management', 'SettingManagement', '设置管理', 'lucide:settings', '/system/settings', '/system/settings/index', 'SettingManagement.Settings.View', 1000, 6, true, false, false, '', 'menu', '', NULL, '2026-05-27 04:00:00.000+00', '2026-05-27 04:00:00.000+00', 1, 1, false, NULL, NULL, true),
(2000, 'basic_data', 'BasicData', '基础数据', 'lucide:database', '/basic', '', '', NULL, 2, true, false, false, '', 'directory', '基础数据目录', NULL, '2026-05-27 04:00:00.000+00', '2026-05-27 04:00:00.000+00', 1, 1, false, NULL, NULL, true),
(2001, 'codesystem_management', 'CodeSystemManagement', '编码管理', 'lucide:message-circle-code', '/basic/code', '/basic/code/index', 'Terminology.CodeSystems.View', 2000, 2, true, false, false, '', 'menu', '', NULL, '2026-05-27 04:00:00.000+00', '2026-05-27 04:00:00.000+00', 1, 1, false, NULL, NULL, true),
(2002, 'bodystructure_management', 'BodyStructureManagement', '部位管理', 'lucide:person-standing', '/basic/body', '/basic/body/index', 'Administration.BodyStructures.View', 2000, 5, true, false, false, '', 'menu', '', NULL, '2026-05-27 04:00:00.000+00', '2026-05-27 04:00:00.000+00', 1, 1, false, NULL, NULL, true),
(2003, 'warehouse_management', 'WarehouseManagement', '库房药房', 'lucide:map-pinned', '/basic/warehouse', '/basic/warehouse/index', 'Administration.Locations.View', 2000, 7, true, false, false, '', 'menu', '', NULL, '2026-05-27 04:00:00.000+00', '2026-05-27 04:00:00.000+00', 1, 1, false, NULL, NULL, true),
(2004, 'ward_location_management', 'WardLocationManagement', '病区病房', 'lucide:circle-pile', '/basic/wardbed', '/basic/wardbed/index', 'Administration.WardLocations.View', 2000, 6, true, false, false, '', 'menu', '', NULL, '2026-05-27 04:00:00.000+00', '2026-05-27 04:00:00.000+00', 1, 1, false, NULL, NULL, true),
(2005, 'patient_management', 'PatientManagement', '患者管理', 'lucide:file-user', '/basic/patient', '/basic/patient/index', 'Administration.Patients.View', 2000, 3, true, false, false, '', 'menu', '', NULL, '2026-05-27 04:00:00.000+00', '2026-05-27 04:00:00.000+00', 1, 1, false, NULL, NULL, true),
(2006, 'position_management', 'PositionManagement', '岗位管理', 'lucide:briefcase-business', '/basic/position', '/basic/position/index', 'Administration.Positions.View', 2000, 4, true, false, false, '', 'menu', '', NULL, '2026-05-27 04:00:00.000+00', '2026-05-27 04:00:00.000+00', 1, 1, false, NULL, NULL, true),
(2007, 'dept_management', 'DeptManagement', '科室管理', 'lucide:building', '/basic/department', '/basic/department/index', 'Administration.Organizations.View', 2000, 1, true, false, false, '', 'menu', '', NULL, '2026-05-27 04:00:00.000+00', '2026-05-27 04:00:00.000+00', 1, 1, false, NULL, NULL, true),
(2008, 'healthcare_service_management', 'HealthcareServiceManagement', '挂号类型管理', 'lucide:clipboard-list', '/basic/registrationtype', '/basic/registrationtype/index', 'Administration.HealthcareServices.View', 2000, 11, true, false, false, '', 'menu', '', NULL, '2026-05-27 04:00:00.000+00', '2026-05-27 04:00:00.000+00', 1, 1, false, NULL, NULL, true),
(2010, 'contract_management', 'ContractManagement', '费用性质管理', 'lucide:credit-card', '/basic/chargetype', '/basic/chargetype/index', 'Financial.Contract.View', 2000, 10, true, false, false, '', 'menu', '', NULL, '2026-05-27 04:00:00.000+00', '2026-05-27 04:00:00.000+00', 1, 1, false, NULL, NULL, true),
(279418897187213312, 'Analytics', 'Analytics', '概览', 'lucide:chart-line', '/analytics', '/_core/analytics/index', '', NULL, 0, true, false, false, '', 'menu', '', NULL, '2026-05-27 04:00:00.000+00', '2026-05-27 04:00:00.000+00', 1, 1, false, NULL, NULL, true),
(284501743746682880, 'Frequency', 'Frequency', '频次管理', 'lucide:alarm-clock', '/basic/frequency', '/basic/frequency/index', '', 2000, 9, true, false, false, '', 'menu', '', NULL, '2026-05-27 04:00:00.000+00', '2026-05-27 04:00:00.000+00', 1, 1, false, NULL, NULL, true),
(284502213630365696, 'Supplier', 'Supplier', '供应商管理', 'lucide:truck', '/basic/supplier', '/basic/supplier/index', '', 2000, 8, true, false, false, '', 'menu', '', NULL, '2026-05-27 04:00:00.000+00', '2026-05-27 04:00:00.000+00', 1, 1, false, NULL, NULL, true),
(284503862625177600, 'Warehouse', 'Warehouse', '库房管理', 'lucide:warehouse', '/inventory', '', '', NULL, 9, true, false, false, '', 'directory', '', NULL, '2026-05-27 04:00:00.000+00', '2026-05-27 04:00:00.000+00', 1, 1, false, NULL, NULL, true),
(284517877903003648, 'Medicine', 'Medicine', '药品管理', 'lucide:pill', '/basic/medicationcatalog', '/basic/medicationcatalog/index', '', 2000, 13, true, false, false, '', 'menu', '', NULL, '2026-05-27 04:00:00.000+00', '2026-05-27 04:00:00.000+00', 1, 1, false, NULL, NULL, true),
(284518561658441728, 'Device', 'Device', '器材管理', 'lucide:syringe', '/basic/devicecatalog', '/basic/devicecatalog/index', '', 2000, 16, true, false, false, '', 'menu', '', NULL, '2026-05-27 04:00:00.000+00', '2026-05-27 04:00:00.000+00', 1, 1, false, NULL, NULL, true),
(284519152342274048, 'Diagnosistreatment', 'Diagnosistreatment', '诊疗管理', 'lucide:square-activity', '/basic/servicecatalog', '/basic/servicecatalog/index', '', 2000, 15, true, false, false, '', 'menu', '', NULL, '2026-05-27 04:00:00.000+00', '2026-05-27 04:00:00.000+00', 1, 1, false, NULL, NULL, true),
(284519592148602880, 'Disease', 'Disease', '诊断', 'lucide:heart-pulse', '/basic/diagnosiscatalog', '/basic/diagnosiscatalog/index', '', 2000, 12, true, false, false, '', 'menu', '', NULL, '2026-05-27 04:00:00.000+00', '2026-05-27 04:00:00.000+00', 1, 1, false, NULL, NULL, true),
(284523631498235904, 'MedicineCatalogDetail', 'MedicineCatalogDetail', '药品详情', 'lucide:tablets', '/basic/medicationcatalog/:id', 'basic/medicationcatalog/[id]/index', '', 2000, 14, true, true, false, '', 'menu', '', NULL, '2026-05-27 04:00:00.000+00', '2026-05-27 04:00:00.000+00', 1, 1, false, NULL, NULL, true),
(301665359451131904, 'OutpatientNurseStation', 'OutpatientNurseStation', '门诊护士站', 'lucide:clipboard-list', '/outpatient/nurse-station', '/outpatient/nurse-station/index', '', NULL, 6, true, false, false, '', 'menu', '', NULL, '2026-05-27 04:00:00.000+00', '2026-05-27 04:00:00.000+00', 1, 1, false, NULL, NULL, true),
(301666596158443520, 'InpatientNurseStation', 'InpatientNurseStation', '住院护士站', 'lucide:bed-double', '/inpatient/nurse-station', '/inpatient/nurse-station/index', '', NULL, 7, true, false, false, '', 'menu', '', NULL, '2026-05-27 04:00:00.000+00', '2026-05-27 04:00:00.000+00', 1, 1, false, NULL, NULL, true),
(301696493912330240, 'Pharmacy', 'Pharmacy', '药房管理', 'lucide:store', '/pharmacy', '', '', NULL, 10, true, false, false, '', 'directory', '', 1, '2026-05-27 04:00:00.000+00', '2026-05-27 04:00:00.000+00', 1, 1, false, NULL, NULL, true),
(301699662818054144, 'ReportFinance', 'ReportFinance', '财务报表', 'lucide:receipt-text', '/reports/finance', '/reports/finance/index', '', 310400000000000100, 4, true, false, false, '', 'menu', '', 1, '2026-05-27 04:00:00.000+00', '2026-05-27 04:00:00.000+00', 1, 1, false, NULL, NULL, true),
(310400000000000001, 'ChargeStation', 'ChargeStation', '收费工作站', 'lucide:banknote', '/charge', '', '', NULL, 3, true, false, false, '', 'directory', 'HIS清单-收费工作站', NULL, '2026-05-27 04:00:00.000+00', '2026-05-27 04:00:00.000+00', 1, 1, false, NULL, NULL, true),
(310400000000000002, 'ChargeOpRegister', 'ChargeOpRegister', '门诊挂号', 'lucide:calendar-plus', '/outpatient/charge/register', '/outpatient/charge/register/index', '', 310400000000000001, 1, true, false, false, '', 'menu', '', NULL, '2026-05-27 04:00:00.000+00', '2026-05-27 04:00:00.000+00', 1, 1, false, NULL, NULL, true),
(310400000000000003, 'ChargeOpCharge', 'ChargeOpCharge', '门诊收费', 'lucide:circle-dollar-sign', '/outpatient/charge/charge', '/outpatient/charge/charge/index', '', 310400000000000001, 2, true, false, false, '', 'menu', '', NULL, '2026-05-27 04:00:00.000+00', '2026-05-27 04:00:00.000+00', 1, 1, false, NULL, NULL, true),
(310400000000000004, 'ChargeOpRefund', 'ChargeOpRefund', '门诊退费', 'lucide:undo-2', '/outpatient/charge/refund', '/outpatient/charge/refund/index', '', 310400000000000001, 3, true, false, false, '', 'menu', '', NULL, '2026-05-27 04:00:00.000+00', '2026-05-27 04:00:00.000+00', 1, 1, false, NULL, NULL, true),
(310400000000000005, 'ChargeOpDayClose', 'ChargeOpDayClose', '门诊日结', 'lucide:calendar-check', '/outpatient/charge/daily-settlement', '/outpatient/charge/daily-settlement/index', '', 310400000000000001, 4, true, false, false, '', 'menu', '', NULL, '2026-05-27 04:00:00.000+00', '2026-05-27 04:00:00.000+00', 1, 1, false, NULL, NULL, true),
(310400000000000006, 'ChargeIpRegister', 'ChargeIpRegister', '住院登记', 'lucide:bed-single', '/inpatient/charge/register', '/inpatient/charge/register/index', '', 310400000000000001, 5, true, false, false, '', 'menu', '', NULL, '2026-05-27 04:00:00.000+00', '2026-05-27 04:00:00.000+00', 1, 1, false, NULL, NULL, true),
(310400000000000007, 'ChargeIpSettle', 'ChargeIpSettle', '住院结算', 'lucide:wallet', '/inpatient/charge/settle', '/inpatient/charge/settle/index', '', 310400000000000001, 6, true, false, false, '', 'menu', '', NULL, '2026-05-27 04:00:00.000+00', '2026-05-27 04:00:00.000+00', 1, 1, false, NULL, NULL, true),
(310400000000000008, 'ChargeIpPrepay', 'ChargeIpPrepay', '预交金管理', 'lucide:coins', '/inpatient/charge/prepay', '/inpatient/charge/prepay/index', '', 310400000000000001, 7, true, false, false, '', 'menu', '', NULL, '2026-05-27 04:00:00.000+00', '2026-05-27 04:00:00.000+00', 1, 1, false, NULL, NULL, true),
(310400000000000009, 'ChargeIpFee', 'ChargeIpFee', '住院费用管理', 'lucide:list-todo', '/inpatient/charge/fee', '/inpatient/charge/fee/index', '', 310400000000000001, 8, true, false, false, '', 'menu', '', NULL, '2026-05-27 04:00:00.000+00', '2026-05-27 04:00:00.000+00', 1, 1, false, NULL, NULL, true),
(310400000000000020, 'OutpatientDoctorStation', 'OutpatientDoctorStation', '门诊医生站', 'lucide:user-round-pen', '/outpatient/doctor-station', '/outpatient/doctor-station/index', '', NULL, 4, true, false, false, '', 'menu', '', NULL, '2026-05-27 04:00:00.000+00', '2026-05-27 04:00:00.000+00', 1, 1, false, NULL, NULL, true),
(310400000000000040, 'InpatientDoctorStation', 'InpatientDoctorStation', '住院医生站', 'lucide:hospital', '/inpatient/doctor-station', '/inpatient/doctor-station/index', '', NULL, 5, true, false, false, '', 'menu', '', NULL, '2026-05-27 04:00:00.000+00', '2026-05-27 04:00:00.000+00', 1, 1, false, NULL, NULL, true),
(310400000000000060, 'MedicationDispense', 'MedicationDispense', '发药管理', 'lucide:pill-bottle', '/pharmacy/dispense', '', '', NULL, 8, true, false, false, '', 'directory', 'HIS清单-发药管理', NULL, '2026-05-27 04:00:00.000+00', '2026-05-27 04:00:00.000+00', 1, 1, false, NULL, NULL, true),
(310400000000000061, 'DispenseOp', 'DispenseOp', '门诊发药', 'lucide:package-open', '/pharmacy/dispense/outpatient', '/pharmacy/dispense/outpatient/index', '', 310400000000000060, 1, true, false, false, '', 'menu', '', NULL, '2026-05-27 04:00:00.000+00', '2026-05-27 04:00:00.000+00', 1, 1, false, NULL, NULL, true),
(310400000000000062, 'DispenseIp', 'DispenseIp', '住院发药', 'lucide:truck', '/pharmacy/dispense/inpatient', '/pharmacy/dispense/inpatient/index', '', 310400000000000060, 2, true, false, false, '', 'menu', '', NULL, '2026-05-27 04:00:00.000+00', '2026-05-27 04:00:00.000+00', 1, 1, false, NULL, NULL, true),
(310400000000000063, 'DispenseDoc', 'DispenseDoc', '发药单据', 'lucide:file-box', '/pharmacy/dispense/documents', '/pharmacy/dispense/documents/index', '', 310400000000000060, 3, true, false, false, '', 'menu', '', NULL, '2026-05-27 04:00:00.000+00', '2026-05-27 04:00:00.000+00', 1, 1, false, NULL, NULL, true),
(310400000000000081, 'WhPurchaseOrder', 'WhPurchaseOrder', '药库订货单', 'lucide:file-spreadsheet', '/inventory/warehouse/purchase-order', '/inventory/warehouse/purchase-order/index', '', 284503862625177600, 1, true, false, false, '', 'menu', '', NULL, '2026-05-27 04:00:00.000+00', '2026-05-27 04:00:00.000+00', 1, 1, false, NULL, NULL, true),
(310400000000000100, 'ReportCenter', 'ReportCenter', '报表管理', 'lucide:layout-dashboard', '/reports', '', '', NULL, 11, true, false, false, '', 'directory', 'HIS清单-报表管理', NULL, '2026-05-27 04:00:00.000+00', '2026-05-27 04:00:00.000+00', 1, 1, false, NULL, NULL, true),
(310400000000000101, 'ReportHome', 'ReportHome', '首页', 'lucide:home', '/reports/home', '/reports/home/index', '', 310400000000000100, 1, true, false, false, '', 'menu', '', NULL, '2026-05-27 04:00:00.000+00', '2026-05-27 04:00:00.000+00', 1, 1, false, NULL, NULL, true),
(310400000000000102, 'ReportDean', 'ReportDean', '院长', 'lucide:building-2', '/reports/dean', '/reports/dean/index', '', 310400000000000100, 2, true, false, false, '', 'menu', '', NULL, '2026-05-27 04:00:00.000+00', '2026-05-27 04:00:00.000+00', 1, 1, false, NULL, NULL, true),
(310400000000000103, 'ReportClinical', 'ReportClinical', '临床报表', 'lucide:activity', '/reports/clinical', '/reports/clinical/index', '', 310400000000000100, 3, true, false, false, '', 'menu', '', NULL, '2026-05-27 04:00:00.000+00', '2026-05-27 04:00:00.000+00', 1, 1, false, NULL, NULL, true),
(312881978679496704, 'WhPurchaseInbound', 'WhPurchaseInbound', '药库进货单', 'lucide:package-check', '/inventory/warehouse/purchase-inbound', '/inventory/warehouse/purchase-inbound/index', '', 284503862625177600, 2, true, false, false, '', 'menu', '', NULL, '2026-05-27 04:00:00.000+00', '2026-05-27 04:00:00.000+00', 1, 1, false, NULL, NULL, true),
(313234941086273536, 'WhStockOut', 'WhStockOut', '药库出库单', 'lucide:package-minus', '/inventory/warehouse/stock-out', '/inventory/warehouse/stock-out/index', '', 284503862625177600, 3, true, false, false, '', 'menu', '', NULL, '2026-05-27 04:00:00.000+00', '2026-05-27 04:00:00.000+00', 1, 1, false, NULL, NULL, true),
(313295483593297921, 'PhStockIn', 'PhStockIn', '药房入库单', 'lucide:package-plus', '/inventory/pharmacy/stock-in', '/inventory/pharmacy/stock-in/index', '', 301696493912330240, 1, true, false, false, '', 'menu', '', 1, '2026-05-27 04:00:00.000+00', '2026-05-27 04:00:00.000+00', 1, 1, false, NULL, NULL, true),
(313570119616827392, 'InvStockList', 'InvStockList', '库存明细', 'lucide:rows-3', '/inventory/stock-list', '/inventory/stock-list/index', '', 284503862625177600, 5, true, false, false, '', 'menu', '', NULL, '2026-05-27 04:00:00.000+00', '2026-05-27 04:00:00.000+00', 1, 1, false, NULL, NULL, true),
(313945127476793344, 'MedicineReturnOp', 'MedicineReturnOp', '门诊退药', 'lucide:truck', '/pharmacy/return-medicine/outpatient', '/pharmacy/return-medicine/outpatient/index', '', 310400000000000060, 4, true, false, false, '', 'menu', '', NULL, '2026-05-27 04:00:00.000+00', '2026-05-27 05:13:33.351+00', 1, 1, false, NULL, NULL, true),
(315372803691384832, 'EmrTemplate', 'EmrTemplate', '病历模版', 'lucide:file-text', '/basic/emr-template', '/basic/emr-template/index', '', 2000, 18, true, false, false, '', 'menu', '', NULL, '2026-05-27 04:00:00.000+00', '2026-05-27 04:00:00.000+00', 1, 1, false, NULL, NULL, true),
(316800463887929340, 'WhStocktakeOrder', 'WhStocktakeOrder', '药库盘点单', 'lucide:clipboard-list', '/inventory/warehouse/stocktake', '/inventory/warehouse/stocktake/index', '', 284503862625177600, 4, true, false, false, '', 'menu', '', NULL, '2026-05-27 04:00:00.000+00', '2026-05-27 04:00:00.000+00', 1, 1, false, NULL, NULL, true),
(316800463887929341, 'PhStocktakeOrder', 'PhStocktakeOrder', '药房盘点单', 'lucide:clipboard-check', '/inventory/pharmacy/stocktake', '/inventory/pharmacy/stocktake/index', '', 301696493912330240, 2, true, false, false, '', 'menu', '', 1, '2026-05-27 04:00:00.000+00', '2026-05-27 04:00:00.000+00', 1, 1, false, NULL, NULL, true),
(316900463887929345, 'DeviceCatalogDetail', 'DeviceCatalogDetail', '器材详情', 'lucide:package', '/basic/devicecatalog/:id', 'basic/devicecatalog/[id]/index', '', 2000, 17, true, true, false, '', 'menu', '', NULL, '2026-05-27 04:00:00.000+00', '2026-05-27 04:00:00.000+00', 1, 1, false, NULL, NULL, true),
(316900463887929346, 'OrderBinding', 'OrderBinding', '医嘱绑定', 'lucide:link-2', '/basic/orderbinding', '/basic/orderbinding/index', '', 2000, 19, true, false, false, '', 'menu', '', NULL, '2026-05-27 04:00:00.000+00', '2026-05-27 04:00:00.000+00', 1, 1, false, NULL, NULL, true),
(316900463887929347, 'ExecuteLocation', 'ExecuteLocation', '执行地点', 'lucide:map-pin', '/basic/executelocation', '/basic/executelocation/index', '', 2000, 20, true, false, false, '', 'menu', '', NULL, '2026-05-27 04:00:00.000+00', '2026-05-27 04:00:00.000+00', 1, 1, false, NULL, NULL, true)
ON CONFLICT ("id") DO NOTHING;


-- ========== whale_sequence ==========
CREATE TABLE IF NOT EXISTS whale_sequence (
    seq_key       VARCHAR(255) NOT NULL PRIMARY KEY,
    current_value BIGINT      NOT NULL DEFAULT 0,
    updated_at    TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_whale_sequence_updated_at ON whale_sequence(updated_at);

COMMENT ON TABLE whale_sequence IS '业务序号表，按 key 记录当前序号';

COMMENT ON COLUMN whale_sequence.seq_key IS '序号键（如 ORDER、PATIENT、或 ORDER:2025-01-30 表示按天）';

COMMENT ON COLUMN whale_sequence.current_value IS '当前序号值（递增后返回）';

COMMENT ON COLUMN whale_sequence.updated_at IS '最后更新时间';

-- seed: whale_sequence
INSERT INTO "whale_sequence" ("seq_key", "current_value", "updated_at")
VALUES
('1:AC:2026-05-03', 1, '2026-05-03 06:31:45.342+00'),
('1:AC:2026-05-04', 1, '2026-05-04 09:32:24.679+00'),
('1:AC:2026-05-05', 1, '2026-05-05 05:41:00.299+00'),
('1:AC:2026-05-08', 2, '2026-05-08 12:11:33.842+00'),
('1:AC:2026-05-09', 2, '2026-05-09 12:20:44.174+00'),
('1:AC:2026-05-10', 4, '2026-05-10 11:18:15.499+00'),
('1:AC:2026-05-15', 2, '2026-05-15 11:54:56.548+00'),
('1:AC:2026-05-16', 1, '2026-05-16 08:51:40.557+00'),
('1:AC:2026-05-19', 5, '2026-05-19 11:30:37.957+00'),
('1:AC:2026-05-20', 18, '2026-05-20 12:54:52.499+00'),
('1:AC:2026-05-22', 5, '2026-05-22 09:11:53.852+00'),
('1:AC:2026-05-24', 9, '2026-05-24 13:55:24.237+00'),
('1:AC:2026-05-27', 7, '2026-05-27 11:34:57.986+00'),
('1:AC:2026-05-28', 5, '2026-05-28 10:39:49.731+00'),
('1:AC:2026-05-29', 3, '2026-05-29 07:59:16.296+00'),
('1:AD', 10, '2026-05-29 05:07:19.618+00'),
('1:CD:2026-05-03', 22, '2026-05-03 12:51:35.129+00'),
('1:CD:2026-05-04', 1, '2026-05-04 10:10:35.149+00'),
('1:CD:2026-05-05', 2, '2026-05-05 12:26:29.943+00'),
('1:CD:2026-05-08', 2, '2026-05-08 12:11:48.380+00'),
('1:CD:2026-05-09', 2, '2026-05-09 12:20:56.026+00'),
('1:CD:2026-05-10', 6, '2026-05-10 11:18:39.904+00'),
('1:CD:2026-05-15', 8, '2026-05-15 12:21:24.128+00'),
('1:CD:2026-05-16', 1, '2026-05-16 08:51:54.236+00'),
('1:CD:2026-05-19', 6, '2026-05-19 11:31:01.730+00'),
('1:CD:2026-05-20', 16, '2026-05-20 12:32:54.068+00'),
('1:CD:2026-05-22', 4, '2026-05-22 08:13:12.912+00'),
('1:CD:2026-05-24', 8, '2026-05-24 13:57:23.047+00'),
('1:CD:2026-05-27', 5, '2026-05-27 11:34:57.986+00'),
('1:CD:2026-05-28', 17, '2026-05-28 10:39:49.731+00'),
('1:CD:2026-05-29', 3, '2026-05-29 07:59:26.814+00'),
('1:CDF', 3, '2026-05-27 07:48:32.110+00'),
('1:CI:2026-05-03', 1, '2026-05-03 06:31:45.342+00'),
('1:CI:2026-05-04', 4, '2026-05-04 10:56:07.865+00'),
('1:CI:2026-05-05', 9, '2026-05-05 12:27:54.419+00'),
('1:CI:2026-05-08', 5, '2026-05-08 12:13:49.228+00'),
('1:CI:2026-05-09', 18, '2026-05-09 12:21:04.456+00'),
('1:CI:2026-05-10', 27, '2026-05-10 11:22:50.898+00'),
('1:CI:2026-05-15', 18, '2026-05-15 13:05:37.282+00'),
('1:CI:2026-05-16', 3, '2026-05-16 08:56:33.587+00'),
('1:CI:2026-05-19', 6, '2026-05-19 11:36:54.153+00'),
('1:CI:2026-05-20', 24, '2026-05-20 12:38:29.417+00'),
('1:CI:2026-05-21', 3, '2026-05-21 11:34:32.117+00'),
('1:CI:2026-05-22', 5, '2026-05-22 12:43:13.924+00'),
('1:CI:2026-05-24', 18, '2026-05-24 13:55:24.237+00'),
('1:CI:2026-05-27', 22, '2026-05-27 11:39:21.679+00'),
('1:CI:2026-05-28', 9, '2026-05-28 10:41:18.268+00'),
('1:CI:2026-05-29', 26, '2026-05-29 08:29:53.616+00'),
('1:CIR:2026-05-15', 6, '2026-05-15 12:56:23.926+00'),
('1:CIR:2026-05-20', 3, '2026-05-20 12:29:29.202+00'),
('1:CIR:2026-05-22', 1, '2026-05-22 09:18:14.236+00'),
('1:CIR:2026-05-27', 2, '2026-05-27 08:30:27.356+00'),
('1:CIR:2026-05-28', 2, '2026-05-28 07:51:00.209+00'),
('1:CIR:2026-05-29', 10, '2026-05-29 07:37:48.114+00'),
('1:CSO:2026-05-15', 2, '2026-05-15 13:03:57.095+00'),
('1:CSO:2026-05-20', 2, '2026-05-20 12:30:39.470+00'),
('1:CSO:2026-05-22', 1, '2026-05-22 09:19:12.473+00'),
('1:CSO:2026-05-27', 3, '2026-05-27 08:31:25.023+00'),
('1:CSO:2026-05-28', 2, '2026-05-28 07:55:17.631+00'),
('1:CSO:2026-05-29', 2, '2026-05-29 07:46:45.745+00'),
('1:DD', 6, '2026-05-29 05:18:15.535+00'),
('1:DDP:2026-05-28', 1, '2026-05-28 08:08:34.055+00'),
('1:DDP:2026-05-29', 3, '2026-05-29 08:06:26.317+00'),
('1:DR:2026-05-28', 1, '2026-05-28 08:08:34.055+00'),
('1:DR:2026-05-29', 3, '2026-05-29 08:06:26.317+00'),
('1:EN:2026-05-03', 1, '2026-05-03 06:31:45.342+00'),
('1:EN:2026-05-04', 1, '2026-05-04 09:32:24.679+00'),
('1:EN:2026-05-05', 1, '2026-05-05 05:41:00.299+00'),
('1:EN:2026-05-08', 2, '2026-05-08 12:11:33.842+00'),
('1:EN:2026-05-09', 2, '2026-05-09 12:20:44.174+00'),
('1:EN:2026-05-10', 4, '2026-05-10 11:18:15.499+00'),
('1:EN:2026-05-15', 2, '2026-05-15 11:54:56.548+00'),
('1:EN:2026-05-16', 1, '2026-05-16 08:51:40.557+00'),
('1:EN:2026-05-19', 4, '2026-05-19 11:30:37.957+00'),
('1:EN:2026-05-20', 7, '2026-05-20 12:13:40.095+00'),
('1:EN:2026-05-22', 1, '2026-05-22 09:11:53.852+00'),
('1:EN:2026-05-24', 2, '2026-05-24 13:55:24.237+00'),
('1:EN:2026-05-27', 4, '2026-05-27 08:46:33.779+00'),
('1:EN:2026-05-28', 2, '2026-05-28 06:12:10.662+00'),
('1:EN:2026-05-29', 2, '2026-05-29 07:59:16.296+00'),
('1:IV:2026-05-03', 1, '2026-05-03 06:31:46.350+00'),
('1:IV:2026-05-04', 1, '2026-05-04 09:32:26.406+00'),
('1:IV:2026-05-05', 1, '2026-05-05 05:41:01.557+00'),
('1:IV:2026-05-08', 3, '2026-05-08 12:51:01.826+00'),
('1:IV:2026-05-09', 4, '2026-05-09 12:21:14.810+00'),
('1:IV:2026-05-10', 8, '2026-05-10 11:18:16.958+00'),
('1:IV:2026-05-15', 4, '2026-05-15 13:05:45.828+00'),
('1:IV:2026-05-16', 2, '2026-05-16 08:56:43.462+00'),
('1:IV:2026-05-19', 3, '2026-05-19 11:30:39.903+00'),
('1:IV:2026-05-20', 11, '2026-05-20 12:33:04.318+00'),
('1:IV:2026-05-22', 1, '2026-05-22 09:11:54.789+00'),
('1:IV:2026-05-24', 2, '2026-05-24 13:55:26.038+00'),
('1:IV:2026-05-27', 6, '2026-05-27 08:51:36.377+00'),
('1:IV:2026-05-28', 5, '2026-05-28 10:42:45.342+00'),
('1:IV:2026-05-29', 6, '2026-05-29 08:17:56.087+00'),
('1:LO', 17, '2026-05-24 12:58:53.673+00'),
('1:MD', 19, '2026-05-29 05:41:33.256+00'),
('1:MDP:2026-05-09', 6, '2026-05-09 12:21:04.456+00'),
('1:MDP:2026-05-10', 24, '2026-05-10 11:18:55.639+00'),
('1:MDP:2026-05-15', 20, '2026-05-15 13:05:37.282+00')
ON CONFLICT ("seq_key") DO NOTHING;

INSERT INTO "whale_sequence" ("seq_key", "current_value", "updated_at")
VALUES
('1:MDP:2026-05-16', 3, '2026-05-16 08:57:10.339+00'),
('1:MDP:2026-05-20', 13, '2026-05-20 12:32:57.357+00'),
('1:MDP:2026-05-21', 2, '2026-05-21 11:34:23.785+00'),
('1:MDP:2026-05-22', 4, '2026-05-22 12:43:13.924+00'),
('1:MDP:2026-05-24', 6, '2026-05-24 13:00:12.125+00'),
('1:MDP:2026-05-27', 14, '2026-05-27 11:39:21.679+00'),
('1:MDP:2026-05-28', 5, '2026-05-28 10:41:18.268+00'),
('1:MDP:2026-05-29', 15, '2026-05-29 08:15:55.851+00'),
('1:MR:2026-05-04', 15, '2026-05-04 11:00:46.045+00'),
('1:MR:2026-05-05', 17, '2026-05-05 12:28:03.158+00'),
('1:MR:2026-05-08', 9, '2026-05-08 12:21:46.430+00'),
('1:MR:2026-05-09', 25, '2026-05-09 12:21:04.456+00'),
('1:MR:2026-05-10', 35, '2026-05-10 11:22:52.972+00'),
('1:MR:2026-05-15', 19, '2026-05-15 13:05:37.282+00'),
('1:MR:2026-05-16', 7, '2026-05-16 08:57:10.339+00'),
('1:MR:2026-05-19', 2, '2026-05-19 11:36:54.153+00'),
('1:MR:2026-05-20', 22, '2026-05-20 12:32:57.357+00'),
('1:MR:2026-05-21', 7, '2026-05-21 11:34:33.923+00'),
('1:MR:2026-05-22', 17, '2026-05-22 12:38:25.323+00'),
('1:MR:2026-05-24', 8, '2026-05-24 13:00:12.125+00'),
('1:MR:2026-05-27', 31, '2026-05-27 11:37:17.860+00'),
('1:MR:2026-05-28', 15, '2026-05-28 10:41:57.235+00'),
('1:MR:2026-05-29', 22, '2026-05-29 08:29:54.818+00'),
('1:PA', 20, '2026-05-27 08:31:43.938+00'),
('1:PSI:2026-05-15', 3, '2026-05-15 13:03:58.358+00'),
('1:PSI:2026-05-20', 2, '2026-05-20 12:30:43.664+00'),
('1:PSI:2026-05-22', 1, '2026-05-22 09:19:13.157+00'),
('1:PSI:2026-05-27', 2, '2026-05-27 08:31:26.134+00'),
('1:PSI:2026-05-28', 2, '2026-05-28 07:55:18.402+00'),
('1:PSI:2026-05-29', 2, '2026-05-29 07:46:47.181+00'),
('1:PST:2026-05-24', 1, '2026-05-24 05:45:21.992+00'),
('1:PYR', 111, '2026-05-29 08:17:50.691+00'),
('1:SQ:2026-05-13', 2, '2026-05-13 12:58:04.191+00'),
('1:SQ:2026-05-14', 3, '2026-05-14 07:11:41.554+00'),
('1:SR:2026-05-10', 6, '2026-05-10 11:22:50.898+00'),
('1:SR:2026-05-15', 1, '2026-05-15 12:25:41.337+00'),
('1:SR:2026-05-20', 3, '2026-05-20 12:38:29.417+00'),
('1:SR:2026-05-21', 1, '2026-05-21 11:34:32.117+00'),
('1:SR:2026-05-24', 13, '2026-05-24 13:03:20.902+00'),
('1:SR:2026-05-28', 6, '2026-05-28 10:41:55.847+00'),
('1:SR:2026-05-29', 6, '2026-05-29 08:29:53.616+00'),
('1:ZY:2026-05-19', 1, '2026-05-19 10:56:35.387+00'),
('1:ZY:2026-05-20', 13, '2026-05-20 12:54:44.283+00'),
('1:ZY:2026-05-22', 4, '2026-05-22 08:13:12.912+00'),
('1:ZY:2026-05-24', 7, '2026-05-24 12:59:08.642+00'),
('1:ZY:2026-05-27', 3, '2026-05-27 11:34:57.986+00'),
('1:ZY:2026-05-28', 3, '2026-05-28 10:39:49.731+00'),
('1:ZY:2026-05-29', 1, '2026-05-29 07:01:37.777+00')
ON CONFLICT ("seq_key") DO NOTHING;


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
