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
COMMENT ON TABLE adm_organization IS '管理域 organization';
COMMENT ON COLUMN adm_organization.id IS '主键';
COMMENT ON COLUMN adm_organization.tenant_id IS '租户标识';
COMMENT ON COLUMN adm_organization.created_at IS '创建时间';
COMMENT ON COLUMN adm_organization.updated_at IS '最后更新时间';
COMMENT ON COLUMN adm_organization.created_by IS '创建人';
COMMENT ON COLUMN adm_organization.updated_by IS '最后更新人';
COMMENT ON COLUMN adm_organization.is_deleted IS '软删标记';
COMMENT ON COLUMN adm_organization.deleted_at IS '删除时间';
COMMENT ON COLUMN adm_organization.deleted_by IS '删除人';
COMMENT ON COLUMN adm_organization.version IS '乐观锁版本号';
COMMENT ON COLUMN adm_organization.name IS '机构聚合根实体';
COMMENT ON COLUMN adm_organization.bus_no IS 'bus 编号';
COMMENT ON COLUMN adm_organization.parent_id IS 'parent 标识';
COMMENT ON COLUMN adm_organization.type_enum IS 'type 枚举值';
COMMENT ON COLUMN adm_organization.class_enum IS 'class 枚举值';
COMMENT ON COLUMN adm_organization.status_enum IS 'status 枚举值（{ PublicationStatus#getCode()}）';
COMMENT ON COLUMN adm_organization.active_flag IS 'active 标志位';
COMMENT ON COLUMN adm_organization.display_order IS '显示顺序';
COMMENT ON COLUMN adm_organization.def_doctor_id IS 'defDoctor 标识';
COMMENT ON COLUMN adm_organization.py_str IS '拼音码';
COMMENT ON COLUMN adm_organization.wb_str IS '五笔码';
COMMENT ON COLUMN adm_organization.yb_no IS 'yb 编号';
COMMENT ON COLUMN adm_organization.yb_name IS 'yb 名称';
COMMENT ON COLUMN adm_organization.medins_id IS 'medins 标识';
COMMENT ON COLUMN adm_organization.medins_admdvs IS '医疗机构医保区划';
COMMENT ON COLUMN adm_organization.medins_type IS 'medins类型';
COMMENT ON COLUMN adm_organization.medins_lv IS '医疗机构等级';
COMMENT ON COLUMN adm_organization.caty IS '科别';

-- seed: adm_organization
INSERT INTO "adm_organization" ("id", "tenant_id", "created_at", "updated_at", "created_by", "updated_by", "is_deleted", "deleted_at", "deleted_by", "version", "name", "bus_no", "parent_id", "type_enum", "class_enum", "status_enum", "active_flag", "display_order", "def_doctor_id", "py_str", "wb_str", "yb_no", "yb_name", "medins_id", "medins_admdvs", "medins_type", "medins_lv", "caty")
VALUES
('322663105141149696', '1', '2026-06-09 09:07:59.056+00', '2026-06-09 09:07:59.056+00', '1', '1', false, NULL, NULL, 0, '测试医院', 'OR0007', NULL, 'HOSPITAL', NULL, 'ACTIVE', true, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL),
('322663218269917184', '1', '2026-06-09 09:08:26.028+00', '2026-06-09 09:08:26.028+00', '1', '1', false, NULL, NULL, 0, '门诊内科', 'OR0007.OR0008', '322663105141149696', 'DEPARTMENT', 'CLINIC', 'ACTIVE', true, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL),
('322663308812357632', '1', '2026-06-09 09:08:47.615+00', '2026-06-09 09:08:47.615+00', '1', '1', false, NULL, NULL, 0, '住院内科', 'OR0007.OR0009', '322663105141149696', 'DEPARTMENT', 'INPATIENT', 'ACTIVE', true, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL),
('322663606385643520', '1', '2026-06-09 09:09:58.562+00', '2026-06-09 09:37:54.930+00', '1', '1', false, NULL, NULL, 0, '处置室', 'OR0007.OR0010', '322663105141149696', 'DEPARTMENT', 'NS', 'ACTIVE', true, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL),
('322663652522987520', '1', '2026-06-09 09:10:09.562+00', '2026-06-09 09:37:49.884+00', '1', '1', false, NULL, NULL, 0, '护理站', 'OR0007.OR0011', '322663105141149696', 'DEPARTMENT', 'NS', 'ACTIVE', true, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL),
('322663815429754880', '1', '2026-06-09 09:10:48.402+00', '2026-06-09 09:10:48.402+00', '1', '1', false, NULL, NULL, 0, '挂号收费处', 'OR0007.OR0012', '322663105141149696', 'DEPARTMENT', 'FIN', 'ACTIVE', true, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
ON CONFLICT ("id") DO NOTHING;
