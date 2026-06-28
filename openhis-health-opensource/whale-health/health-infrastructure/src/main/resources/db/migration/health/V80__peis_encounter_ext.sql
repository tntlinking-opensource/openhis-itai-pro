-- peis_encounter_ext（建表）
-- Flyway V80 · DM-PEIS-007

CREATE TABLE IF NOT EXISTS "public"."peis_encounter_ext" (
    "encounter_id" int8 NOT NULL,
    "tenant_id" int8,
    "org_id" int8,
    "created_at" timestamptz(6) NOT NULL,
    "updated_at" timestamptz(6),
    "created_by" int8,
    "updated_by" int8,
    "is_deleted" bool NOT NULL DEFAULT false,
    "deleted_at" timestamptz(6),
    "deleted_by" int8,
    "version" int4 NOT NULL DEFAULT 0,
    "package_activity_id" int8,
    "package_snapshot_json" text,
    "group_flag" bool NOT NULL DEFAULT false,
    "company_id" int8,
    "contract_id" int8,
    "group_batch_id" int8,
    "booking_appointment_id" int8,
    "bus_no" varchar(255),
    CONSTRAINT "peis_encounter_ext_pkey" PRIMARY KEY ("encounter_id")
);
CREATE INDEX IF NOT EXISTS "idx_peis_enc_ext_bus_no"
    ON "public"."peis_encounter_ext" ("bus_no")
    WHERE "is_deleted" = false;

COMMENT ON TABLE peis_encounter_ext IS 'Encounter(PE) 体检扩展（个检/团检）';
COMMENT ON COLUMN peis_encounter_ext.encounter_id IS '就诊标识（主键）';
COMMENT ON COLUMN peis_encounter_ext.tenant_id IS '租户标识';
COMMENT ON COLUMN peis_encounter_ext.org_id IS '机构标识';
COMMENT ON COLUMN peis_encounter_ext.created_at IS '创建时间';
COMMENT ON COLUMN peis_encounter_ext.updated_at IS '最后更新时间';
COMMENT ON COLUMN peis_encounter_ext.created_by IS '创建人';
COMMENT ON COLUMN peis_encounter_ext.updated_by IS '最后更新人';
COMMENT ON COLUMN peis_encounter_ext.is_deleted IS '软删标记';
COMMENT ON COLUMN peis_encounter_ext.deleted_at IS '删除时间';
COMMENT ON COLUMN peis_encounter_ext.deleted_by IS '删除人';
COMMENT ON COLUMN peis_encounter_ext.version IS '乐观锁版本号';
COMMENT ON COLUMN peis_encounter_ext.package_activity_id IS '套餐活动定义标识';
COMMENT ON COLUMN peis_encounter_ext.package_snapshot_json IS '套餐快照 JSON';
COMMENT ON COLUMN peis_encounter_ext.group_flag IS '是否团检';
COMMENT ON COLUMN peis_encounter_ext.company_id IS '单位标识';
COMMENT ON COLUMN peis_encounter_ext.contract_id IS '合约标识';
COMMENT ON COLUMN peis_encounter_ext.group_batch_id IS '团检批次标识';
COMMENT ON COLUMN peis_encounter_ext.booking_appointment_id IS '预约队列标识';
COMMENT ON COLUMN peis_encounter_ext.bus_no IS '业务编号';
