-- mrms_homepage_procedure（建表）
-- Flyway V91

CREATE TABLE IF NOT EXISTS mrms_homepage_procedure (
    id                  BIGINT       NOT NULL PRIMARY KEY,
    case_record_id      BIGINT       NOT NULL REFERENCES mrms_case_record(id),
    tenant_id           BIGINT       NOT NULL,
    sort_no             INTEGER      NOT NULL DEFAULT 0,
    oprn_oprt_code      VARCHAR(32),
    oprn_oprt_name      VARCHAR(200),
    oprn_date           DATE,
    main_oprn_flag      VARCHAR(3),
    vali_flag           VARCHAR(3)   NOT NULL DEFAULT '1',
    created_at          TIMESTAMP    NOT NULL,
    updated_at          TIMESTAMP,
    created_by          BIGINT,
    updated_by          BIGINT,
    is_deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at          TIMESTAMP,
    deleted_by          BIGINT
);

CREATE INDEX IF NOT EXISTS idx_mrms_homepage_proc_case
    ON mrms_homepage_procedure (case_record_id);

COMMENT ON TABLE mrms_homepage_procedure IS 'mrms首页处置';
COMMENT ON COLUMN mrms_homepage_procedure.id IS '主键';
COMMENT ON COLUMN mrms_homepage_procedure.case_record_id IS '病案记录标识';
COMMENT ON COLUMN mrms_homepage_procedure.tenant_id IS '租户标识';
COMMENT ON COLUMN mrms_homepage_procedure.sort_no IS '排序号';
COMMENT ON COLUMN mrms_homepage_procedure.oprn_oprt_code IS '手术操作编码';
COMMENT ON COLUMN mrms_homepage_procedure.oprn_oprt_name IS '手术操作名称';
COMMENT ON COLUMN mrms_homepage_procedure.oprn_date IS '手术日期';
COMMENT ON COLUMN mrms_homepage_procedure.main_oprn_flag IS '是否主手术';
COMMENT ON COLUMN mrms_homepage_procedure.vali_flag IS '有效标志';
COMMENT ON COLUMN mrms_homepage_procedure.created_at IS '创建时间';
COMMENT ON COLUMN mrms_homepage_procedure.updated_at IS '最后更新时间';
COMMENT ON COLUMN mrms_homepage_procedure.created_by IS '创建人';
COMMENT ON COLUMN mrms_homepage_procedure.updated_by IS '最后更新人';
COMMENT ON COLUMN mrms_homepage_procedure.is_deleted IS '软删标记';
COMMENT ON COLUMN mrms_homepage_procedure.deleted_at IS '删除时间';
COMMENT ON COLUMN mrms_homepage_procedure.deleted_by IS '删除人';
