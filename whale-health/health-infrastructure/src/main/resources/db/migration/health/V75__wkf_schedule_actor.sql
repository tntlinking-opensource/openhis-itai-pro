-- wkf_schedule_actor（建表）
-- Flyway V75

CREATE TABLE IF NOT EXISTS "public"."wkf_schedule_actor" (
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
    "schedule_id" int8 NOT NULL,
    "actor_type" int4 NOT NULL,
    "actor_id" int8 NOT NULL,
    "sort_no" int4 NOT NULL,
    CONSTRAINT "wkf_schedule_actor_pkey" PRIMARY KEY ("id")
);