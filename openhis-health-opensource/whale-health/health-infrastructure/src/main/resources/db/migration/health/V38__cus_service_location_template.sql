-- cus_service_location_template（建表）
-- Flyway V38

CREATE TABLE IF NOT EXISTS "public"."cus_service_location_template" (
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
    "organization_id" int8 NOT NULL,
    "item_table" varchar(255) NOT NULL,
    "distribution_category_code" varchar(255) NOT NULL,
    "def_location_id" int8 NOT NULL,
    "item_definition_id" int8,
    "method_code" varchar(255),
    "start_time" time(6),
    "end_time" time(6),
    "priority" int4 NOT NULL,
    "display_order" int4,
    "remark" varchar(255),
    CONSTRAINT "cus_service_location_template_pkey" PRIMARY KEY ("id")
);

INSERT INTO "public"."cus_service_location_template" ("id", "tenant_id", "created_at", "updated_at", "created_by", "updated_by", "is_deleted", "deleted_at", "deleted_by", "version", "organization_id", "item_table", "distribution_category_code", "def_location_id", "item_definition_id", "method_code", "start_time", "end_time", "priority", "display_order", "remark") VALUES (4400000000000000001, 1, '2026-05-30 16:03:48.837731+08', '2026-05-30 16:03:48.837731+08', 1, 1, 'f', NULL, NULL, 0, 6, 'med_medication_definition', '2', 4200000000000000002, NULL, NULL, NULL, NULL, 1, 1, '');
INSERT INTO "public"."cus_service_location_template" ("id", "tenant_id", "created_at", "updated_at", "created_by", "updated_by", "is_deleted", "deleted_at", "deleted_by", "version", "organization_id", "item_table", "distribution_category_code", "def_location_id", "item_definition_id", "method_code", "start_time", "end_time", "priority", "display_order", "remark") VALUES (4400000000000000002, 1, '2026-05-30 16:03:48.837731+08', '2026-05-30 16:03:48.837731+08', 1, 1, 'f', NULL, NULL, 0, 6, 'adm_device_definition', '11', 4200000000000000002, NULL, NULL, NULL, NULL, 1, 1, '');
