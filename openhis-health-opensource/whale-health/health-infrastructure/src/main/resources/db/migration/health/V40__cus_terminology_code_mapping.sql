-- cus_terminology_code_mapping（建表）
-- Flyway V40

CREATE TABLE IF NOT EXISTS "public"."cus_terminology_code_mapping" (
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
    "source_item_table" varchar(255) NOT NULL,
    "source_item_no" varchar(255) NOT NULL,
    "target_item_no" varchar(255) NOT NULL,
    "target_name" varchar(255),
    "active_flag" bool NOT NULL,
    CONSTRAINT "cus_terminology_code_mapping_pkey" PRIMARY KEY ("id")
);