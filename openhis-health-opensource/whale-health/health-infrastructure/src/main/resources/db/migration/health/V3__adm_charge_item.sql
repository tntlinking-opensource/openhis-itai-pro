-- adm_charge_item（建表）
-- Flyway V3

CREATE TABLE IF NOT EXISTS "public"."adm_charge_item" (
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
    "generate_source_enum" varchar(255),
    "context_enum" varchar(255),
    "bus_no" varchar(255),
    "parent_id" int8,
    "prescription_no" varchar(255),
    "patient_id" int8 NOT NULL,
    "encounter_id" int8 NOT NULL,
    "service_table" varchar(255),
    "service_id" int8,
    "account_id" int8,
    "definition_id" int8,
    "status_enum" varchar(255),
    "total_price" numeric(19,6),
    "occurrence_time" timestamptz(6),
    "performer_id" int8,
    "performing_org_id" int8,
    "requesting_org_id" int8,
    "cost_org_id" int8,
    "quantity_value" numeric(18, 4),
    "quantity_unit" varchar(255),
    "unit_price" numeric(19, 6),
    "def_detail_id" int8,
    "base_amount" numeric(19, 6),
    "discount_amount" numeric(19, 6),
    "surcharge_amount" numeric(19, 6),
    "system_discount_price" numeric(19, 6),
    "manual_adjusted_price" numeric(19, 6),
    "override_reason_code" varchar(255),
    "override_reason_text" varchar(255),
    "enterer_id" int8,
    "entered_date" timestamptz(6),
    "product_table" varchar(255),
    "product_id" int8,
    "procedure_id" int8,
    "dispense_table" varchar(255),
    "dispense_id" int8,
    "claim_state_enum" int4,
    "print_count" int4,
    "org_id" int8,
    "refund_id" int8,
    "condition_id" int8,
    "encounter_diagnosis_id" int8,
    "tcm_flag" bool,
    CONSTRAINT "adm_charge_item_pkey" PRIMARY KEY ("id")
);
COMMENT ON TABLE adm_charge_item IS '管理域 charge item';
COMMENT ON COLUMN adm_charge_item.id IS '主键';
COMMENT ON COLUMN adm_charge_item.tenant_id IS '租户标识';
COMMENT ON COLUMN adm_charge_item.created_at IS '创建时间';
COMMENT ON COLUMN adm_charge_item.updated_at IS '最后更新时间';
COMMENT ON COLUMN adm_charge_item.created_by IS '创建人';
COMMENT ON COLUMN adm_charge_item.updated_by IS '最后更新人';
COMMENT ON COLUMN adm_charge_item.is_deleted IS '软删标记';
COMMENT ON COLUMN adm_charge_item.deleted_at IS '删除时间';
COMMENT ON COLUMN adm_charge_item.deleted_by IS '删除人';
COMMENT ON COLUMN adm_charge_item.version IS '乐观锁版本号';
COMMENT ON COLUMN adm_charge_item.generate_source_enum IS '生成来源存储码（DM generate_source_enum）';
COMMENT ON COLUMN adm_charge_item.context_enum IS '收费上下文存储码（DM context_enum）';
COMMENT ON COLUMN adm_charge_item.bus_no IS '业务流水号（DM bus_no）';
COMMENT ON COLUMN adm_charge_item.parent_id IS '父收费明细主键（拆分/关联场景，DM parent_id）';
COMMENT ON COLUMN adm_charge_item.prescription_no IS '处方号展示/追溯（DM prescription_no）';
COMMENT ON COLUMN adm_charge_item.patient_id IS '患者主键（DM patient_id）';
COMMENT ON COLUMN adm_charge_item.encounter_id IS '就诊主键（DM encounter_id）';
COMMENT ON COLUMN adm_charge_item.service_table IS '上游业务表名锚点（BR-CHG-001，DM service_table）';
COMMENT ON COLUMN adm_charge_item.service_id IS '上游业务主键锚点（DM service_id）';
COMMENT ON COLUMN adm_charge_item.account_id IS '账户主键（DM account_id）';
COMMENT ON COLUMN adm_charge_item.definition_id IS '收费项目定义主键（DM definition_id）';
COMMENT ON COLUMN adm_charge_item.status_enum IS '收费明细聚合根（DM-ADM-011 §3.1，表 adm_charge_item）';
COMMENT ON COLUMN adm_charge_item.total_price IS '总价（DM total_price）';
COMMENT ON COLUMN adm_charge_item.occurrence_time IS '费用发生时刻（DM occurrence_time）';
COMMENT ON COLUMN adm_charge_item.performer_id IS '执行人主键（DM performer_id）';
COMMENT ON COLUMN adm_charge_item.performing_org_id IS '执行机构主键（DM performing_org_id）';
COMMENT ON COLUMN adm_charge_item.requesting_org_id IS '申请机构主键（DM requesting_org_id）';
COMMENT ON COLUMN adm_charge_item.cost_org_id IS '成本归属机构主键（DM cost_org_id）';
COMMENT ON COLUMN adm_charge_item.quantity_value IS '数量值（DM quantity_value）';
COMMENT ON COLUMN adm_charge_item.quantity_unit IS '数量单位（DM quantity_unit）';
COMMENT ON COLUMN adm_charge_item.unit_price IS '单价（DM unit_price）';
COMMENT ON COLUMN adm_charge_item.def_detail_id IS '定义明细主键（DM def_detail_id）';
COMMENT ON COLUMN adm_charge_item.base_amount IS '基准金额（DM base_amount）';
COMMENT ON COLUMN adm_charge_item.discount_amount IS '折扣金额（DM discount_amount）';
COMMENT ON COLUMN adm_charge_item.surcharge_amount IS '附加费金额（DM surcharge_amount）';
COMMENT ON COLUMN adm_charge_item.system_discount_price IS '系统折后价（DM system_discount_price）';
COMMENT ON COLUMN adm_charge_item.manual_adjusted_price IS '手工调价后金额（DM manual_adjusted_price）';
COMMENT ON COLUMN adm_charge_item.override_reason_code IS '改价原因码（DM override_reason_code）';
COMMENT ON COLUMN adm_charge_item.override_reason_text IS '改价原因说明（DM override_reason_text）';
COMMENT ON COLUMN adm_charge_item.enterer_id IS '录入人主键（DM enterer_id）';
COMMENT ON COLUMN adm_charge_item.entered_date IS '录入时间（DM entered_date）';
COMMENT ON COLUMN adm_charge_item.product_table IS '产品来源表名（DM product_table）';
COMMENT ON COLUMN adm_charge_item.product_id IS '产品主键（DM product_id）';
COMMENT ON COLUMN adm_charge_item.procedure_id IS '处置/手术类关联主键（DM procedure_id）';
COMMENT ON COLUMN adm_charge_item.dispense_table IS '发药来源表名（DM dispense_table）';
COMMENT ON COLUMN adm_charge_item.dispense_id IS '发药主键（DM dispense_id）';
COMMENT ON COLUMN adm_charge_item.claim_state_enum IS '理赔状态枚举（DM claim_state_enum）';
COMMENT ON COLUMN adm_charge_item.print_count IS '打印次数（DM print_count）';
COMMENT ON COLUMN adm_charge_item.org_id IS '开立机构主键（DM org_id）';
COMMENT ON COLUMN adm_charge_item.refund_id IS '退费关联主键（DM refund_id）';
COMMENT ON COLUMN adm_charge_item.condition_id IS '病情/诊断条件关联（DM condition_id）';
COMMENT ON COLUMN adm_charge_item.encounter_diagnosis_id IS '就诊诊断关联主键（DM encounter_diagnosis_id）';
COMMENT ON COLUMN adm_charge_item.tcm_flag IS '是否中医相关标记（DM tcm_flag）';
