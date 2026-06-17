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

COMMENT ON TABLE adm_healthcare_service IS '管理域 healthcare service';

COMMENT ON COLUMN adm_healthcare_service.id IS '主键';
COMMENT ON COLUMN adm_healthcare_service.tenant_id IS '租户标识';
COMMENT ON COLUMN adm_healthcare_service.created_at IS '创建时间';
COMMENT ON COLUMN adm_healthcare_service.updated_at IS '最后更新时间';
COMMENT ON COLUMN adm_healthcare_service.created_by IS '创建人';
COMMENT ON COLUMN adm_healthcare_service.updated_by IS '最后更新人';
COMMENT ON COLUMN adm_healthcare_service.is_deleted IS '软删标记';
COMMENT ON COLUMN adm_healthcare_service.deleted_at IS '删除时间';
COMMENT ON COLUMN adm_healthcare_service.deleted_by IS '删除人';
COMMENT ON COLUMN adm_healthcare_service.version IS '乐观锁版本号';
COMMENT ON COLUMN adm_healthcare_service.name IS '医疗服务聚合根，对应表 { adm_healthcare_service}';
COMMENT ON COLUMN adm_healthcare_service.type_code IS '服务类型编码（type_code），区分普通门诊、专家门诊等服务类型';
COMMENT ON COLUMN adm_healthcare_service.category_code IS '服务类别编码（category_code），区分门诊、住院等服务大类';
COMMENT ON COLUMN adm_healthcare_service.offered_org_id IS '提供该服务的机构主键（offered_org_id），关联 { adm_organization}';
COMMENT ON COLUMN adm_healthcare_service.specialty_code IS '专科码（specialty_code），如"CARDIOLOGY""SURGERY"，可为空';
COMMENT ON COLUMN adm_healthcare_service.location_id IS '服务所在地点主键，关联 { adm_location}，可为空';
COMMENT ON COLUMN adm_healthcare_service.status_enum IS '服务状态码（status_enum，String code）';
COMMENT ON COLUMN adm_healthcare_service.active_flag IS '服务是否处于激活可用状态，与 statusEnum=ACTIVE 联动维护';
COMMENT ON COLUMN adm_healthcare_service.appointment_required_flag IS '预约是否必填标志，true 表示患者必须预约后才可就诊';
COMMENT ON COLUMN adm_healthcare_service.yb_no IS '医保编号（yb_no），对接医保结算使用，可为空';
COMMENT ON COLUMN adm_healthcare_service.bus_no IS '业务编号（bus_no），系统内唯一标识医疗服务的人读编码，可为空';
COMMENT ON COLUMN adm_healthcare_service.comment IS '备注说明，对外展示的补充描述，可为空';
COMMENT ON COLUMN adm_healthcare_service.extra_details IS '扩展详情（JSON 字符串），存储结构化附加信息，可为空';
COMMENT ON COLUMN adm_healthcare_service.contact IS '联系方式（JSON 字符串），医疗服务对外联系信息，可为空';

-- seed: adm_healthcare_service
INSERT INTO "adm_healthcare_service" ("id", "tenant_id", "created_at", "updated_at", "created_by", "updated_by", "is_deleted", "deleted_at", "deleted_by", "version", "name", "type_code", "category_code", "offered_org_id", "specialty_code", "location_id", "status_enum", "active_flag", "appointment_required_flag", "yb_no", "bus_no", "comment", "extra_details", "contact")
VALUES
('322957224354254848', '1', '2026-06-10 04:36:42.541+00', '2026-06-10 06:57:21.250+00', '1', '1', false, NULL, NULL, 0, '便民号', '1', NULL, NULL, NULL, NULL, 'ACTIVE', true, NULL, '001', 'HS0001', NULL, NULL, NULL),
('322957264586018816', '1', '2026-06-10 04:36:52.133+00', '2026-06-10 06:57:31.630+00', '1', '1', false, NULL, NULL, 0, '普通号', '1', NULL, NULL, NULL, NULL, 'ACTIVE', true, NULL, '002', 'HS0002', NULL, NULL, NULL),
('322957296852799488', '1', '2026-06-10 04:36:59.826+00', '2026-06-10 06:57:36.527+00', '1', '1', false, NULL, NULL, 0, '专家号', '1', NULL, NULL, NULL, NULL, 'ACTIVE', true, NULL, '003', 'HS0003', NULL, NULL, NULL)
ON CONFLICT ("id") DO NOTHING;
