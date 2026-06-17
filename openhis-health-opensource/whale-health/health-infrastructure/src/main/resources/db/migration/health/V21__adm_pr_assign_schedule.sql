-- adm_pr_assign_schedule（建表 + 种子数据）
-- Flyway V21

CREATE TABLE IF NOT EXISTS "public"."adm_pr_assign_schedule" (
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

    "assignment_id" int8 NOT NULL,
    "availability_json" varchar(20000) NOT NULL DEFAULT '{}',

    CONSTRAINT "adm_pr_assign_schedule_pkey" PRIMARY KEY ("id")
);

COMMENT ON TABLE adm_pr_assign_schedule IS '管理域 pr assign schedule';

COMMENT ON COLUMN adm_pr_assign_schedule.id IS '主键';
COMMENT ON COLUMN adm_pr_assign_schedule.tenant_id IS '租户标识';
COMMENT ON COLUMN adm_pr_assign_schedule.created_at IS '创建时间';
COMMENT ON COLUMN adm_pr_assign_schedule.updated_at IS '最后更新时间';
COMMENT ON COLUMN adm_pr_assign_schedule.created_by IS '创建人';
COMMENT ON COLUMN adm_pr_assign_schedule.updated_by IS '最后更新人';
COMMENT ON COLUMN adm_pr_assign_schedule.is_deleted IS '软删标记';
COMMENT ON COLUMN adm_pr_assign_schedule.deleted_at IS '删除时间';
COMMENT ON COLUMN adm_pr_assign_schedule.deleted_by IS '删除人';
COMMENT ON COLUMN adm_pr_assign_schedule.version IS '乐观锁版本号';
COMMENT ON COLUMN adm_pr_assign_schedule.assignment_id IS '岗位排班项子实体，对应表 { adm_pr_assign_schedule}';
COMMENT ON COLUMN adm_pr_assign_schedule.availability_json IS '可用时间 JSON';

-- seed: adm_pr_assign_schedule
-- (empty) no rows in adm_pr_assign_schedule
