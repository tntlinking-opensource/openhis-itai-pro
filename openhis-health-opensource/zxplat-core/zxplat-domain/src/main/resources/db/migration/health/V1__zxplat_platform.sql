-- Squashed zxplat_* platform DDL (V1 + V68 + V70)

CREATE TABLE IF NOT EXISTS zxplat_users (
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

CREATE INDEX IF NOT EXISTS idx_users_email ON zxplat_users(email);

CREATE UNIQUE INDEX IF NOT EXISTS idx_users_user_name_unique ON zxplat_users(user_name) WHERE is_deleted = false;

CREATE UNIQUE INDEX IF NOT EXISTS idx_users_email_unique ON zxplat_users(email) WHERE email IS NOT NULL AND is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_zxplat_users_is_deleted ON zxplat_users(is_deleted);

CREATE INDEX IF NOT EXISTS idx_zxplat_users_is_active ON zxplat_users(is_active) WHERE is_active = true;

CREATE INDEX IF NOT EXISTS idx_zxplat_users_is_locked_out ON zxplat_users(is_locked_out) WHERE is_locked_out = true;

CREATE INDEX IF NOT EXISTS idx_zxplat_users_created_at ON zxplat_users(created_at DESC);

CREATE INDEX IF NOT EXISTS idx_users_ukey_id ON zxplat_users(ukey_id);

CREATE INDEX IF NOT EXISTS idx_users_ukey_src_id ON zxplat_users(ukey_src_id);

COMMENT ON TABLE zxplat_users IS '系统用户';

COMMENT ON COLUMN zxplat_users.id IS '主键';

COMMENT ON COLUMN zxplat_users.user_name IS '登录用户名';

COMMENT ON COLUMN zxplat_users.email IS '邮箱';

COMMENT ON COLUMN zxplat_users.phone_number IS '手机号';

COMMENT ON COLUMN zxplat_users.password_hash IS '密码哈希';

COMMENT ON COLUMN zxplat_users.name IS '名';

COMMENT ON COLUMN zxplat_users.surname IS '姓';

COMMENT ON COLUMN zxplat_users.is_active IS '是否启用';

COMMENT ON COLUMN zxplat_users.is_locked_out IS '是否锁定';

COMMENT ON COLUMN zxplat_users.lockout_end IS '锁定结束时间';

COMMENT ON COLUMN zxplat_users.access_failed_count IS '登录失败次数';

COMMENT ON COLUMN zxplat_users.extra_properties IS '扩展属性 JSON';

COMMENT ON COLUMN zxplat_users.created_at IS '创建时间';

COMMENT ON COLUMN zxplat_users.updated_at IS '最后更新时间';

COMMENT ON COLUMN zxplat_users.created_by IS '创建人';

COMMENT ON COLUMN zxplat_users.updated_by IS '最后更新人';

COMMENT ON COLUMN zxplat_users.is_deleted IS '软删标记';

COMMENT ON COLUMN zxplat_users.deleted_at IS '删除时间';

COMMENT ON COLUMN zxplat_users.deleted_by IS '删除人';

COMMENT ON COLUMN zxplat_users.ukey_id IS 'UKey 标识';

COMMENT ON COLUMN zxplat_users.ukey_src_id IS 'UKey 来源标识';

-- seed: zxplat_users

CREATE TABLE IF NOT EXISTS zxplat_roles (
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

CREATE INDEX IF NOT EXISTS idx_zxplat_roles_code ON zxplat_roles(code);

CREATE INDEX IF NOT EXISTS idx_zxplat_roles_tenant_id ON zxplat_roles(tenant_id);

CREATE INDEX IF NOT EXISTS idx_zxplat_roles_is_deleted ON zxplat_roles(is_deleted);

CREATE INDEX IF NOT EXISTS idx_zxplat_roles_is_default ON zxplat_roles(is_default) WHERE is_default = true;

CREATE INDEX IF NOT EXISTS idx_zxplat_roles_is_static ON zxplat_roles(is_static) WHERE is_static = true;

CREATE INDEX IF NOT EXISTS idx_zxplat_roles_created_at ON zxplat_roles(created_at DESC);

-- seed: zxplat_roles

CREATE TABLE IF NOT EXISTS zxplat_user_roles (
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

CREATE INDEX IF NOT EXISTS idx_zxplat_user_roles_role_id ON zxplat_user_roles(role_id);

CREATE INDEX IF NOT EXISTS idx_zxplat_user_roles_tenant_id ON zxplat_user_roles(tenant_id);

CREATE INDEX IF NOT EXISTS idx_zxplat_user_roles_is_deleted ON zxplat_user_roles(is_deleted);

-- seed: zxplat_user_roles

CREATE TABLE IF NOT EXISTS zxplat_permissions (
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

CREATE INDEX IF NOT EXISTS idx_zxplat_permissions_parent_name ON zxplat_permissions(parent_name);

CREATE INDEX IF NOT EXISTS idx_zxplat_permissions_tenant_id ON zxplat_permissions(tenant_id);

CREATE INDEX IF NOT EXISTS idx_zxplat_permissions_is_deleted ON zxplat_permissions(is_deleted);

-- seed: zxplat_permissions

CREATE TABLE IF NOT EXISTS zxplat_permission_grants (
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

CREATE INDEX IF NOT EXISTS idx_zxplat_permission_grants_provider ON zxplat_permission_grants(provider_name, provider_key);

CREATE INDEX IF NOT EXISTS idx_zxplat_permission_grants_tenant_id ON zxplat_permission_grants(tenant_id);

CREATE INDEX IF NOT EXISTS idx_zxplat_permission_grants_is_deleted ON zxplat_permission_grants(is_deleted);

CREATE INDEX IF NOT EXISTS idx_zxplat_permission_grants_provider_lookup ON zxplat_permission_grants(provider_name, provider_key, permission_name);

-- seed: zxplat_permission_grants

CREATE TABLE IF NOT EXISTS zxplat_security_logs (
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

CREATE INDEX IF NOT EXISTS idx_zxplat_security_logs_action ON zxplat_security_logs(action);

CREATE INDEX IF NOT EXISTS idx_zxplat_security_logs_created_at ON zxplat_security_logs(created_at);

CREATE INDEX IF NOT EXISTS idx_zxplat_security_logs_tenant_id ON zxplat_security_logs(tenant_id);

CREATE INDEX IF NOT EXISTS idx_zxplat_security_logs_is_deleted ON zxplat_security_logs(is_deleted);

CREATE INDEX IF NOT EXISTS idx_zxplat_security_logs_user_action ON zxplat_security_logs(user_id, action, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_zxplat_security_logs_result ON zxplat_security_logs(result) WHERE result = 'FAILED';

-- (empty) no rows in zxplat_security_logs

-- ========== zxplat_organization_units ==========

CREATE TABLE IF NOT EXISTS zxplat_organization_units (
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
    CONSTRAINT fk_zxplat_ou_parent FOREIGN KEY (parent_id) REFERENCES zxplat_organization_units(id)
);

CREATE INDEX IF NOT EXISTS idx_zxplat_ou_parent_id ON zxplat_organization_units(parent_id);

CREATE INDEX IF NOT EXISTS idx_zxplat_ou_tenant_id ON zxplat_organization_units(tenant_id);

-- seed: zxplat_organization_units

CREATE TABLE IF NOT EXISTS zxplat_user_organization_units (
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
    CONSTRAINT fk_zxplat_uou_user FOREIGN KEY (user_id) REFERENCES zxplat_users(id),
    CONSTRAINT fk_zxplat_uou_ou FOREIGN KEY (organization_unit_id) REFERENCES zxplat_organization_units(id),
    CONSTRAINT uk_zxplat_uou_user_ou UNIQUE (user_id, organization_unit_id)
);

CREATE INDEX IF NOT EXISTS idx_zxplat_uou_ou_id ON zxplat_user_organization_units(organization_unit_id);

CREATE INDEX IF NOT EXISTS idx_zxplat_uou_tenant_id ON zxplat_user_organization_units(tenant_id);

-- seed: zxplat_user_organization_units

CREATE TABLE IF NOT EXISTS zxplat_user_claims (
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
    CONSTRAINT fk_zxplat_uc_user FOREIGN KEY (user_id) REFERENCES zxplat_users(id),
    CONSTRAINT uk_zxplat_uc_user_type_value UNIQUE (user_id, claim_type, claim_value)
);

CREATE INDEX IF NOT EXISTS idx_zxplat_uc_claim_type ON zxplat_user_claims(claim_type);

CREATE INDEX IF NOT EXISTS idx_zxplat_uc_tenant_id ON zxplat_user_claims(tenant_id);

-- ========== zxplat_role_claims ==========

CREATE TABLE IF NOT EXISTS zxplat_role_claims (
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
    CONSTRAINT fk_zxplat_rc_role FOREIGN KEY (role_id) REFERENCES zxplat_roles(id),
    CONSTRAINT uk_zxplat_rc_role_type_value UNIQUE (role_id, claim_type, claim_value)
);

CREATE INDEX IF NOT EXISTS idx_zxplat_rc_claim_type ON zxplat_role_claims(claim_type);

CREATE INDEX IF NOT EXISTS idx_zxplat_rc_tenant_id ON zxplat_role_claims(tenant_id);

-- seed: zxplat_role_claims

CREATE TABLE IF NOT EXISTS zxplat_qrtz_locks (
    sched_name VARCHAR(120) NOT NULL,
    lock_name VARCHAR(40) NOT NULL,
    PRIMARY KEY (sched_name, lock_name)
);

CREATE TABLE IF NOT EXISTS zxplat_qrtz_job_details (
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

CREATE INDEX IF NOT EXISTS idx_zxplat_qrtz_j_grp ON zxplat_qrtz_job_details(sched_name, job_group);

-- ========== zxplat_qrtz_triggers ==========

CREATE TABLE IF NOT EXISTS zxplat_qrtz_triggers (
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
        REFERENCES zxplat_qrtz_job_details(sched_name, job_name, job_group)
);

CREATE INDEX IF NOT EXISTS idx_zxplat_qrtz_t_j ON zxplat_qrtz_triggers(sched_name, job_name, job_group);

CREATE INDEX IF NOT EXISTS idx_zxplat_qrtz_t_jg ON zxplat_qrtz_triggers(sched_name, job_group);

CREATE INDEX IF NOT EXISTS idx_zxplat_qrtz_t_c ON zxplat_qrtz_triggers(sched_name, calendar_name);

CREATE INDEX IF NOT EXISTS idx_zxplat_qrtz_t_g ON zxplat_qrtz_triggers(sched_name, trigger_group);

CREATE INDEX IF NOT EXISTS idx_zxplat_qrtz_t_state ON zxplat_qrtz_triggers(sched_name, trigger_state);

CREATE INDEX IF NOT EXISTS idx_zxplat_qrtz_t_n_state ON zxplat_qrtz_triggers(sched_name, trigger_name, trigger_group, trigger_state);

CREATE INDEX IF NOT EXISTS idx_zxplat_qrtz_t_n_g_state ON zxplat_qrtz_triggers(sched_name, trigger_group, trigger_state);

CREATE INDEX IF NOT EXISTS idx_zxplat_qrtz_t_next_fire_time ON zxplat_qrtz_triggers(sched_name, next_fire_time);

CREATE INDEX IF NOT EXISTS idx_zxplat_qrtz_t_nft_st ON zxplat_qrtz_triggers(sched_name, trigger_state, next_fire_time);

CREATE INDEX IF NOT EXISTS idx_zxplat_qrtz_t_nft_misfire ON zxplat_qrtz_triggers(sched_name, misfire_instr, next_fire_time);

CREATE INDEX IF NOT EXISTS idx_zxplat_qrtz_t_nft_st_misfire ON zxplat_qrtz_triggers(sched_name, misfire_instr, next_fire_time, trigger_state);

-- ========== zxplat_qrtz_cron_triggers ==========

CREATE TABLE IF NOT EXISTS zxplat_qrtz_cron_triggers (
    sched_name VARCHAR(120) NOT NULL,
    trigger_name VARCHAR(200) NOT NULL,
    trigger_group VARCHAR(200) NOT NULL,
    cron_expression VARCHAR(120) NOT NULL,
    time_zone_id VARCHAR(80),
    PRIMARY KEY (sched_name, trigger_name, trigger_group),
    FOREIGN KEY (sched_name, trigger_name, trigger_group)
        REFERENCES zxplat_qrtz_triggers(sched_name, trigger_name, trigger_group)
);

CREATE TABLE IF NOT EXISTS zxplat_qrtz_simple_triggers (
    sched_name VARCHAR(120) NOT NULL,
    trigger_name VARCHAR(200) NOT NULL,
    trigger_group VARCHAR(200) NOT NULL,
    repeat_count BIGINT NOT NULL,
    repeat_interval BIGINT NOT NULL,
    times_triggered BIGINT NOT NULL,
    PRIMARY KEY (sched_name, trigger_name, trigger_group),
    FOREIGN KEY (sched_name, trigger_name, trigger_group)
        REFERENCES zxplat_qrtz_triggers(sched_name, trigger_name, trigger_group)
);

CREATE TABLE IF NOT EXISTS zxplat_qrtz_blob_triggers (
    sched_name VARCHAR(120) NOT NULL,
    trigger_name VARCHAR(200) NOT NULL,
    trigger_group VARCHAR(200) NOT NULL,
    blob_data BYTEA,
    PRIMARY KEY (sched_name, trigger_name, trigger_group),
    FOREIGN KEY (sched_name, trigger_name, trigger_group)
        REFERENCES zxplat_qrtz_triggers(sched_name, trigger_name, trigger_group)
);

CREATE TABLE IF NOT EXISTS zxplat_qrtz_calendars (
    sched_name VARCHAR(120) NOT NULL,
    calendar_name VARCHAR(200) NOT NULL,
    calendar BYTEA NOT NULL,
    PRIMARY KEY (sched_name, calendar_name)
);

CREATE TABLE IF NOT EXISTS zxplat_qrtz_paused_trigger_grps (
    sched_name VARCHAR(120) NOT NULL,
    trigger_group VARCHAR(200) NOT NULL,
    PRIMARY KEY (sched_name, trigger_group)
);

CREATE TABLE IF NOT EXISTS zxplat_qrtz_scheduler_state (
    sched_name VARCHAR(120) NOT NULL,
    instance_name VARCHAR(200) NOT NULL,
    last_checkin_time BIGINT NOT NULL,
    checkin_interval BIGINT NOT NULL,
    PRIMARY KEY (sched_name, instance_name)
);

CREATE TABLE IF NOT EXISTS zxplat_qrtz_fired_triggers (
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

CREATE INDEX IF NOT EXISTS idx_zxplat_qrtz_ft_trig_inst_name ON zxplat_qrtz_fired_triggers(sched_name, instance_name);

CREATE INDEX IF NOT EXISTS idx_zxplat_qrtz_ft_inst_job_req_rcvry ON zxplat_qrtz_fired_triggers(sched_name, instance_name, requests_recovery);

CREATE INDEX IF NOT EXISTS idx_zxplat_qrtz_ft_j_g ON zxplat_qrtz_fired_triggers(sched_name, job_name, job_group);

CREATE INDEX IF NOT EXISTS idx_zxplat_qrtz_ft_jg ON zxplat_qrtz_fired_triggers(sched_name, job_group);

CREATE INDEX IF NOT EXISTS idx_zxplat_qrtz_ft_t_g ON zxplat_qrtz_fired_triggers(sched_name, trigger_name, trigger_group);

CREATE INDEX IF NOT EXISTS idx_zxplat_qrtz_ft_tg ON zxplat_qrtz_fired_triggers(sched_name, trigger_group);

-- ========== zxplat_tenants ==========

CREATE TABLE IF NOT EXISTS zxplat_tenants (
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

CREATE INDEX IF NOT EXISTS idx_zxplat_tenants_code ON zxplat_tenants(code);

CREATE INDEX IF NOT EXISTS idx_zxplat_tenants_is_active ON zxplat_tenants(is_active);

CREATE INDEX IF NOT EXISTS idx_zxplat_tenants_created_at ON zxplat_tenants(created_at);

CREATE INDEX IF NOT EXISTS idx_zxplat_tenants_is_deleted ON zxplat_tenants(is_deleted);

-- seed: zxplat_tenants

CREATE TABLE IF NOT EXISTS zxplat_user_tenants (
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

CREATE INDEX IF NOT EXISTS idx_zxplat_user_tenants_user_id ON zxplat_user_tenants(user_id);

CREATE INDEX IF NOT EXISTS idx_zxplat_user_tenants_tenant_id ON zxplat_user_tenants(tenant_id);

CREATE INDEX IF NOT EXISTS idx_zxplat_user_tenants_is_deleted ON zxplat_user_tenants(is_deleted);

-- seed: zxplat_user_tenants

CREATE TABLE IF NOT EXISTS zxplat_settings (
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

CREATE INDEX IF NOT EXISTS idx_zxplat_settings_provider_name ON zxplat_settings(provider_name);

CREATE INDEX IF NOT EXISTS idx_zxplat_settings_provider_key ON zxplat_settings(provider_key);

CREATE INDEX IF NOT EXISTS idx_zxplat_settings_tenant_id ON zxplat_settings(tenant_id);

CREATE INDEX IF NOT EXISTS idx_zxplat_settings_created_at ON zxplat_settings(created_at);

CREATE INDEX IF NOT EXISTS idx_zxplat_settings_is_deleted ON zxplat_settings(is_deleted);

-- seed: zxplat_settings

CREATE TABLE IF NOT EXISTS zxplat_menus (
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
    CONSTRAINT fk_zxplat_menus_parent FOREIGN KEY (parent_id) REFERENCES zxplat_menus(id)
);

CREATE INDEX IF NOT EXISTS idx_zxplat_menus_parent_id ON zxplat_menus(parent_id);

CREATE INDEX IF NOT EXISTS idx_zxplat_menus_tenant_id ON zxplat_menus(tenant_id);

CREATE INDEX IF NOT EXISTS idx_zxplat_menus_menu_type ON zxplat_menus(menu_type);

CREATE INDEX IF NOT EXISTS idx_zxplat_menus_is_enabled ON zxplat_menus(is_enabled);

-- seed: zxplat_menus

CREATE TABLE IF NOT EXISTS zxplat_sequence (
    seq_key       VARCHAR(255) NOT NULL PRIMARY KEY,
    current_value BIGINT      NOT NULL DEFAULT 0,
    updated_at    TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_zxplat_sequence_updated_at ON zxplat_sequence(updated_at);

-- (empty) 业务序号由运行时递增，不预置

-- ========== zxplat_audit_logs ==========

CREATE TABLE IF NOT EXISTS zxplat_audit_logs (
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

CREATE INDEX IF NOT EXISTS idx_audit_logs_entity_type ON zxplat_audit_logs(entity_type);

CREATE INDEX IF NOT EXISTS idx_audit_logs_entity_id ON zxplat_audit_logs(entity_id);

CREATE INDEX IF NOT EXISTS idx_audit_logs_created_at ON zxplat_audit_logs(created_at DESC);

CREATE INDEX IF NOT EXISTS idx_audit_logs_user_id ON zxplat_audit_logs(user_id);

-- ========== zxplat_framework_runtime_state ==========
-- 框架运行时状态快照（zxplat-runtime 中心节点收集，无种子）

CREATE TABLE IF NOT EXISTS zxplat_framework_runtime_state (
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

CREATE INDEX IF NOT EXISTS idx_fw_runtime_state_insight_code ON zxplat_framework_runtime_state(insight_code);

CREATE INDEX IF NOT EXISTS idx_fw_runtime_state_fingerprint ON zxplat_framework_runtime_state(deployment_fingerprint);

CREATE INDEX IF NOT EXISTS idx_fw_runtime_state_received_at ON zxplat_framework_runtime_state(received_at DESC);

-- ========== 表/列注释（须在 CREATE TABLE 之后执行）==========

COMMENT ON TABLE zxplat_roles IS '系统角色';

COMMENT ON COLUMN zxplat_roles.id IS '主键';

COMMENT ON COLUMN zxplat_roles.name IS '角色名称（唯一）';

COMMENT ON COLUMN zxplat_roles.display_name IS '显示名称';

COMMENT ON COLUMN zxplat_roles.code IS '角色编码';

COMMENT ON COLUMN zxplat_roles.description IS '描述';

COMMENT ON COLUMN zxplat_roles.is_default IS '是否默认角色';

COMMENT ON COLUMN zxplat_roles.is_static IS '是否静态角色（不可删）';

COMMENT ON COLUMN zxplat_roles.tenant_id IS '租户标识';

COMMENT ON COLUMN zxplat_roles.created_at IS '创建时间';

COMMENT ON COLUMN zxplat_roles.updated_at IS '最后更新时间';

COMMENT ON COLUMN zxplat_roles.created_by IS '创建人';

COMMENT ON COLUMN zxplat_roles.updated_by IS '最后更新人';

COMMENT ON COLUMN zxplat_roles.is_deleted IS '软删标记';

COMMENT ON COLUMN zxplat_roles.deleted_at IS '删除时间';

COMMENT ON COLUMN zxplat_roles.deleted_by IS '删除人';

COMMENT ON TABLE zxplat_user_roles IS '用户角色关联';

COMMENT ON COLUMN zxplat_user_roles.id IS '主键';

COMMENT ON COLUMN zxplat_user_roles.user_id IS '用户标识';

COMMENT ON COLUMN zxplat_user_roles.role_id IS '角色标识';

COMMENT ON COLUMN zxplat_user_roles.tenant_id IS '租户标识';

COMMENT ON COLUMN zxplat_user_roles.created_at IS '创建时间';

COMMENT ON COLUMN zxplat_user_roles.updated_at IS '最后更新时间';

COMMENT ON COLUMN zxplat_user_roles.created_by IS '创建人';

COMMENT ON COLUMN zxplat_user_roles.updated_by IS '最后更新人';

COMMENT ON COLUMN zxplat_user_roles.is_deleted IS '软删标记';

COMMENT ON COLUMN zxplat_user_roles.deleted_at IS '删除时间';

COMMENT ON COLUMN zxplat_user_roles.deleted_by IS '删除人';

COMMENT ON TABLE zxplat_permissions IS '权限定义';

COMMENT ON COLUMN zxplat_permissions.id IS '主键';

COMMENT ON COLUMN zxplat_permissions.name IS '权限名称（唯一）';

COMMENT ON COLUMN zxplat_permissions.display_name IS '显示名称';

COMMENT ON COLUMN zxplat_permissions.parent_name IS '父权限名称';

COMMENT ON COLUMN zxplat_permissions.description IS '描述';

COMMENT ON COLUMN zxplat_permissions.tenant_id IS '租户标识';

COMMENT ON COLUMN zxplat_permissions.created_at IS '创建时间';

COMMENT ON COLUMN zxplat_permissions.updated_at IS '最后更新时间';

COMMENT ON COLUMN zxplat_permissions.created_by IS '创建人';

COMMENT ON COLUMN zxplat_permissions.updated_by IS '最后更新人';

COMMENT ON COLUMN zxplat_permissions.is_deleted IS '软删标记';

COMMENT ON COLUMN zxplat_permissions.deleted_at IS '删除时间';

COMMENT ON COLUMN zxplat_permissions.deleted_by IS '删除人';

COMMENT ON TABLE zxplat_permission_grants IS '权限授权';

COMMENT ON COLUMN zxplat_permission_grants.id IS '主键';

COMMENT ON COLUMN zxplat_permission_grants.permission_name IS '权限名称';

COMMENT ON COLUMN zxplat_permission_grants.provider_name IS '授权主体类型（R=角色/U=用户）';

COMMENT ON COLUMN zxplat_permission_grants.provider_key IS '授权主体键';

COMMENT ON COLUMN zxplat_permission_grants.tenant_id IS '租户标识';

COMMENT ON COLUMN zxplat_permission_grants.created_at IS '创建时间';

COMMENT ON COLUMN zxplat_permission_grants.updated_at IS '最后更新时间';

COMMENT ON COLUMN zxplat_permission_grants.created_by IS '创建人';

COMMENT ON COLUMN zxplat_permission_grants.updated_by IS '最后更新人';

COMMENT ON COLUMN zxplat_permission_grants.is_deleted IS '软删标记';

COMMENT ON COLUMN zxplat_permission_grants.deleted_at IS '删除时间';

COMMENT ON COLUMN zxplat_permission_grants.deleted_by IS '删除人';

COMMENT ON TABLE zxplat_security_logs IS '安全审计日志';

COMMENT ON COLUMN zxplat_security_logs.id IS '主键';

COMMENT ON COLUMN zxplat_security_logs.user_id IS '用户标识';

COMMENT ON COLUMN zxplat_security_logs.user_name IS '用户名称';

COMMENT ON COLUMN zxplat_security_logs.action IS '操作动作';

COMMENT ON COLUMN zxplat_security_logs.client_ip IS '客户端ip';

COMMENT ON COLUMN zxplat_security_logs.user_agent IS '用户agent';

COMMENT ON COLUMN zxplat_security_logs.result IS '结果';

COMMENT ON COLUMN zxplat_security_logs.failure_reason IS 'failure原因';

COMMENT ON COLUMN zxplat_security_logs.tenant_id IS '租户标识';

COMMENT ON COLUMN zxplat_security_logs.extra_properties IS '扩展属性 JSON';

COMMENT ON COLUMN zxplat_security_logs.created_at IS '创建时间';

COMMENT ON COLUMN zxplat_security_logs.updated_at IS '最后更新时间';

COMMENT ON COLUMN zxplat_security_logs.created_by IS '创建人';

COMMENT ON COLUMN zxplat_security_logs.updated_by IS '最后更新人';

COMMENT ON COLUMN zxplat_security_logs.is_deleted IS '软删标记';

COMMENT ON COLUMN zxplat_security_logs.deleted_at IS '删除时间';

COMMENT ON COLUMN zxplat_security_logs.deleted_by IS '删除人';

COMMENT ON TABLE zxplat_organization_units IS '组织机构单元';

COMMENT ON COLUMN zxplat_organization_units.id IS '主键';

COMMENT ON COLUMN zxplat_organization_units.code IS '编码';

COMMENT ON COLUMN zxplat_organization_units.display_name IS '显示名称';

COMMENT ON COLUMN zxplat_organization_units.parent_id IS '父节点标识';

COMMENT ON COLUMN zxplat_organization_units.sort_order IS '排序号';

COMMENT ON COLUMN zxplat_organization_units.is_enabled IS '是否启用，语义等同 { PublicationStatus.ACTIVE}（见 health-domain-shared）';

COMMENT ON COLUMN zxplat_organization_units.remark IS '备注';

COMMENT ON COLUMN zxplat_organization_units.tenant_id IS '租户标识';

COMMENT ON COLUMN zxplat_organization_units.created_at IS '创建时间';

COMMENT ON COLUMN zxplat_organization_units.updated_at IS '最后更新时间';

COMMENT ON COLUMN zxplat_organization_units.created_by IS '创建人';

COMMENT ON COLUMN zxplat_organization_units.updated_by IS '最后更新人';

COMMENT ON COLUMN zxplat_organization_units.is_deleted IS '软删标记';

COMMENT ON COLUMN zxplat_organization_units.deleted_at IS '删除时间';

COMMENT ON COLUMN zxplat_organization_units.deleted_by IS '删除人';

COMMENT ON TABLE zxplat_user_organization_units IS '用户组织单元关联';

COMMENT ON COLUMN zxplat_user_organization_units.id IS '主键';

COMMENT ON COLUMN zxplat_user_organization_units.user_id IS '用户标识';

COMMENT ON COLUMN zxplat_user_organization_units.organization_unit_id IS '组织单元标识';

COMMENT ON COLUMN zxplat_user_organization_units.tenant_id IS '租户标识';

COMMENT ON COLUMN zxplat_user_organization_units.created_at IS '创建时间';

COMMENT ON COLUMN zxplat_user_organization_units.updated_at IS '最后更新时间';

COMMENT ON COLUMN zxplat_user_organization_units.created_by IS '创建人';

COMMENT ON COLUMN zxplat_user_organization_units.updated_by IS '最后更新人';

COMMENT ON COLUMN zxplat_user_organization_units.is_deleted IS '软删标记';

COMMENT ON COLUMN zxplat_user_organization_units.deleted_at IS '删除时间';

COMMENT ON COLUMN zxplat_user_organization_units.deleted_by IS '删除人';

COMMENT ON TABLE zxplat_user_claims IS '用户声明';

COMMENT ON COLUMN zxplat_user_claims.id IS '主键';

COMMENT ON COLUMN zxplat_user_claims.user_id IS '用户标识';

COMMENT ON COLUMN zxplat_user_claims.claim_type IS '声明类型';

COMMENT ON COLUMN zxplat_user_claims.claim_value IS '声明值';

COMMENT ON COLUMN zxplat_user_claims.tenant_id IS '租户标识';

COMMENT ON COLUMN zxplat_user_claims.created_at IS '创建时间';

COMMENT ON COLUMN zxplat_user_claims.updated_at IS '最后更新时间';

COMMENT ON COLUMN zxplat_user_claims.created_by IS '创建人';

COMMENT ON COLUMN zxplat_user_claims.updated_by IS '最后更新人';

COMMENT ON COLUMN zxplat_user_claims.is_deleted IS '软删标记';

COMMENT ON COLUMN zxplat_user_claims.deleted_at IS '删除时间';

COMMENT ON COLUMN zxplat_user_claims.deleted_by IS '删除人';

COMMENT ON TABLE zxplat_role_claims IS '角色声明';

COMMENT ON COLUMN zxplat_role_claims.id IS '主键';

COMMENT ON COLUMN zxplat_role_claims.role_id IS '角色标识';

COMMENT ON COLUMN zxplat_role_claims.claim_type IS '声明类型';

COMMENT ON COLUMN zxplat_role_claims.claim_value IS '声明值';

COMMENT ON COLUMN zxplat_role_claims.tenant_id IS '租户标识';

COMMENT ON COLUMN zxplat_role_claims.created_at IS '创建时间';

COMMENT ON COLUMN zxplat_role_claims.updated_at IS '最后更新时间';

COMMENT ON COLUMN zxplat_role_claims.created_by IS '创建人';

COMMENT ON COLUMN zxplat_role_claims.updated_by IS '最后更新人';

COMMENT ON COLUMN zxplat_role_claims.is_deleted IS '软删标记';

COMMENT ON COLUMN zxplat_role_claims.deleted_at IS '删除时间';

COMMENT ON COLUMN zxplat_role_claims.deleted_by IS '删除人';

COMMENT ON TABLE zxplat_qrtz_locks IS 'Quartz 调度锁';

COMMENT ON COLUMN zxplat_qrtz_locks.sched_name IS '调度器名称';

COMMENT ON COLUMN zxplat_qrtz_locks.lock_name IS '锁名称';

COMMENT ON TABLE zxplat_qrtz_job_details IS 'Quartz 作业详情';

COMMENT ON COLUMN zxplat_qrtz_job_details.sched_name IS '调度器名称';

COMMENT ON COLUMN zxplat_qrtz_job_details.job_name IS '作业名称';

COMMENT ON COLUMN zxplat_qrtz_job_details.job_group IS '作业组';

COMMENT ON COLUMN zxplat_qrtz_job_details.description IS '描述';

COMMENT ON COLUMN zxplat_qrtz_job_details.job_class_name IS '作业类名';

COMMENT ON COLUMN zxplat_qrtz_job_details.is_durable IS '是否持久化';

COMMENT ON COLUMN zxplat_qrtz_job_details.is_nonconcurrent IS '是否禁止并发';

COMMENT ON COLUMN zxplat_qrtz_job_details.is_update_data IS '是否更新数据';

COMMENT ON COLUMN zxplat_qrtz_job_details.requests_recovery IS '是否请求恢复';

COMMENT ON COLUMN zxplat_qrtz_job_details.job_data IS '作业数据（BLOB）';

COMMENT ON TABLE zxplat_qrtz_triggers IS 'Quartz 触发器';

COMMENT ON COLUMN zxplat_qrtz_triggers.sched_name IS '调度器名称';

COMMENT ON COLUMN zxplat_qrtz_triggers.trigger_name IS '触发器名称';

COMMENT ON COLUMN zxplat_qrtz_triggers.trigger_group IS '触发器组';

COMMENT ON COLUMN zxplat_qrtz_triggers.job_name IS '关联作业名称';

COMMENT ON COLUMN zxplat_qrtz_triggers.job_group IS '关联作业组';

COMMENT ON COLUMN zxplat_qrtz_triggers.description IS '描述';

COMMENT ON COLUMN zxplat_qrtz_triggers.next_fire_time IS '下次触发时间（毫秒）';

COMMENT ON COLUMN zxplat_qrtz_triggers.prev_fire_time IS '上次触发时间（毫秒）';

COMMENT ON COLUMN zxplat_qrtz_triggers.priority IS '优先级';

COMMENT ON COLUMN zxplat_qrtz_triggers.trigger_state IS '触发器状态';

COMMENT ON COLUMN zxplat_qrtz_triggers.trigger_type IS '触发器类型';

COMMENT ON COLUMN zxplat_qrtz_triggers.start_time IS '开始时间（毫秒）';

COMMENT ON COLUMN zxplat_qrtz_triggers.end_time IS '结束时间（毫秒）';

COMMENT ON COLUMN zxplat_qrtz_triggers.calendar_name IS '日历名称';

COMMENT ON COLUMN zxplat_qrtz_triggers.misfire_instr IS '错失触发策略';

COMMENT ON COLUMN zxplat_qrtz_triggers.job_data IS '触发器数据（BLOB）';

COMMENT ON TABLE zxplat_qrtz_cron_triggers IS 'Quartz Cron 触发器';

COMMENT ON COLUMN zxplat_qrtz_cron_triggers.sched_name IS '调度器名称';

COMMENT ON COLUMN zxplat_qrtz_cron_triggers.trigger_name IS '触发器名称';

COMMENT ON COLUMN zxplat_qrtz_cron_triggers.trigger_group IS '触发器组';

COMMENT ON COLUMN zxplat_qrtz_cron_triggers.cron_expression IS 'Cron 表达式';

COMMENT ON COLUMN zxplat_qrtz_cron_triggers.time_zone_id IS '时区 ID';

COMMENT ON TABLE zxplat_qrtz_simple_triggers IS 'Quartz 简单触发器';

COMMENT ON COLUMN zxplat_qrtz_simple_triggers.sched_name IS '调度器名称';

COMMENT ON COLUMN zxplat_qrtz_simple_triggers.trigger_name IS '触发器名称';

COMMENT ON COLUMN zxplat_qrtz_simple_triggers.trigger_group IS '触发器组';

COMMENT ON COLUMN zxplat_qrtz_simple_triggers.repeat_count IS '重复次数';

COMMENT ON COLUMN zxplat_qrtz_simple_triggers.repeat_interval IS '重复间隔（毫秒）';

COMMENT ON COLUMN zxplat_qrtz_simple_triggers.times_triggered IS '已触发次数';

COMMENT ON TABLE zxplat_qrtz_blob_triggers IS 'Quartz BLOB 触发器';

COMMENT ON COLUMN zxplat_qrtz_blob_triggers.sched_name IS '调度器名称';

COMMENT ON COLUMN zxplat_qrtz_blob_triggers.trigger_name IS '触发器名称';

COMMENT ON COLUMN zxplat_qrtz_blob_triggers.trigger_group IS '触发器组';

COMMENT ON COLUMN zxplat_qrtz_blob_triggers.blob_data IS '触发器 BLOB 数据';

COMMENT ON TABLE zxplat_qrtz_calendars IS 'Quartz 日历';

COMMENT ON COLUMN zxplat_qrtz_calendars.sched_name IS '调度器名称';

COMMENT ON COLUMN zxplat_qrtz_calendars.calendar_name IS '日历名称';

COMMENT ON COLUMN zxplat_qrtz_calendars.calendar IS '日历对象（BLOB）';

COMMENT ON TABLE zxplat_qrtz_paused_trigger_grps IS 'Quartz 暂停触发器组';

COMMENT ON COLUMN zxplat_qrtz_paused_trigger_grps.sched_name IS '调度器名称';

COMMENT ON COLUMN zxplat_qrtz_paused_trigger_grps.trigger_group IS '触发器组';

COMMENT ON TABLE zxplat_qrtz_scheduler_state IS 'Quartz 调度器状态';

COMMENT ON COLUMN zxplat_qrtz_scheduler_state.sched_name IS '调度器名称';

COMMENT ON COLUMN zxplat_qrtz_scheduler_state.instance_name IS '实例名称';

COMMENT ON COLUMN zxplat_qrtz_scheduler_state.last_checkin_time IS '最后检入时间（毫秒）';

COMMENT ON COLUMN zxplat_qrtz_scheduler_state.checkin_interval IS '检入间隔（毫秒）';

COMMENT ON TABLE zxplat_qrtz_fired_triggers IS 'Quartz 已触发记录';

COMMENT ON COLUMN zxplat_qrtz_fired_triggers.sched_name IS '调度器名称';

COMMENT ON COLUMN zxplat_qrtz_fired_triggers.entry_id IS '条目 ID';

COMMENT ON COLUMN zxplat_qrtz_fired_triggers.trigger_name IS '触发器名称';

COMMENT ON COLUMN zxplat_qrtz_fired_triggers.trigger_group IS '触发器组';

COMMENT ON COLUMN zxplat_qrtz_fired_triggers.instance_name IS '实例名称';

COMMENT ON COLUMN zxplat_qrtz_fired_triggers.fired_time IS '触发时间（毫秒）';

COMMENT ON COLUMN zxplat_qrtz_fired_triggers.sched_time IS '计划时间（毫秒）';

COMMENT ON COLUMN zxplat_qrtz_fired_triggers.priority IS '优先级';

COMMENT ON COLUMN zxplat_qrtz_fired_triggers.state IS '状态';

COMMENT ON COLUMN zxplat_qrtz_fired_triggers.job_name IS '作业名称';

COMMENT ON COLUMN zxplat_qrtz_fired_triggers.job_group IS '作业组';

COMMENT ON COLUMN zxplat_qrtz_fired_triggers.is_nonconcurrent IS '是否禁止并发';

COMMENT ON COLUMN zxplat_qrtz_fired_triggers.requests_recovery IS '是否请求恢复';

COMMENT ON TABLE zxplat_tenants IS '租户';

COMMENT ON COLUMN zxplat_tenants.id IS '主键';

COMMENT ON COLUMN zxplat_tenants.name IS '租户名称';

COMMENT ON COLUMN zxplat_tenants.code IS '租户代码';

COMMENT ON COLUMN zxplat_tenants.description IS '租户描述';

COMMENT ON COLUMN zxplat_tenants.is_active IS '是否启用';

COMMENT ON COLUMN zxplat_tenants.extra_properties IS '扩展属性';

COMMENT ON COLUMN zxplat_tenants.created_at IS '创建时间';

COMMENT ON COLUMN zxplat_tenants.updated_at IS '最后更新时间';

COMMENT ON COLUMN zxplat_tenants.created_by IS '创建人';

COMMENT ON COLUMN zxplat_tenants.updated_by IS '最后更新人';

COMMENT ON COLUMN zxplat_tenants.is_deleted IS '软删标记';

COMMENT ON COLUMN zxplat_tenants.deleted_at IS '删除时间';

COMMENT ON COLUMN zxplat_tenants.deleted_by IS '删除人';

COMMENT ON TABLE zxplat_user_tenants IS '用户租户关联';

COMMENT ON COLUMN zxplat_user_tenants.id IS '主键';

COMMENT ON COLUMN zxplat_user_tenants.user_id IS '用户标识';

COMMENT ON COLUMN zxplat_user_tenants.tenant_id IS '租户标识';

COMMENT ON COLUMN zxplat_user_tenants.created_at IS '创建时间';

COMMENT ON COLUMN zxplat_user_tenants.updated_at IS '最后更新时间';

COMMENT ON COLUMN zxplat_user_tenants.created_by IS '创建人';

COMMENT ON COLUMN zxplat_user_tenants.updated_by IS '最后更新人';

COMMENT ON COLUMN zxplat_user_tenants.is_deleted IS '软删标记';

COMMENT ON COLUMN zxplat_user_tenants.deleted_at IS '删除时间';

COMMENT ON COLUMN zxplat_user_tenants.deleted_by IS '删除人';

COMMENT ON TABLE zxplat_settings IS '系统设置';

COMMENT ON COLUMN zxplat_settings.id IS '主键';

COMMENT ON COLUMN zxplat_settings.name IS '设置键';

COMMENT ON COLUMN zxplat_settings.value IS '设置值';

COMMENT ON COLUMN zxplat_settings.provider_name IS '提供者类型';

COMMENT ON COLUMN zxplat_settings.provider_key IS '提供者键';

COMMENT ON COLUMN zxplat_settings.tenant_id IS '租户标识';

COMMENT ON COLUMN zxplat_settings.description IS '描述';

COMMENT ON COLUMN zxplat_settings.created_at IS '创建时间';

COMMENT ON COLUMN zxplat_settings.updated_at IS '最后更新时间';

COMMENT ON COLUMN zxplat_settings.created_by IS '创建人';

COMMENT ON COLUMN zxplat_settings.updated_by IS '最后更新人';

COMMENT ON COLUMN zxplat_settings.is_deleted IS '软删标记';

COMMENT ON COLUMN zxplat_settings.deleted_at IS '删除时间';

COMMENT ON COLUMN zxplat_settings.deleted_by IS '删除人';

COMMENT ON TABLE zxplat_menus IS '菜单';

COMMENT ON COLUMN zxplat_menus.id IS '主键';

COMMENT ON COLUMN zxplat_menus.code IS '菜单编码';

COMMENT ON COLUMN zxplat_menus.name IS '菜单名称';

COMMENT ON COLUMN zxplat_menus.display_name IS '显示名称';

COMMENT ON COLUMN zxplat_menus.icon IS '图标';

COMMENT ON COLUMN zxplat_menus.path IS '路由路径';

COMMENT ON COLUMN zxplat_menus.component IS '前端组件路径';

COMMENT ON COLUMN zxplat_menus.permission_name IS '关联权限名称';

COMMENT ON COLUMN zxplat_menus.parent_id IS '父菜单 ID';

COMMENT ON COLUMN zxplat_menus.sort_order IS '排序号';

COMMENT ON COLUMN zxplat_menus.is_enabled IS '是否启用';

COMMENT ON COLUMN zxplat_menus.is_hidden IS '是否隐藏';

COMMENT ON COLUMN zxplat_menus.is_external IS '是否外链';

COMMENT ON COLUMN zxplat_menus.external_url IS '外链 URL';

COMMENT ON COLUMN zxplat_menus.menu_type IS '菜单类型（menu/directory）';

COMMENT ON COLUMN zxplat_menus.remark IS '备注';

COMMENT ON COLUMN zxplat_menus.tenant_id IS '租户标识';

COMMENT ON COLUMN zxplat_menus.created_at IS '创建时间';

COMMENT ON COLUMN zxplat_menus.updated_at IS '最后更新时间';

COMMENT ON COLUMN zxplat_menus.created_by IS '创建人';

COMMENT ON COLUMN zxplat_menus.updated_by IS '最后更新人';

COMMENT ON COLUMN zxplat_menus.is_deleted IS '软删标记';

COMMENT ON COLUMN zxplat_menus.deleted_at IS '删除时间';

COMMENT ON COLUMN zxplat_menus.deleted_by IS '删除人';

COMMENT ON COLUMN zxplat_menus.keep_alive IS '是否缓存页面';

COMMENT ON TABLE zxplat_sequence IS '业务序号表，按 key 记录当前序号';

COMMENT ON COLUMN zxplat_sequence.seq_key IS '序号键（如 ORDER、PATIENT、或 ORDER:2025-01-30 表示按天）';

COMMENT ON COLUMN zxplat_sequence.current_value IS '当前序号值（递增后返回）';

COMMENT ON COLUMN zxplat_sequence.updated_at IS '最后更新时间';

COMMENT ON TABLE zxplat_audit_logs IS '实体变更审计日志';

COMMENT ON COLUMN zxplat_audit_logs.id IS '主键';

COMMENT ON COLUMN zxplat_audit_logs.entity_type IS '实体类型';

COMMENT ON COLUMN zxplat_audit_logs.entity_id IS '实体 ID';

COMMENT ON COLUMN zxplat_audit_logs.operation_type IS '操作类型';

COMMENT ON COLUMN zxplat_audit_logs.user_id IS '操作人';

COMMENT ON COLUMN zxplat_audit_logs.tenant_id IS '租户标识';

COMMENT ON COLUMN zxplat_audit_logs.ip_address IS 'IP 地址';

COMMENT ON COLUMN zxplat_audit_logs.user_agent IS '用户agent';

COMMENT ON COLUMN zxplat_audit_logs.changes IS '变更内容';

COMMENT ON COLUMN zxplat_audit_logs.created_at IS '创建时间';

COMMENT ON TABLE zxplat_framework_runtime_state IS '框架运行时状态';

COMMENT ON COLUMN zxplat_framework_runtime_state.id IS '主键';

COMMENT ON COLUMN zxplat_framework_runtime_state.insight_code IS '洞察编码';

COMMENT ON COLUMN zxplat_framework_runtime_state.category IS '分类';

COMMENT ON COLUMN zxplat_framework_runtime_state.runtime_version IS '运行时版本';

COMMENT ON COLUMN zxplat_framework_runtime_state.operator_user_id IS '操作人用户标识';

COMMENT ON COLUMN zxplat_framework_runtime_state.session_started_at IS '会话开始时间';

COMMENT ON COLUMN zxplat_framework_runtime_state.org_category IS '机构类别';

COMMENT ON COLUMN zxplat_framework_runtime_state.org_name IS '机构名称';

COMMENT ON COLUMN zxplat_framework_runtime_state.deployment_fingerprint IS '部署指纹';

COMMENT ON COLUMN zxplat_framework_runtime_state.license_customer IS '许可证客户';

COMMENT ON COLUMN zxplat_framework_runtime_state.license_not_after IS '许可证失效时间';

COMMENT ON COLUMN zxplat_framework_runtime_state.payload_json IS '载荷 JSON';

COMMENT ON COLUMN zxplat_framework_runtime_state.observed_at IS '观测时间';

COMMENT ON COLUMN zxplat_framework_runtime_state.received_at IS '接收时间';

-- ========== 发版合并：原 V74–V87 菜单/权限种子（zxplat_menus / zxplat_permissions）==========

-- ----- V74__report_menus.sql -----
-- V74：zxplat-print/report 菜单 + Report.* 权限 + 管理员角色授权
-- 说明：V1 已写入菜单但缺少 Print.Templates.View 等权限及 role=1 授权，导致 UI 不显示
-- 结构：顶级「报表打印」目录下集中 zxplat-print/report 全部入口

-- ========== 1. Report 权限定义 ==========

-- V68 zxplat_user_login_session

-- 用户登录后会话：租户 / 医院 / 科室选择（跨进程重启持久化）
CREATE TABLE IF NOT EXISTS zxplat_user_login_session (
    user_id BIGINT PRIMARY KEY,
    tenant_id BIGINT,
    hospital_organization_id BIGINT,
    organization_unit_id BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_zxplat_user_login_session_user FOREIGN KEY (user_id) REFERENCES zxplat_users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_zxplat_user_login_session_tenant
    ON zxplat_user_login_session(tenant_id);
COMMENT ON TABLE zxplat_user_login_session IS '用户登录后的服务端会话：租户 / 医院 / 科室选择（跨重启持久化）';
COMMENT ON COLUMN zxplat_user_login_session.user_id IS '用户标识';
COMMENT ON COLUMN zxplat_user_login_session.tenant_id IS '租户标识';
COMMENT ON COLUMN zxplat_user_login_session.hospital_organization_id IS 'hospital机构标识';
COMMENT ON COLUMN zxplat_user_login_session.organization_unit_id IS '机构单元标识';
COMMENT ON COLUMN zxplat_user_login_session.updated_at IS '最后更新时间';

-- V70 zxplat_qrtz_job_execution_log

-- V92：定时任务执行历史（Quartz 标准表 zxplat_qrtz_fired_triggers 仅保留「正在跑」的瞬时行，跑完即删，无法存成功/失败历史）

CREATE TABLE IF NOT EXISTS zxplat_qrtz_job_execution_log (
    id BIGINT PRIMARY KEY,
    sched_name VARCHAR(120) NOT NULL DEFAULT 'ZxplatScheduler',
    job_name VARCHAR(200) NOT NULL,
    job_group VARCHAR(200) NOT NULL,
    job_bean_name VARCHAR(128) NOT NULL,
    display_name VARCHAR(128),
    tenant_id BIGINT,
    execution_type VARCHAR(16) NOT NULL,
    success BOOLEAN NOT NULL,
    error_message TEXT,
    duration_ms BIGINT NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_zxplat_qrtz_job_exec_log_finished
    ON zxplat_qrtz_job_execution_log (finished_at DESC);

CREATE INDEX IF NOT EXISTS idx_zxplat_qrtz_job_exec_log_tenant
    ON zxplat_qrtz_job_execution_log (tenant_id);

CREATE INDEX IF NOT EXISTS idx_zxplat_qrtz_job_exec_log_job_key
    ON zxplat_qrtz_job_execution_log (sched_name, job_group, job_name);

CREATE INDEX IF NOT EXISTS idx_zxplat_qrtz_job_exec_log_success
    ON zxplat_qrtz_job_execution_log (success);
COMMENT ON TABLE zxplat_qrtz_job_execution_log IS 'ZXPlat 扩展：定时任务执行历史（非 Quartz 内置 fired_triggers 替代品）';
COMMENT ON COLUMN zxplat_qrtz_job_execution_log.id IS '主键';
COMMENT ON COLUMN zxplat_qrtz_job_execution_log.sched_name IS '调度名称';
COMMENT ON COLUMN zxplat_qrtz_job_execution_log.job_name IS '任务名称';
COMMENT ON COLUMN zxplat_qrtz_job_execution_log.job_group IS '任务分组';
COMMENT ON COLUMN zxplat_qrtz_job_execution_log.job_bean_name IS '任务Bean名称';
COMMENT ON COLUMN zxplat_qrtz_job_execution_log.display_name IS '显示名称';
COMMENT ON COLUMN zxplat_qrtz_job_execution_log.tenant_id IS '租户标识';
COMMENT ON COLUMN zxplat_qrtz_job_execution_log.execution_type IS '执行类型';
COMMENT ON COLUMN zxplat_qrtz_job_execution_log.success IS '成功';
COMMENT ON COLUMN zxplat_qrtz_job_execution_log.error_message IS '错误消息';
COMMENT ON COLUMN zxplat_qrtz_job_execution_log.duration_ms IS '耗时ms';
COMMENT ON COLUMN zxplat_qrtz_job_execution_log.started_at IS '开始时间';
COMMENT ON COLUMN zxplat_qrtz_job_execution_log.finished_at IS '结束时间';
