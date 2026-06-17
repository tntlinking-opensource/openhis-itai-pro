-- adm_account（建表）
-- Flyway V2

CREATE TABLE IF NOT EXISTS "public"."adm_account" (
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
    "encounter_id" int8 NOT NULL,
    "type_code" varchar(255) NOT NULL,
    "bus_no" varchar(255),
    "name" varchar(255),
    "balance" numeric(19,6),
    "billing_status_enum" varchar(255),
    "contract_no" varchar(255),
    "yb_area_no" varchar(255),
    "encounter_flag" bool,
    "insutype" varchar(255),
    "default_flag" bool,
    "status_enum" varchar(255),
    CONSTRAINT "adm_account_pkey" PRIMARY KEY ("id")
);

COMMENT ON TABLE adm_account IS '管理域 account';

COMMENT ON COLUMN adm_account.id IS '主键';
COMMENT ON COLUMN adm_account.tenant_id IS '租户标识';
COMMENT ON COLUMN adm_account.created_at IS '创建时间';
COMMENT ON COLUMN adm_account.updated_at IS '最后更新时间';
COMMENT ON COLUMN adm_account.created_by IS '创建人';
COMMENT ON COLUMN adm_account.updated_by IS '最后更新人';
COMMENT ON COLUMN adm_account.is_deleted IS '软删标记';
COMMENT ON COLUMN adm_account.deleted_at IS '删除时间';
COMMENT ON COLUMN adm_account.deleted_by IS '删除人';
COMMENT ON COLUMN adm_account.version IS '乐观锁版本号';
COMMENT ON COLUMN adm_account.patient_id IS '就诊账户聚合根：与单次就诊关联的分账账户（如自费/医保等）';
COMMENT ON COLUMN adm_account.encounter_id IS 'encounter 标识';
COMMENT ON COLUMN adm_account.type_code IS 'type 代码';
COMMENT ON COLUMN adm_account.bus_no IS '业务流水号（新建必填）';
COMMENT ON COLUMN adm_account.name IS '名称';
COMMENT ON COLUMN adm_account.balance IS 'balance';
COMMENT ON COLUMN adm_account.billing_status_enum IS '结账状态（{ com.openhis.health.domain.share.enums.AccountBillingStatus#getCode()}）';
COMMENT ON COLUMN adm_account.contract_no IS 'contract 编号';
COMMENT ON COLUMN adm_account.yb_area_no IS 'ybArea 编号';
COMMENT ON COLUMN adm_account.encounter_flag IS 'encounter 标志位';
COMMENT ON COLUMN adm_account.insutype IS 'insutype';
COMMENT ON COLUMN adm_account.default_flag IS 'default 标志位';
COMMENT ON COLUMN adm_account.status_enum IS '账户主状态（有效/无效，{ com.openhis.health.domain.share.enums.PublicationStatus#getCode()}）';

