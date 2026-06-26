-- cln_procedure_performer（建表）
-- Flyway V28

CREATE TABLE IF NOT EXISTS "public"."cln_procedure_performer" (
    "id" int8 NOT NULL,
    "tenant_id" int8,
    "created_at" timestamptz(6) NOT NULL,
    "updated_at" timestamptz(6),
    "created_by" int8,
    "updated_by" int8,
    "is_deleted" bool NOT NULL DEFAULT false,
    "deleted_at" timestamptz(6),
    "deleted_by" int8,
    "version" int4 NOT NULL DEFAULT 0,
    "procedure_id" int8 NOT NULL,
    "function_code" varchar(255),
    "start_time" timestamptz(6) NOT NULL,
    "end_time" timestamptz(6),
    "practitioner_id" int8 NOT NULL,
    "org_from_id" varchar(255),
    CONSTRAINT "cln_procedure_performer_pkey" PRIMARY KEY ("id")
);
COMMENT ON TABLE cln_procedure_performer IS '临床 procedure performer';
COMMENT ON COLUMN cln_procedure_performer.id IS '主键';
COMMENT ON COLUMN cln_procedure_performer.tenant_id IS '租户标识';
COMMENT ON COLUMN cln_procedure_performer.created_at IS '创建时间';
COMMENT ON COLUMN cln_procedure_performer.updated_at IS '最后更新时间';
COMMENT ON COLUMN cln_procedure_performer.created_by IS '创建人';
COMMENT ON COLUMN cln_procedure_performer.updated_by IS '最后更新人';
COMMENT ON COLUMN cln_procedure_performer.is_deleted IS '软删标记';
COMMENT ON COLUMN cln_procedure_performer.deleted_at IS '删除时间';
COMMENT ON COLUMN cln_procedure_performer.deleted_by IS '删除人';
COMMENT ON COLUMN cln_procedure_performer.version IS '乐观锁版本号';
COMMENT ON COLUMN cln_procedure_performer.procedure_id IS '处置标识';
COMMENT ON COLUMN cln_procedure_performer.function_code IS 'function编码';
COMMENT ON COLUMN cln_procedure_performer.start_time IS '开始时间';
COMMENT ON COLUMN cln_procedure_performer.end_time IS '结束时间';
COMMENT ON COLUMN cln_procedure_performer.practitioner_id IS '医护人员标识';
COMMENT ON COLUMN cln_procedure_performer.org_from_id IS 'orgfrom标识';
