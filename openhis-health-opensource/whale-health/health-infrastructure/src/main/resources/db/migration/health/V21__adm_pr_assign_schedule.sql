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

-- seed: adm_pr_assign_schedule
-- (empty) no rows in adm_pr_assign_schedule
