-- adm_organization（建表）
-- Flyway V16

CREATE TABLE IF NOT EXISTS "public"."adm_organization" (
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
    "parent_id" int8,
    "type_enum" varchar(255) NOT NULL,
    "class_enum" varchar(255),
    "status_enum" varchar(255) NOT NULL,
    "active_flag" bool NOT NULL,
    "display_order" int4,
    "def_doctor_id" int8,
    "py_str" varchar(255),
    "wb_str" varchar(255),
    "yb_no" varchar(255),
    "yb_name" varchar(255),
    "medins_id" varchar(255),
    "medins_admdvs" varchar(255),
    "medins_type" varchar(255),
    "medins_lv" varchar(255),
    "caty" varchar(255),
    CONSTRAINT "adm_organization_pkey" PRIMARY KEY ("id")
);

INSERT INTO adm_organization (
    id, bus_no, name, parent_id, display_order, active_flag, type_enum, class_enum, status_enum,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted
) VALUES
      (1, 'ROOT', '总部', NULL, 0, true, 'HOSPITAL', 'MANAGER', 'ACTIVE', 1, NOW(), NOW(), 1, 1, false),
      (2, 'BRANCH_01', '第一分院', 1, 1, true, 'HOSPITAL', 'MANAGER', 'ACTIVE', 1, NOW(), NOW(), 1, 1, false),
      (3, 'BRANCH_02', '第二分院', 1, 2, true, 'HOSPITAL', 'MANAGER', 'ACTIVE', 1, NOW(), NOW(), 1, 1, false),
      (4, 'DEPT_INTERNAL', '内科', 2, 1, true, 'DEPARTMENT', 'INPATIENT', 'ACTIVE', 1, NOW(), NOW(), 1, 1, false),
      (5, 'DEPT_SURGERY', '外科', 2, 2, true, 'DEPARTMENT', 'INPATIENT', 'ACTIVE', 1, NOW(), NOW(), 1, 1, false),
      (6, 'DEPT_OUTPATIENT', '门诊部', 3, 1, true, 'OUTPATIENT', 'CLINIC', 'ACTIVE', 1, NOW(), NOW(), 1, 1, false),
      (7, 'DEPT_PHARMACY', '药房', 3, 2, true, 'DEPARTMENT', 'PHARMACY', 'ACTIVE', 1, NOW(), NOW(), 1, 1, false)
    ON CONFLICT (id) DO UPDATE SET
    bus_no = EXCLUDED.bus_no,
                            name = EXCLUDED.name,
                            parent_id = EXCLUDED.parent_id,
                            display_order = EXCLUDED.display_order,
                            active_flag = EXCLUDED.active_flag,
                            type_enum = EXCLUDED.type_enum,
                            class_enum = EXCLUDED.class_enum,
                            status_enum = EXCLUDED.status_enum,
                            tenant_id = EXCLUDED.tenant_id,
                            updated_at = NOW(),
                            updated_by = EXCLUDED.updated_by,
                            is_deleted = false;
