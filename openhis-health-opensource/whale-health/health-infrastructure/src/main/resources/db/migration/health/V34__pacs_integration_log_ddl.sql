-- pacs_integration_log（WBS_LOG 对标；厂商码表不在本脚本）
-- Flyway V12

CREATE TABLE IF NOT EXISTS "public"."pacs_integration_log" (
    "id" int8 NOT NULL,
    "tenant_id" int8,
    "created_at" timestamptz(6) NOT NULL,
    "updated_at" timestamptz(6),
    "created_by" int8,
    "updated_by" int8,
    "is_deleted" bool NOT NULL DEFAULT false,
    "deleted_at" timestamptz(6),
    "deleted_by" int8,
    "direction_enum" varchar(16) NOT NULL,
    "action_code" varchar(64) NOT NULL,
    "entity_type" varchar(64),
    "entity_id" int8,
    "outcome_enum" varchar(16) NOT NULL,
    "request_body" text,
    "response_body" text,
    "error_message" varchar(512),
    CONSTRAINT "pacs_integration_log_pkey" PRIMARY KEY ("id")
);
COMMENT ON TABLE pacs_integration_log IS 'PACS 集成接口日志';
CREATE INDEX IF NOT EXISTS "idx_pacs_int_log_entity" ON "public"."pacs_integration_log" ("entity_type", "entity_id");
CREATE INDEX IF NOT EXISTS "idx_pacs_int_log_outcome_action" ON "public"."pacs_integration_log" ("outcome_enum", "action_code");
