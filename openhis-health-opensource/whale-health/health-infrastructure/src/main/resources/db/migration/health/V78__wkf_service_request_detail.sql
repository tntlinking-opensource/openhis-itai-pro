-- wkf_service_request_detail（建表）
-- Flyway V78

CREATE TABLE IF NOT EXISTS "public"."wkf_service_request_detail" (
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
    "service_req_id" int8 NOT NULL,
    "type_code" varchar(255),
    "order_detail_table" varchar(255),
    "order_detail_id" int8,
    "detail_json" varchar(255),
    CONSTRAINT "wkf_service_request_detail_pkey" PRIMARY KEY ("id")
);