-- mrms_homepage_diagnosis（建表）
-- Flyway V90

CREATE TABLE IF NOT EXISTS mrms_homepage_diagnosis (
    id                  BIGINT       NOT NULL PRIMARY KEY,
    case_record_id      BIGINT       NOT NULL REFERENCES mrms_case_record(id),
    tenant_id           BIGINT       NOT NULL,
    diag_type_code      VARCHAR(32)  NOT NULL,
    sort_no             INTEGER      NOT NULL DEFAULT 0,
    diag_code           VARCHAR(32),
    diag_name           VARCHAR(200),
    adm_cond_code       VARCHAR(16),
    maindiag_flag       VARCHAR(3),
    vali_flag           VARCHAR(3)   NOT NULL DEFAULT '1',
    created_at          TIMESTAMP    NOT NULL,
    updated_at          TIMESTAMP,
    created_by          BIGINT,
    updated_by          BIGINT,
    is_deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at          TIMESTAMP,
    deleted_by          BIGINT
);

CREATE INDEX IF NOT EXISTS idx_mrms_homepage_diag_case
    ON mrms_homepage_diagnosis (case_record_id, diag_type_code);

COMMENT ON TABLE mrms_homepage_diagnosis IS 'mrms首页诊断';
COMMENT ON COLUMN mrms_homepage_diagnosis.id IS '主键';
COMMENT ON COLUMN mrms_homepage_diagnosis.case_record_id IS '病案记录标识';
COMMENT ON COLUMN mrms_homepage_diagnosis.tenant_id IS '租户标识';
COMMENT ON COLUMN mrms_homepage_diagnosis.diag_type_code IS 'diag类型编码';
COMMENT ON COLUMN mrms_homepage_diagnosis.sort_no IS '排序号';
COMMENT ON COLUMN mrms_homepage_diagnosis.diag_code IS 'diag编码';
COMMENT ON COLUMN mrms_homepage_diagnosis.diag_name IS 'diag名称';
COMMENT ON COLUMN mrms_homepage_diagnosis.adm_cond_code IS 'admcond编码';
COMMENT ON COLUMN mrms_homepage_diagnosis.maindiag_flag IS 'maindiag标志位';
COMMENT ON COLUMN mrms_homepage_diagnosis.vali_flag IS 'vali标志位';
COMMENT ON COLUMN mrms_homepage_diagnosis.created_at IS '创建时间';
COMMENT ON COLUMN mrms_homepage_diagnosis.updated_at IS '最后更新时间';
COMMENT ON COLUMN mrms_homepage_diagnosis.created_by IS '创建人';
COMMENT ON COLUMN mrms_homepage_diagnosis.updated_by IS '最后更新人';
COMMENT ON COLUMN mrms_homepage_diagnosis.is_deleted IS '软删标记';
COMMENT ON COLUMN mrms_homepage_diagnosis.deleted_at IS '删除时间';
COMMENT ON COLUMN mrms_homepage_diagnosis.deleted_by IS '删除人';
