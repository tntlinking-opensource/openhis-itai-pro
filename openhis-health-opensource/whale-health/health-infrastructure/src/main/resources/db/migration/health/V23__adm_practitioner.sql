-- adm_practitioner（建表 + 种子数据）
-- Flyway V23

CREATE TABLE IF NOT EXISTS "public"."adm_practitioner" (
    "id" int8 NOT NULL,
    "tenant_id" int8,
    "created_at" timestamptz(6) NOT NULL,
    "updated_at" timestamptz(6),
    "created_by" int8,
    "updated_by" int8,
    "is_deleted" bool NOT NULL DEFAULT false,
    "deleted_at" timestamptz(6),
    "deleted_by" int8,
    "version" int8 NOT NULL DEFAULT 0,

    "name" varchar(255) NOT NULL,
    "name_json" varchar(2000),
    "py_str" varchar(255),
    "wb_str" varchar(255),
    "gender_enum" varchar(255) NOT NULL,
    "birth_date" date,
    "deceased_date" timestamptz(6),
    "phone" varchar(50),

    "address_province" varchar(64),
    "address_city" varchar(64),
    "address_district" varchar(64),
    "address_street" varchar(255),
    "address_json" varchar(2000),

    "status_enum" varchar(255) NOT NULL,
    "active_flag" bool NOT NULL DEFAULT true,

    "bus_no" varchar(64),
    "yb_no" varchar(64),
    "user_id" int8 NOT NULL,
    "org_id" int8,
    "dr_profttl_code" int4,
    "phar_prac_cert_no" varchar(64),
    "prsc_dr_cert_code" varchar(64),
    "signature" varchar(500),
    "kpd_code" varchar(64),
    "pos_no" varchar(64),

    CONSTRAINT "adm_practitioner_pkey" PRIMARY KEY ("id")
);

-- 医生签名图片：Base64 存储（可含 data:image/*;base64, 前缀）
ALTER TABLE adm_practitioner
    ALTER COLUMN signature TYPE TEXT;

COMMENT ON COLUMN adm_practitioner.signature IS '医生签名图片 Base64（可含 data:image/*;base64, 前缀）';

-- seed: adm_practitioner
INSERT INTO "adm_practitioner" ("id", "tenant_id", "created_at", "updated_at", "created_by", "updated_by", "is_deleted", "deleted_at", "deleted_by", "version", "name", "name_json", "py_str", "wb_str", "gender_enum", "birth_date", "deceased_date", "phone", "address_province", "address_city", "address_district", "address_street", "address_json", "status_enum", "active_flag", "bus_no", "yb_no", "user_id", "org_id", "dr_profttl_code", "phar_prac_cert_no", "prsc_dr_cert_code", "signature", "kpd_code", "pos_no")
VALUES
(2, 1, '2026-05-03 06:07:04.583+00', '2026-05-03 06:07:04.583+00', 1, 1, false, NULL, NULL, 0, '张三', NULL, NULL, NULL, 0, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, true, 'P001', NULL, 2, NULL, NULL, NULL, NULL, NULL, NULL, NULL),
(3, 1, '2026-05-03 06:07:04.583+00', '2026-05-03 06:07:04.583+00', 1, 1, false, NULL, NULL, 0, '李四', NULL, NULL, NULL, 0, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, true, 'P002', NULL, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL),
(4, 1, '2026-05-03 06:07:04.583+00', '2026-05-03 06:07:04.583+00', 1, 1, false, NULL, NULL, 0, '王五', NULL, NULL, NULL, 0, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, true, 'P003', NULL, 4, NULL, NULL, NULL, NULL, NULL, NULL, NULL),
(308791351298756608, 1, '2026-05-02 02:26:35.216+00', '2026-05-02 02:26:35.216+00', 1, 1, false, NULL, NULL, 0, 'admin', NULL, NULL, NULL, '', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'ACTIVE', true, NULL, NULL, 1, 6, NULL, NULL, NULL, NULL, NULL, NULL)
ON CONFLICT ("id") DO NOTHING;
