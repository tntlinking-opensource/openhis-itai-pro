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
    "signature" text,
    "kpd_code" varchar(64),
    "pos_no" varchar(64),
    CONSTRAINT "adm_practitioner_pkey" PRIMARY KEY ("id")
);

COMMENT ON TABLE adm_practitioner IS '管理域 practitioner';

COMMENT ON COLUMN adm_practitioner.id IS '主键';
COMMENT ON COLUMN adm_practitioner.tenant_id IS '租户标识';
COMMENT ON COLUMN adm_practitioner.created_at IS '创建时间';
COMMENT ON COLUMN adm_practitioner.updated_at IS '最后更新时间';
COMMENT ON COLUMN adm_practitioner.created_by IS '创建人';
COMMENT ON COLUMN adm_practitioner.updated_by IS '最后更新人';
COMMENT ON COLUMN adm_practitioner.is_deleted IS '软删标记';
COMMENT ON COLUMN adm_practitioner.deleted_at IS '删除时间';
COMMENT ON COLUMN adm_practitioner.deleted_by IS '删除人';
COMMENT ON COLUMN adm_practitioner.version IS '乐观锁版本号';
COMMENT ON COLUMN adm_practitioner.name IS 'name';
COMMENT ON COLUMN adm_practitioner.name_json IS 'name json';
COMMENT ON COLUMN adm_practitioner.py_str IS 'py str';
COMMENT ON COLUMN adm_practitioner.wb_str IS 'wb str';
COMMENT ON COLUMN adm_practitioner.gender_enum IS 'gender enum';
COMMENT ON COLUMN adm_practitioner.birth_date IS 'birth date';
COMMENT ON COLUMN adm_practitioner.deceased_date IS 'deceased date';
COMMENT ON COLUMN adm_practitioner.phone IS 'phone';
COMMENT ON COLUMN adm_practitioner.address_province IS 'address province';
COMMENT ON COLUMN adm_practitioner.address_city IS 'address city';
COMMENT ON COLUMN adm_practitioner.address_district IS 'address district';
COMMENT ON COLUMN adm_practitioner.address_street IS 'address street';
COMMENT ON COLUMN adm_practitioner.address_json IS 'address json';
COMMENT ON COLUMN adm_practitioner.status_enum IS 'status enum';
COMMENT ON COLUMN adm_practitioner.active_flag IS 'active flag';
COMMENT ON COLUMN adm_practitioner.bus_no IS 'bus no';
COMMENT ON COLUMN adm_practitioner.yb_no IS 'yb no';
COMMENT ON COLUMN adm_practitioner.user_id IS 'user id';
COMMENT ON COLUMN adm_practitioner.org_id IS 'org id';
COMMENT ON COLUMN adm_practitioner.dr_profttl_code IS 'dr profttl code';
COMMENT ON COLUMN adm_practitioner.phar_prac_cert_no IS 'phar prac cert no';
COMMENT ON COLUMN adm_practitioner.prsc_dr_cert_code IS 'prsc dr cert code';
COMMENT ON COLUMN adm_practitioner.signature IS '医务人员聚合根实体';
COMMENT ON COLUMN adm_practitioner.kpd_code IS 'kpd code';
COMMENT ON COLUMN adm_practitioner.pos_no IS 'pos no';

-- seed: adm_practitioner
INSERT INTO "adm_practitioner" ("id", "tenant_id", "created_at", "updated_at", "created_by", "updated_by", "is_deleted", "deleted_at", "deleted_by", "version", "name", "name_json", "py_str", "wb_str", "gender_enum", "birth_date", "deceased_date", "phone", "address_province", "address_city", "address_district", "address_street", "address_json", "status_enum", "active_flag", "bus_no", "yb_no", "user_id", "org_id", "dr_profttl_code", "phar_prac_cert_no", "prsc_dr_cert_code", "signature", "kpd_code", "pos_no")
VALUES
('308791351298756608', '1', '2026-06-10 03:23:06.648+00', '2026-06-10 05:11:05.910+00', '1', '1', false, NULL, NULL, '0', 'admin', NULL, NULL, NULL, 'UNKNOWN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'ACTIVE', true, NULL, NULL, '1', '322663218269917184', NULL, NULL, NULL, NULL, NULL, NULL),
('322932010683207680', '1', '2026-06-10 02:56:31.133+00', '2026-06-10 04:53:46.571+00', '1', '1', false, NULL, NULL, '0', '药房', NULL, NULL, NULL, 'FEMALE', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'ACTIVE', true, NULL, NULL, '322932010666430464', NULL, NULL, NULL, NULL, NULL, NULL, NULL),
('322932096108597248', '1', '2026-06-10 02:56:51.500+00', '2026-06-10 02:57:30.134+00', '1', '1', false, NULL, NULL, '0', '库管', NULL, NULL, NULL, 'FEMALE', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'ACTIVE', true, NULL, NULL, '322932096091820032', NULL, NULL, NULL, NULL, NULL, NULL, NULL),
('322932211326128128', '1', '2026-06-10 02:57:18.970+00', '2026-06-10 05:38:13.796+00', '1', '1', false, NULL, NULL, '0', '收费', NULL, NULL, NULL, 'FEMALE', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'ACTIVE', true, NULL, NULL, '322932211309350912', '322663815429754880', NULL, NULL, NULL, NULL, NULL, NULL),
('322935698173202432', '1', '2026-06-10 03:11:10.299+00', '2026-06-10 04:53:21.933+00', '1', '1', false, NULL, NULL, '0', '住院医生', NULL, NULL, NULL, 'MALE', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'ACTIVE', true, NULL, NULL, '322935698131259392', '322663308812357632', NULL, NULL, NULL, NULL, NULL, NULL),
('322963602326687744', '1', '2026-06-10 05:02:03.168+00', '2026-06-10 05:36:51.309+00', '1', '1', false, NULL, NULL, '0', '门诊医生', NULL, NULL, NULL, 'MALE', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'ACTIVE', true, NULL, NULL, '322963602154721280', '322663218269917184', NULL, NULL, NULL, NULL, NULL, NULL),
('322965022887120896', '1', '2026-06-10 05:07:41.856+00', '2026-06-10 05:36:43.288+00', '1', '1', false, NULL, NULL, '0', '护士', NULL, NULL, NULL, 'FEMALE', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'ACTIVE', true, NULL, NULL, '322965022853566464', '322663652522987520', NULL, NULL, NULL, NULL, NULL, NULL),
('323022664260128768', '1', '2026-06-10 08:56:44.630+00', '2026-06-10 08:56:44.630+00', '1', '1', false, NULL, NULL, '0', '信息科', NULL, NULL, NULL, 'MALE', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'ACTIVE', true, 'PR0015', NULL, '323022664197214208', NULL, NULL, NULL, NULL, NULL, NULL, NULL)
ON CONFLICT ("id") DO NOTHING;
