-- V8__Booking_{主题}.sql（复制本模板为下一版本，勿修改 V1～V7）

CREATE TABLE IF NOT EXISTS bkg_{表名} (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT,
    organization_id BIGINT,
    -- 业务字段 …
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT,
    version INT NOT NULL DEFAULT 0
);

COMMENT ON TABLE bkg_{表名} IS '{表中文名}';
COMMENT ON COLUMN bkg_{表名}.id IS '主键';
COMMENT ON COLUMN bkg_{表名}.tenant_id IS '租户标识';
COMMENT ON COLUMN bkg_{表名}.organization_id IS '机构标识';
COMMENT ON COLUMN bkg_{表名}.created_at IS '创建时间';
COMMENT ON COLUMN bkg_{表名}.updated_at IS '最后更新时间';
COMMENT ON COLUMN bkg_{表名}.created_by IS '创建人';
COMMENT ON COLUMN bkg_{表名}.updated_by IS '最后更新人';
COMMENT ON COLUMN bkg_{表名}.is_deleted IS '软删标记';
COMMENT ON COLUMN bkg_{表名}.deleted_at IS '删除时间';
COMMENT ON COLUMN bkg_{表名}.deleted_by IS '删除人';
COMMENT ON COLUMN bkg_{表名}.version IS '乐观锁版本号';
