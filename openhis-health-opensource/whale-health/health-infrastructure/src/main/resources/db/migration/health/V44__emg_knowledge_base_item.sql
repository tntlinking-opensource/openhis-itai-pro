-- emg_knowledge_base_item（建表）
-- Flyway V44

CREATE TABLE IF NOT EXISTS "public"."emg_knowledge_base_item" (
    "id" int8 NOT NULL,
    "tenant_id" int8,
    "created_at" timestamptz(6) NOT NULL,
    "updated_at" timestamptz(6),
    "created_by" int8,
    "updated_by" int8,
    "is_deleted" bool NOT NULL DEFAULT false,
    "deleted_at" timestamptz(6),
    "deleted_by" int8,

    "name" varchar(500) NOT NULL,
    "pinyin_code" varchar(100) NOT NULL,
    "triage_level" int4 NOT NULL,
    "type" varchar(64) NOT NULL,
    "category" varchar(128) NOT NULL,
    "sort_order" int4 NOT NULL,
    "status" int4 NOT NULL,

    CONSTRAINT "emg_knowledge_base_item_pkey" PRIMARY KEY ("id")
);