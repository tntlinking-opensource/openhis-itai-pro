-- PEIS V6：团检单位/合同/批次/名册 + 团检管理菜单
-- 主路径：名册 → AS-PE-FIN-002 批量收费建 Encounter(待检) → GRP 到检确认

CREATE TABLE IF NOT EXISTS peis_group_company (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    org_id BIGINT,
    name VARCHAR(128) NOT NULL,
    campus_college_id BIGINT,
    contact_name VARCHAR(64),
    contact_phone VARCHAR(32),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT,
    version INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS peis_group_contract (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    org_id BIGINT,
    company_id BIGINT NOT NULL,
    package_activity_id BIGINT NOT NULL,
    contract_name VARCHAR(128),
    valid_from DATE,
    valid_to DATE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT,
    version INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS peis_group_batch (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    org_id BIGINT,
    company_id BIGINT NOT NULL,
    contract_id BIGINT NOT NULL,
    batch_name VARCHAR(128) NOT NULL,
    package_activity_id BIGINT NOT NULL,
    booking_date DATE,
    status_code VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT,
    version INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS peis_group_roster (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    org_id BIGINT,
    batch_id BIGINT NOT NULL,
    patient_id BIGINT,
    full_name VARCHAR(128) NOT NULL,
    id_card_no VARCHAR(32),
    student_no VARCHAR(64),
    employee_no VARCHAR(64),
    college_id BIGINT,
    class_name VARCHAR(128),
    mobile_phone VARCHAR(32),
    encounter_id BIGINT,
    pay_status_code VARCHAR(32) NOT NULL DEFAULT 'UNPAID',
    arrival_status_code VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT,
    version INT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_peis_group_roster_batch ON peis_group_roster (batch_id) WHERE is_deleted = false;
CREATE INDEX IF NOT EXISTS idx_peis_group_roster_pay ON peis_group_roster (batch_id, pay_status_code) WHERE is_deleted = false;

INSERT INTO whale_permissions (
    id, name, display_name, parent_name, description,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by
)
VALUES
    ('311100000000000007', 'Peis.GroupCheck.View', '团检管理', 'Peis',
     '菜单入口: PeisGroupCheckAdmin · AS-PEIS-GRP-001', NULL,
     TIMESTAMP '2026-07-19 00:00:00+00', TIMESTAMP '2026-07-19 00:00:00+00', 1, 1, FALSE, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    display_name = EXCLUDED.display_name,
    is_deleted = FALSE,
    updated_at = EXCLUDED.updated_at;

INSERT INTO whale_menus (
    id, code, name, display_name, icon, path, component, permission_name, parent_id,
    sort_order, is_enabled, is_hidden, is_external, external_url, menu_type, remark,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by, keep_alive
)
VALUES
    ('311100000000000107', 'PeisGroupCheck', 'PeisGroupCheck', '团检管理', 'lucide:users',
     '/peis/group-check', '/peis/group-check/index', 'Peis.GroupCheck.View', '311100000000000100', 5,
     TRUE, FALSE, FALSE, '', 'menu', 'PeisGroupCheckAdminAppService · AS-PEIS-GRP-001', NULL,
     TIMESTAMP '2026-07-19 00:00:00+00', TIMESTAMP '2026-07-19 00:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE)
ON CONFLICT (id) DO UPDATE SET
    path = EXCLUDED.path,
    component = EXCLUDED.component,
    is_deleted = FALSE,
    updated_at = EXCLUDED.updated_at;

INSERT INTO whale_permission_grants (
    id, permission_name, provider_name, provider_key,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by
)
VALUES
    ('311100000000000207', 'Peis.GroupCheck.View', 'R', '1', NULL,
     TIMESTAMP '2026-07-19 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL)
ON CONFLICT (permission_name, provider_name, provider_key) DO UPDATE SET
    is_deleted = FALSE,
    updated_at = TIMESTAMP '2026-07-19 00:00:00+00';
