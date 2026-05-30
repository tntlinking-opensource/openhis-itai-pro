-- wkf_activity_definition（建表 + 种子数据）
-- Flyway V68

CREATE TABLE IF NOT EXISTS "public"."wkf_activity_definition" (
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
    "category_code" varchar(255) NOT NULL,
    "bus_no" varchar(255) NOT NULL,
    "name" varchar(255) NOT NULL,
    "py_str" varchar(255),
    "wb_str" varchar(255),
    "package_unit_code" varchar(255),
    "type_enum" varchar(255) NOT NULL,
    "status_enum" varchar(255) NOT NULL,
    "description_text" varchar(255),
    "body_site_code" varchar(255),
    "specimen_code" varchar(255),
    "org_id" int8,
    "location_id" int8,
    "rule_id" int4,
    "pricing_flag" bool,
    "yb_flag" bool,
    "yb_no" varchar(255),
    "yb_match_flag" bool,
    "chrgitm_lv" int4,
    "children_json" varchar(255),
    "children_flag" bool,
    "activity_version" varchar(255),
    "merchandise_name" varchar(255),
    "merchandise_py_str" varchar(255),
    "merchandise_wb_str" varchar(255),
    "def_encounter_unit_code" varchar(255),
    "def_inhospital_unit_code" varchar(255),
    CONSTRAINT "wkf_activity_definition_pkey" PRIMARY KEY ("id")
);

-- seed: wkf_activity_definition
INSERT INTO "wkf_activity_definition" ("id", "tenant_id", "created_at", "updated_at", "created_by", "updated_by", "is_deleted", "deleted_at", "deleted_by", "version", "category_code", "bus_no", "name", "py_str", "wb_str", "package_unit_code", "type_enum", "status_enum", "description_text", "body_site_code", "specimen_code", "org_id", "location_id", "rule_id", "pricing_flag", "yb_flag", "yb_no", "yb_match_flag", "chrgitm_lv", "children_json", "children_flag", "activity_version", "merchandise_name", "merchandise_py_str", "merchandise_wb_str", "def_encounter_unit_code", "def_inhospital_unit_code")
VALUES
(318616090499878912, 1, '2026-05-29 05:06:35.575+00', '2026-05-29 05:06:35.589+00', 1, 1, false, NULL, NULL, 0, 22, 'AD00000009', '肾功3项', 'SG3X', 'JA3A', 6, 1, 'ACTIVE', NULL, NULL, NULL, 6, NULL, 1004, NULL, NULL, NULL, NULL, NULL, NULL, false, NULL, NULL, NULL, NULL, NULL, NULL),
(318616275237998592, 1, '2026-05-29 05:07:19.620+00', '2026-05-29 05:07:19.625+00', 1, 1, false, NULL, NULL, 0, 21, 'AD00000010', '静脉输液', 'JMSY', 'GELI', 6, 3, 'ACTIVE', NULL, NULL, NULL, 6, NULL, 1008, NULL, NULL, NULL, NULL, NULL, NULL, false, NULL, NULL, NULL, NULL, NULL, NULL)
ON CONFLICT ("id") DO NOTHING;
