-- peis_report_grant_ext（建表）
-- Flyway V84 · DM-PEIS-005

CREATE TABLE IF NOT EXISTS "public"."peis_report_grant_ext" (
    "diagnostic_report_id" int8 NOT NULL,
    "tenant_id" int8,
    "org_id" int8,
    "created_at" timestamptz(6) NOT NULL,
    "updated_at" timestamptz(6),
    "created_by" int8,
    "updated_by" int8,
    "is_deleted" bool NOT NULL DEFAULT false,
    "deleted_at" timestamptz(6),
    "deleted_by" int8,
    "version" int4 NOT NULL DEFAULT 0,
    "grant_status_enum" varchar(64) NOT NULL DEFAULT 'pending',
    "grant_time" timestamptz(6),
    "grant_by" int8,
    "recipient_name" varchar(255),
    CONSTRAINT "peis_report_grant_ext_pkey" PRIMARY KEY ("diagnostic_report_id")
);

COMMENT ON TABLE peis_report_grant_ext IS '体检报告发放/签收扩展（1:1 DiagnosticReport）';
COMMENT ON COLUMN peis_report_grant_ext.diagnostic_report_id IS '总检报告标识（主键）';
COMMENT ON COLUMN peis_report_grant_ext.tenant_id IS '租户标识';
COMMENT ON COLUMN peis_report_grant_ext.org_id IS '机构标识';
COMMENT ON COLUMN peis_report_grant_ext.created_at IS '创建时间';
COMMENT ON COLUMN peis_report_grant_ext.updated_at IS '最后更新时间';
COMMENT ON COLUMN peis_report_grant_ext.created_by IS '创建人';
COMMENT ON COLUMN peis_report_grant_ext.updated_by IS '最后更新人';
COMMENT ON COLUMN peis_report_grant_ext.is_deleted IS '软删标记';
COMMENT ON COLUMN peis_report_grant_ext.deleted_at IS '删除时间';
COMMENT ON COLUMN peis_report_grant_ext.deleted_by IS '删除人';
COMMENT ON COLUMN peis_report_grant_ext.version IS '乐观锁版本号';
COMMENT ON COLUMN peis_report_grant_ext.grant_status_enum IS '发放状态枚举';
COMMENT ON COLUMN peis_report_grant_ext.grant_time IS '发放时间';
COMMENT ON COLUMN peis_report_grant_ext.grant_by IS '发放人标识';
COMMENT ON COLUMN peis_report_grant_ext.recipient_name IS '签收人姓名';
