-- wkf_activity_definition_children（建表）
-- Flyway V69

CREATE TABLE IF NOT EXISTS "public"."wkf_activity_definition_children" (
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
    "activity_id" int8 NOT NULL,
    "children_id" int8 NOT NULL,
    "children_num" int4,
    "children_name" varchar(255),
    CONSTRAINT "wkf_activity_definition_children_pkey" PRIMARY KEY ("id")
);