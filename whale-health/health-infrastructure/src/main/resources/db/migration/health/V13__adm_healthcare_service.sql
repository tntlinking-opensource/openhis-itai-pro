-- adm_healthcare_service（建表 + 种子数据）
-- Flyway V13

CREATE TABLE IF NOT EXISTS "public"."adm_healthcare_service" (
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
    "type_code" varchar(255) NOT NULL,
    "category_code" varchar(255),
    "offered_org_id" int8,
    "specialty_code" varchar(255),
    "location_id" int8,
    "status_enum" varchar(255) NOT NULL,
    "active_flag" bool NOT NULL,
    "appointment_required_flag" bool,
    "yb_no" varchar(255),
    "bus_no" varchar(255),
    "comment" varchar(255),
    "extra_details" varchar(255),
    "contact" varchar(255),
    CONSTRAINT "adm_healthcare_service_pkey" PRIMARY KEY ("id")
);

-- seed: adm_healthcare_service
INSERT INTO "adm_healthcare_service" ("id", "tenant_id", "created_at", "updated_at", "created_by", "updated_by", "is_deleted", "deleted_at", "deleted_by", "version", "name", "type_code", "category_code", "offered_org_id", "specialty_code", "location_id", "status_enum", "active_flag", "appointment_required_flag", "yb_no", "bus_no", "comment", "extra_details", "contact")
VALUES
(3300000000000000001, 1, '2026-05-03 06:07:04.578+00', '2026-05-03 06:07:04.578+00', 1, 1, false, NULL, NULL, 0, '普通门诊', 1, 1, NULL, NULL, NULL, 1, true, NULL, NULL, 'REG_NORMAL', NULL, NULL, NULL),
(3300000000000000002, 1, '2026-05-03 06:07:04.578+00', '2026-05-03 06:07:04.578+00', 1, 1, false, NULL, NULL, 0, '专家门诊', 2, 1, NULL, NULL, NULL, 1, true, NULL, NULL, 'REG_EXPERT', NULL, NULL, NULL),
(3300000000000000003, 1, '2026-05-03 06:07:04.578+00', '2026-05-03 06:07:04.578+00', 1, 1, false, NULL, NULL, 0, '便民门诊', 3, 1, NULL, NULL, NULL, 1, true, NULL, NULL, 'REG_CONVENIENCE', NULL, NULL, NULL)
ON CONFLICT ("id") DO NOTHING;
