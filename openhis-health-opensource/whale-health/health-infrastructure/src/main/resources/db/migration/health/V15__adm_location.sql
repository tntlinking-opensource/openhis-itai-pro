-- adm_location（建表）
-- Flyway V15

CREATE TABLE IF NOT EXISTS "public"."adm_location" (
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
    "name" varchar(255) NOT NULL,
    "bus_no" varchar(255) NOT NULL,
    "status_enum" varchar(255) NOT NULL,
    "operational_enum" varchar(255),
    "form_enum" varchar(255) NOT NULL,
    "mode_enum" varchar(255) NOT NULL,
    "type_json" varchar(255),
    "organization_id" int8 NOT NULL,
    "display_order" int4,
    "py_str" varchar(255),
    "wb_str" varchar(255),
    "parent_id" int8,
    CONSTRAINT "adm_location_pkey" PRIMARY KEY ("id")
);

INSERT INTO "public"."adm_location" ("id", "tenant_id", "created_at", "updated_at", "created_by", "updated_by", "is_deleted", "deleted_at", "deleted_by", "version", "name", "bus_no", "status_enum", "operational_enum", "form_enum", "mode_enum", "type_json", "organization_id", "display_order", "py_str", "wb_str", "parent_id") VALUES (4200000000000000001, 1, '2026-05-30 15:52:59.345322+08', '2026-05-30 15:52:59.345322+08', 1, 1, 'f', NULL, NULL, 0, '中心总库', 'LOC-WH-0001', 'ACTIVE', 'NOT_IN_USE', 'WAREHOUSE', 'INSTANCE', '{}', 2, 1, 'ZXZK', 'ZXZK', NULL);
INSERT INTO "public"."adm_location" ("id", "tenant_id", "created_at", "updated_at", "created_by", "updated_by", "is_deleted", "deleted_at", "deleted_by", "version", "name", "bus_no", "status_enum", "operational_enum", "form_enum", "mode_enum", "type_json", "organization_id", "display_order", "py_str", "wb_str", "parent_id") VALUES (4200000000000000002, 1, '2026-05-30 15:52:59.345322+08', '2026-05-30 15:52:59.345322+08', 1, 1, 'f', NULL, NULL, 0, '门诊中心药房', 'LOC-PH-0001', 'ACTIVE', 'NOT_IN_USE', 'PHARMACY', 'INSTANCE', '{}', 7, 2, 'MZZXYF', 'MZZXYF', NULL);
INSERT INTO "public"."adm_location" ("id", "tenant_id", "created_at", "updated_at", "created_by", "updated_by", "is_deleted", "deleted_at", "deleted_by", "version", "name", "bus_no", "status_enum", "operational_enum", "form_enum", "mode_enum", "type_json", "organization_id", "display_order", "py_str", "wb_str", "parent_id") VALUES (4200000000000000003, 1, '2026-05-30 15:52:59.345322+08', '2026-05-30 15:52:59.345322+08', 1, 1, 'f', NULL, NULL, 0, '住院药房', 'LOC-PH-0002', 'ACTIVE', 'NOT_IN_USE', 'PHARMACY', 'INSTANCE', '{}', 4, 3, 'ZYYF', 'ZYYF', NULL);
INSERT INTO "public"."adm_location" ("id", "tenant_id", "created_at", "updated_at", "created_by", "updated_by", "is_deleted", "deleted_at", "deleted_by", "version", "name", "bus_no", "status_enum", "operational_enum", "form_enum", "mode_enum", "type_json", "organization_id", "display_order", "py_str", "wb_str", "parent_id") VALUES (4200000000000000011, 1, '2026-05-30 15:52:59.345322+08', '2026-05-30 15:52:59.345322+08', 1, 1, 'f', NULL, NULL, 0, '中心总库-西药区', 'LOC-WH-0001-A', 'ACTIVE', 'NOT_IN_USE', 'WAREHOUSE', 'INSTANCE', '{}', 2, 1, 'ZXZKXYQ', 'ZXZKXYQ', 4200000000000000001);
INSERT INTO "public"."adm_location" ("id", "tenant_id", "created_at", "updated_at", "created_by", "updated_by", "is_deleted", "deleted_at", "deleted_by", "version", "name", "bus_no", "status_enum", "operational_enum", "form_enum", "mode_enum", "type_json", "organization_id", "display_order", "py_str", "wb_str", "parent_id") VALUES (4200000000000000012, 1, '2026-05-30 15:52:59.345322+08', '2026-05-30 15:52:59.345322+08', 1, 1, 'f', NULL, NULL, 0, '中心总库-耗材区', 'LOC-WH-0001-B', 'ACTIVE', 'NOT_IN_USE', 'WAREHOUSE', 'INSTANCE', '{}', 2, 2, 'ZXZKHCQ', 'ZXZKHCQ', 4200000000000000001);
INSERT INTO "public"."adm_location" ("id", "tenant_id", "created_at", "updated_at", "created_by", "updated_by", "is_deleted", "deleted_at", "deleted_by", "version", "name", "bus_no", "status_enum", "operational_enum", "form_enum", "mode_enum", "type_json", "organization_id", "display_order", "py_str", "wb_str", "parent_id") VALUES (4200000000000000013, 1, '2026-05-30 15:52:59.345322+08', '2026-05-30 15:52:59.345322+08', 1, 1, 'f', NULL, NULL, 0, '门诊中心药房-发药窗口1', 'LOC-PH-0001-A', 'ACTIVE', 'NOT_IN_USE', 'PHARMACY', 'INSTANCE', '{}', 7, 1, 'MZZXYFFYCK1', 'MZZXYFFYCK1', 4200000000000000002);
