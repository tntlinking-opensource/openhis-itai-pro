-- adm_patient（建表）
-- Flyway V17

CREATE TABLE IF NOT EXISTS "public"."adm_patient" (
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
    "bus_no" varchar(255) NOT NULL,
    "name" varchar(255) NOT NULL,
    "name_json" varchar(255),
    "gender_enum" varchar(255) NOT NULL,
    "birth_date" date,
    "status_enum" varchar(255) NOT NULL,
    "active_flag" bool NOT NULL,
    "temp_flag" bool NOT NULL,
    "id_card" varchar(255),
    "marital_status_enum" varchar(255),
    "prfs_enum" varchar(255),
    "phone" varchar(255),
    "address" varchar(255),
    "address_province" varchar(255),
    "address_city" varchar(255),
    "address_district" varchar(255),
    "address_street" varchar(255),
    "address_extend_json" varchar(255),
    "nationality_code" varchar(255),
    "py_str" varchar(255),
    "wb_str" varchar(255),
    "blood_type_abo" int4,
    "blood_type_rh" int4,
    "work_company" varchar(255),
    "native_place" varchar(255),
    "country_code" varchar(255),
    "link_name" varchar(255),
    "link_relation_code" int4,
    "link_telcom" varchar(255),
    "link_jsons" varchar(255),
    "deceased_date" timestamptz(6),
    "deceased_reason" varchar(255),
    "organization_id" int8 NOT NULL,
    "merged_target_patient_id" int8,
    "merged_at" timestamptz(6),
    CONSTRAINT "adm_patient_pkey" PRIMARY KEY ("id")
);

COMMENT ON TABLE adm_patient IS '管理域 patient';

COMMENT ON COLUMN adm_patient.id IS '主键';
COMMENT ON COLUMN adm_patient.tenant_id IS '租户标识';
COMMENT ON COLUMN adm_patient.created_at IS '创建时间';
COMMENT ON COLUMN adm_patient.updated_at IS '最后更新时间';
COMMENT ON COLUMN adm_patient.created_by IS '创建人';
COMMENT ON COLUMN adm_patient.updated_by IS '最后更新人';
COMMENT ON COLUMN adm_patient.is_deleted IS '软删标记';
COMMENT ON COLUMN adm_patient.deleted_at IS '删除时间';
COMMENT ON COLUMN adm_patient.deleted_by IS '删除人';
COMMENT ON COLUMN adm_patient.version IS '乐观锁版本号';
COMMENT ON COLUMN adm_patient.bus_no IS '患者聚合根实体';
COMMENT ON COLUMN adm_patient.name IS '患者姓名';
COMMENT ON COLUMN adm_patient.name_json IS '姓名扩展 JSON';
COMMENT ON COLUMN adm_patient.gender_enum IS '性别编码';
COMMENT ON COLUMN adm_patient.birth_date IS '出生日期';
COMMENT ON COLUMN adm_patient.status_enum IS '患者状态编码';
COMMENT ON COLUMN adm_patient.active_flag IS '启用标记';
COMMENT ON COLUMN adm_patient.temp_flag IS '临时建档标记';
COMMENT ON COLUMN adm_patient.id_card IS '身份证号';
COMMENT ON COLUMN adm_patient.marital_status_enum IS '婚姻状态编码';
COMMENT ON COLUMN adm_patient.prfs_enum IS '职业编码';
COMMENT ON COLUMN adm_patient.phone IS '联系电话';
COMMENT ON COLUMN adm_patient.address IS 'address';
COMMENT ON COLUMN adm_patient.address_province IS 'address province';
COMMENT ON COLUMN adm_patient.address_city IS 'address city';
COMMENT ON COLUMN adm_patient.address_district IS 'address district';
COMMENT ON COLUMN adm_patient.address_street IS 'address street';
COMMENT ON COLUMN adm_patient.address_extend_json IS '主要地址';
COMMENT ON COLUMN adm_patient.nationality_code IS '民族编码';
COMMENT ON COLUMN adm_patient.py_str IS '拼音码';
COMMENT ON COLUMN adm_patient.wb_str IS '五笔码';
COMMENT ON COLUMN adm_patient.blood_type_abo IS 'blood type abo';
COMMENT ON COLUMN adm_patient.blood_type_rh IS 'blood type rh';
COMMENT ON COLUMN adm_patient.work_company IS '血型';
COMMENT ON COLUMN adm_patient.native_place IS '籍贯';
COMMENT ON COLUMN adm_patient.country_code IS '国家地区编码';
COMMENT ON COLUMN adm_patient.link_name IS 'link name';
COMMENT ON COLUMN adm_patient.link_relation_code IS 'link relation code';
COMMENT ON COLUMN adm_patient.link_telcom IS 'link telcom';
COMMENT ON COLUMN adm_patient.link_jsons IS '主要联系人';
COMMENT ON COLUMN adm_patient.deceased_date IS '死亡时间';
COMMENT ON COLUMN adm_patient.deceased_reason IS '死亡原因';
COMMENT ON COLUMN adm_patient.organization_id IS '建档机构标识';
COMMENT ON COLUMN adm_patient.merged_target_patient_id IS '合并目标患者标识';
COMMENT ON COLUMN adm_patient.merged_at IS '合并时间';

