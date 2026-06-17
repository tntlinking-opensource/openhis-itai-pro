-- adm_encounter（建表）
-- Flyway V6

CREATE TABLE IF NOT EXISTS "public"."adm_encounter" (
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
    "patient_id" int8 NOT NULL,
    "group_id" int8,
    "bus_no" varchar(255) NOT NULL,
    "status_enum" varchar(255) NOT NULL,
    "class_enum" varchar(255) NOT NULL,
    "yb_class_enum" varchar(255),
    "yb_class_text" varchar(255),
    "class_json" varchar(255),
    "priority_enum" varchar(255),
    "type_enum" varchar(255),
    "service_type_id" int8,
    "subject_status_enum" varchar(255),
    "start_time" timestamptz(6),
    "end_time" timestamptz(6),
    "organization_id" int8 NOT NULL,
    "display_order" int4,
    "first_enum" varchar(255),
    "admit_source_code" varchar(255),
    "in_way_code" varchar(255),
    "reception_time" timestamptz(6),
    "registrar_id" int8,
    "amb_encounter_id" int8,
    "out_way_code" varchar(255),
    CONSTRAINT "adm_encounter_pkey" PRIMARY KEY ("id")
);

COMMENT ON TABLE adm_encounter IS '管理域 encounter';

COMMENT ON COLUMN adm_encounter.id IS '主键';
COMMENT ON COLUMN adm_encounter.tenant_id IS '租户标识';
COMMENT ON COLUMN adm_encounter.created_at IS '创建时间';
COMMENT ON COLUMN adm_encounter.updated_at IS '最后更新时间';
COMMENT ON COLUMN adm_encounter.created_by IS '创建人';
COMMENT ON COLUMN adm_encounter.updated_by IS '最后更新人';
COMMENT ON COLUMN adm_encounter.is_deleted IS '软删标记';
COMMENT ON COLUMN adm_encounter.deleted_at IS '删除时间';
COMMENT ON COLUMN adm_encounter.deleted_by IS '删除人';
COMMENT ON COLUMN adm_encounter.version IS '乐观锁版本号';
COMMENT ON COLUMN adm_encounter.patient_id IS '就诊聚合根实体';
COMMENT ON COLUMN adm_encounter.group_id IS 'group 标识';
COMMENT ON COLUMN adm_encounter.bus_no IS 'bus 编号';
COMMENT ON COLUMN adm_encounter.status_enum IS 'status 枚举值';
COMMENT ON COLUMN adm_encounter.class_enum IS 'class 枚举值';
COMMENT ON COLUMN adm_encounter.yb_class_enum IS 'ybClass 枚举值';
COMMENT ON COLUMN adm_encounter.yb_class_text IS 'ybClassText';
COMMENT ON COLUMN adm_encounter.class_json IS 'class JSON 快照';
COMMENT ON COLUMN adm_encounter.priority_enum IS 'priority 枚举值';
COMMENT ON COLUMN adm_encounter.type_enum IS 'type 枚举值';
COMMENT ON COLUMN adm_encounter.service_type_id IS 'serviceType 标识';
COMMENT ON COLUMN adm_encounter.subject_status_enum IS 'subjectStatus 枚举值';
COMMENT ON COLUMN adm_encounter.start_time IS 'start 时间';
COMMENT ON COLUMN adm_encounter.end_time IS 'end 时间';
COMMENT ON COLUMN adm_encounter.organization_id IS 'organization 标识';
COMMENT ON COLUMN adm_encounter.display_order IS 'displayOrder';
COMMENT ON COLUMN adm_encounter.first_enum IS 'first 枚举值';
COMMENT ON COLUMN adm_encounter.admit_source_code IS 'admitSource 代码';
COMMENT ON COLUMN adm_encounter.in_way_code IS 'inWay 代码';
COMMENT ON COLUMN adm_encounter.reception_time IS 'reception 时间';
COMMENT ON COLUMN adm_encounter.registrar_id IS 'registrar 标识';
COMMENT ON COLUMN adm_encounter.amb_encounter_id IS 'ambEncounter 标识';
COMMENT ON COLUMN adm_encounter.out_way_code IS 'outWay 代码';

