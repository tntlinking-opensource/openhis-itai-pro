-- cus_order_template（建表）
-- Flyway V34

CREATE TABLE IF NOT EXISTS "public"."cus_order_template" (
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
    "object_id" int8 NOT NULL,
    "binding_enum" varchar(255) NOT NULL,
    "scene_enum" varchar(255) NOT NULL,
    "template_code" varchar(255),
    "name" varchar(255) NOT NULL,
    "description" varchar(255),
    "sort_weight" int4,
    "status_enum" varchar(255) NOT NULL,
    "remark" varchar(255),
    CONSTRAINT "cus_order_template_pkey" PRIMARY KEY ("id")
);