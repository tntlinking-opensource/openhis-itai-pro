-- mrms_medcasno_sequence（建表）
-- Flyway V88

CREATE TABLE IF NOT EXISTS mrms_medcasno_sequence (
    id                  BIGINT       NOT NULL PRIMARY KEY,
    tenant_id           BIGINT       NOT NULL,
    year_value          INTEGER      NOT NULL,
    last_seq            INTEGER      NOT NULL DEFAULT 0,
    created_at          TIMESTAMP    NOT NULL,
    updated_at          TIMESTAMP,
    created_by          BIGINT,
    updated_by          BIGINT,
    is_deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at          TIMESTAMP,
    deleted_by          BIGINT
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_mrms_medcasno_seq
    ON mrms_medcasno_sequence (tenant_id, year_value)
    WHERE is_deleted = FALSE;

COMMENT ON TABLE mrms_medcasno_sequence IS 'mrms病案号序号';
COMMENT ON COLUMN mrms_medcasno_sequence.id IS '主键';
COMMENT ON COLUMN mrms_medcasno_sequence.tenant_id IS '租户标识';
COMMENT ON COLUMN mrms_medcasno_sequence.year_value IS '年份';
COMMENT ON COLUMN mrms_medcasno_sequence.last_seq IS '末位序号';
COMMENT ON COLUMN mrms_medcasno_sequence.created_at IS '创建时间';
COMMENT ON COLUMN mrms_medcasno_sequence.updated_at IS '最后更新时间';
COMMENT ON COLUMN mrms_medcasno_sequence.created_by IS '创建人';
COMMENT ON COLUMN mrms_medcasno_sequence.updated_by IS '最后更新人';
COMMENT ON COLUMN mrms_medcasno_sequence.is_deleted IS '软删标记';
COMMENT ON COLUMN mrms_medcasno_sequence.deleted_at IS '删除时间';
COMMENT ON COLUMN mrms_medcasno_sequence.deleted_by IS '删除人';
