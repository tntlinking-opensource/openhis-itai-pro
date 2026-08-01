-- V2
-- CRB 编码对照三表（科室 / 检验 / 药品）
-- 说明：crb_org_report_config 表已弃用前置连接写库（改由 CrbProperties），本迁移不删除该表。

CREATE TABLE crb_dept_code_map (
    id                BIGINT       NOT NULL PRIMARY KEY,
    tenant_id         BIGINT       NOT NULL,
    location_bus_no   VARCHAR(64)  NOT NULL,
    location_name     VARCHAR(256),
    target_dept_code  VARCHAR(64),
    target_dept_name  VARCHAR(256),
    is_deleted        BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMP    NOT NULL,
    updated_at        TIMESTAMP,
    CONSTRAINT uk_crb_dept_code_map UNIQUE (tenant_id, location_bus_no)
);
CREATE INDEX idx_crb_dept_code_map_tenant ON crb_dept_code_map (tenant_id, is_deleted);
CREATE INDEX idx_crb_dept_code_map_updated ON crb_dept_code_map (tenant_id, updated_at DESC);

CREATE TABLE crb_lab_item_map (
    id                  BIGINT       NOT NULL PRIMARY KEY,
    tenant_id           BIGINT       NOT NULL,
    item_bus_no         VARCHAR(64)  NOT NULL,
    item_name           VARCHAR(256),
    national_item_code  VARCHAR(64),
    result_shape        VARCHAR(16),
    annex2_scope        VARCHAR(32),
    push_enabled        BOOLEAN      NOT NULL DEFAULT TRUE,
    is_deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMP    NOT NULL,
    updated_at          TIMESTAMP,
    CONSTRAINT uk_crb_lab_item_map UNIQUE (tenant_id, item_bus_no)
);
CREATE INDEX idx_crb_lab_item_map_tenant ON crb_lab_item_map (tenant_id, is_deleted);
CREATE INDEX idx_crb_lab_item_map_push ON crb_lab_item_map (tenant_id, push_enabled, is_deleted);
CREATE INDEX idx_crb_lab_item_map_updated ON crb_lab_item_map (tenant_id, updated_at DESC);

CREATE TABLE crb_drug_code_map (
    id                   BIGINT       NOT NULL PRIMARY KEY,
    tenant_id            BIGINT       NOT NULL,
    med_bus_no           VARCHAR(64)  NOT NULL,
    med_name             VARCHAR(256),
    national_drug_code   VARCHAR(64),
    national_drug_name   VARCHAR(256),
    is_deleted           BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at           TIMESTAMP    NOT NULL,
    updated_at           TIMESTAMP,
    CONSTRAINT uk_crb_drug_code_map UNIQUE (tenant_id, med_bus_no)
);
CREATE INDEX idx_crb_drug_code_map_tenant ON crb_drug_code_map (tenant_id, is_deleted);
CREATE INDEX idx_crb_drug_code_map_updated ON crb_drug_code_map (tenant_id, updated_at DESC);
