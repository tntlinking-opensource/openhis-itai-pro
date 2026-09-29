-- Squashed V2 adm_ddl

-- from V2__adm_account.sql
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
COMMENT ON COLUMN adm_account.balance IS '余额';
COMMENT ON COLUMN adm_account.billing_status_enum IS '结账状态（{ com.openhis.health.domain.share.enums.AccountBillingStatus#getCode()}）';
COMMENT ON COLUMN adm_account.contract_no IS 'contract 编号';
COMMENT ON COLUMN adm_account.yb_area_no IS 'ybArea 编号';
COMMENT ON COLUMN adm_account.encounter_flag IS 'encounter 标志位';
COMMENT ON COLUMN adm_account.insutype IS '险种类型';
COMMENT ON COLUMN adm_account.default_flag IS 'default 标志位';
COMMENT ON COLUMN adm_account.status_enum IS '账户主状态（有效/无效，{ com.openhis.health.domain.share.enums.PublicationStatus#getCode()}）';

-- from V3__adm_charge_item.sql
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
    "fee_confirmer_id" int8,
    "fee_confirmed_at" timestamptz(6),
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
    "encounter_diagnosis_ids" varchar(2000),
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
COMMENT ON COLUMN adm_charge_item.fee_confirmer_id IS '医技确费人主键（DM fee_confirmer_id；空=未确费）';
COMMENT ON COLUMN adm_charge_item.fee_confirmed_at IS '医技确费时刻（DM fee_confirmed_at）';
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
COMMENT ON COLUMN adm_charge_item.encounter_diagnosis_ids IS '绑定就诊诊断主键 CSV（有序，首位为主诊断 adm_encounter_diagnosis.id；挂费自请求拷贝）';
COMMENT ON COLUMN adm_charge_item.tcm_flag IS '是否中医相关标记（DM tcm_flag）';

-- from V4__adm_charge_item_definition.sql
-- adm_charge_item_definition（建表 + 种子数据）
-- Flyway V4

CREATE TABLE IF NOT EXISTS "public"."adm_charge_item_definition" (
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
    "instance_table" varchar(255),
    "instance_id" int8 NOT NULL,
    "charge_name" varchar(255),
    "status_enum" varchar(255) NOT NULL DEFAULT 'ACTIVE',
    "org_id" int8,
    "type_code" varchar(255) NOT NULL,
    "yb_type" varchar(255),
    "price_type_code" varchar(255) NOT NULL,
    "lot_no" varchar(255),
    "price_value" numeric(19,6) NOT NULL,
    "priority" int4,
    CONSTRAINT "adm_charge_item_definition_pkey" PRIMARY KEY ("id")
);
COMMENT ON TABLE adm_charge_item_definition IS '管理域 charge item definition';
COMMENT ON COLUMN adm_charge_item_definition.id IS '主键';
COMMENT ON COLUMN adm_charge_item_definition.tenant_id IS '租户标识';
COMMENT ON COLUMN adm_charge_item_definition.created_at IS '创建时间';
COMMENT ON COLUMN adm_charge_item_definition.updated_at IS '最后更新时间';
COMMENT ON COLUMN adm_charge_item_definition.created_by IS '创建人';
COMMENT ON COLUMN adm_charge_item_definition.updated_by IS '最后更新人';
COMMENT ON COLUMN adm_charge_item_definition.is_deleted IS '软删标记';
COMMENT ON COLUMN adm_charge_item_definition.deleted_at IS '删除时间';
COMMENT ON COLUMN adm_charge_item_definition.deleted_by IS '删除人';
COMMENT ON COLUMN adm_charge_item_definition.version IS '乐观锁版本号';
COMMENT ON COLUMN adm_charge_item_definition.instance_table IS '收费项目定义实体：描述某业务实例在特定价类/批次下的可计费项与价格';
COMMENT ON COLUMN adm_charge_item_definition.instance_id IS '业务实例主键';
COMMENT ON COLUMN adm_charge_item_definition.charge_name IS '收费展示名称';
COMMENT ON COLUMN adm_charge_item_definition.status_enum IS '状态枚举编码（PublicationStatus，默认 ACTIVE）';
COMMENT ON COLUMN adm_charge_item_definition.org_id IS '所属机构主键（可空表示全局）';
COMMENT ON COLUMN adm_charge_item_definition.type_code IS '收费类型编码（fin_type_code，必填；号源落诊察费 1012）';
COMMENT ON COLUMN adm_charge_item_definition.yb_type IS '医保类别或对照编码';
COMMENT ON COLUMN adm_charge_item_definition.price_type_code IS '价格类型编码（必填）';
COMMENT ON COLUMN adm_charge_item_definition.lot_no IS '批次或效期批次号';
COMMENT ON COLUMN adm_charge_item_definition.price_value IS '价格数值（元，与 { adm_charge_item} 金额列同口径）';
COMMENT ON COLUMN adm_charge_item_definition.priority IS '同实例多价时的优先级（数值越小越优先，由产品约定）';

CREATE INDEX IF NOT EXISTS idx_cid_instance_price_type
    ON adm_charge_item_definition (instance_table, instance_id, price_type_code)
    WHERE is_deleted = FALSE;
COMMENT ON INDEX idx_cid_instance_price_type IS
    '收费定义：实例表+主键+价类（取 SALE）';

-- seed: adm_charge_item_definition
-- ----- seed sync from DB (adm_charge_item_definition, 23 rows) -----
-- 产品种子：复诊0元号零售价 0 元（fin_type 1012 诊察费；Gate 按 name 识别，见 BR-REG-020）
INSERT INTO adm_charge_item_definition (
    id, tenant_id, created_at, updated_at, created_by, updated_by,
    is_deleted, deleted_at, deleted_by, version,
    instance_table, instance_id, charge_name, status_enum, org_id,
    type_code, yb_type, price_type_code, lot_no, price_value, priority
)
VALUES (
    322957328119180161, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1, 1,
    false, NULL, NULL, 0,
    'adm_healthcare_service', 322957328119180160, '复诊0元号', 'ACTIVE', NULL,
    '1012', NULL, 'SALE', NULL, 0.000000, NULL
)
ON CONFLICT (id) DO NOTHING;

-- from V5__adm_device_definition.sql
-- adm_device_definition（建表 + 种子数据）
-- Flyway V5

CREATE TABLE IF NOT EXISTS "public"."adm_device_definition" (
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
    "bus_no" varchar(255),
    "py_str" varchar(255),
    "wb_str" varchar(255),
    "category_code" varchar(255),
    "type_code" varchar(255),
    "purchase_unit_code" varchar(255),
    "total_volume" varchar(255),
    "item_min_quantity" numeric(18,2),
    "item_max_quantity" numeric(18,2),
    "split_unit_code" varchar(255),
    "merchandise_name" varchar(255),
    "merchandise_py_str" varchar(255),
    "merchandise_wb_str" varchar(255),
    "def_encounter_unit_code" varchar(255),
    "def_inhospital_unit_code" varchar(255),
    "org_id" int8,
    "location_id" int8,
    "model_number" varchar(255),
    "hvcm_flag" bool,
    "chargeable_flag" bool,
    "mr_settlement_type_code" varchar(255),
    "dose_unit_code" varchar(255),
    "package_unit_code" varchar(255),
    "stock_unit_code" varchar(255),
    "approval_number" varchar(255),
    "manufacturer_id" int8,
    "supply_id" int8,
    "description" varchar(255),
    "jurisdiction" varchar(255),
    "device_version" varchar(255),
    "substance_text" varchar(255),
    "allergen_flag" bool,
    "rx_flag" bool,
    "yb_flag" bool,
    "yb_no" varchar(255),
    "yb_match_flag" bool,
    "chrgitm_lv" int4,
    "status_enum" varchar(255) NOT NULL,
    "active_flag" bool NOT NULL,
    "yb_mapping_code" varchar(255),
    "dose_unit_ratio" numeric(18,6),
    "purchase_unit_ratio" numeric(18,6),
    "stock_unit_ratio" numeric(18,6),
    "package_unit_ratio" numeric(18,6),
    "trace_unit_code" varchar(64),
    "trace_unit_ratio" numeric(18,6),
    CONSTRAINT "adm_device_definition_pkey" PRIMARY KEY ("id")
);
COMMENT ON TABLE adm_device_definition IS '管理域 device definition';
COMMENT ON COLUMN adm_device_definition.id IS '主键';
COMMENT ON COLUMN adm_device_definition.tenant_id IS '租户标识';
COMMENT ON COLUMN adm_device_definition.created_at IS '创建时间';
COMMENT ON COLUMN adm_device_definition.updated_at IS '最后更新时间';
COMMENT ON COLUMN adm_device_definition.created_by IS '创建人';
COMMENT ON COLUMN adm_device_definition.updated_by IS '最后更新人';
COMMENT ON COLUMN adm_device_definition.is_deleted IS '软删标记';
COMMENT ON COLUMN adm_device_definition.deleted_at IS '删除时间';
COMMENT ON COLUMN adm_device_definition.deleted_by IS '删除人';
COMMENT ON COLUMN adm_device_definition.version IS '乐观锁版本号';
COMMENT ON COLUMN adm_device_definition.name IS '器材定义聚合根（DM-ADM-008 §3.1，SDD §4.2）';
COMMENT ON COLUMN adm_device_definition.bus_no IS '器材业务编码（院内唯一）';
COMMENT ON COLUMN adm_device_definition.py_str IS '拼音码（用于检索）';
COMMENT ON COLUMN adm_device_definition.wb_str IS '五笔码（用于检索）';
COMMENT ON COLUMN adm_device_definition.category_code IS '器材分类编码';
COMMENT ON COLUMN adm_device_definition.type_code IS '器材种类编码';
COMMENT ON COLUMN adm_device_definition.purchase_unit_code IS '采购单位（5类单位之一）';
COMMENT ON COLUMN adm_device_definition.total_volume IS '包装规格描述（与药品 total_volume 语义对齐）';
COMMENT ON COLUMN adm_device_definition.item_min_quantity IS '最小库存警戒量';
COMMENT ON COLUMN adm_device_definition.item_max_quantity IS '最大库存警戒量';
COMMENT ON COLUMN adm_device_definition.split_unit_code IS '拆零单位（5类单位之一）';
COMMENT ON COLUMN adm_device_definition.merchandise_name IS '商品名称';
COMMENT ON COLUMN adm_device_definition.merchandise_py_str IS '商品名拼音检索码';
COMMENT ON COLUMN adm_device_definition.merchandise_wb_str IS '商品名五笔检索码';
COMMENT ON COLUMN adm_device_definition.def_encounter_unit_code IS '默认门诊/就诊单位编码';
COMMENT ON COLUMN adm_device_definition.def_inhospital_unit_code IS '默认住院单位编码';
COMMENT ON COLUMN adm_device_definition.org_id IS '所属科室标识';
COMMENT ON COLUMN adm_device_definition.location_id IS '所在位置标识';
COMMENT ON COLUMN adm_device_definition.model_number IS '产品型号';
COMMENT ON COLUMN adm_device_definition.hvcm_flag IS '高值器材标志';
COMMENT ON COLUMN adm_device_definition.chargeable_flag IS '是否为收费耗材标记';
COMMENT ON COLUMN adm_device_definition.mr_settlement_type_code IS '病案结算类型（术语 mr_settlement_type_code）';
COMMENT ON COLUMN adm_device_definition.dose_unit_code IS '剂量单位（5类单位之一）';
COMMENT ON COLUMN adm_device_definition.package_unit_code IS '销售单位（5类单位之一）';
COMMENT ON COLUMN adm_device_definition.stock_unit_code IS '库存单位（5类单位之一）';
COMMENT ON COLUMN adm_device_definition.approval_number IS '批准文号';
COMMENT ON COLUMN adm_device_definition.manufacturer_id IS '生产厂家标识（cus_supplier.id，type=生产厂家）';
COMMENT ON COLUMN adm_device_definition.supply_id IS '供应商标识';
COMMENT ON COLUMN adm_device_definition.description IS '器材说明';
COMMENT ON COLUMN adm_device_definition.jurisdiction IS '适用范围描述';
COMMENT ON COLUMN adm_device_definition.device_version IS '器材版本';
COMMENT ON COLUMN adm_device_definition.substance_text IS '主要成分描述';
COMMENT ON COLUMN adm_device_definition.allergen_flag IS '过敏标记';
COMMENT ON COLUMN adm_device_definition.rx_flag IS '处方标志';
COMMENT ON COLUMN adm_device_definition.yb_flag IS '医保标记';
COMMENT ON COLUMN adm_device_definition.yb_no IS '医保编码';
COMMENT ON COLUMN adm_device_definition.yb_match_flag IS '医保对码标记';
COMMENT ON COLUMN adm_device_definition.chrgitm_lv IS '医保等级';
COMMENT ON COLUMN adm_device_definition.status_enum IS '发布状态（存储为整数码）';
COMMENT ON COLUMN adm_device_definition.active_flag IS '是否启用标记';
COMMENT ON COLUMN adm_device_definition.yb_mapping_code IS '医保对码标识';
COMMENT ON COLUMN adm_device_definition.dose_unit_ratio IS '剂量单位→拆零单位换算倍数（拆零单位为1）';
COMMENT ON COLUMN adm_device_definition.purchase_unit_ratio IS '采购单位→拆零单位换算倍数（拆零单位为1）';
COMMENT ON COLUMN adm_device_definition.stock_unit_ratio IS '库存单位→拆零单位换算倍数（拆零单位为1）';
COMMENT ON COLUMN adm_device_definition.package_unit_ratio IS '销售单位→拆零单位换算倍数（拆零单位为1）';
COMMENT ON COLUMN adm_device_definition.trace_unit_code IS '追溯码所贴包装单位（可空；有码时须与 ratio 同时维护）';
COMMENT ON COLUMN adm_device_definition.trace_unit_ratio IS '1 个追溯码单位对应的拆零数量；可空；有码时须 > 0';

-- seed: adm_device_definition
-- ----- seed sync from DB (adm_device_definition, 1 rows) -----

-- from V6__adm_encounter.sql
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
COMMENT ON COLUMN adm_encounter.yb_class_text IS '医保class文本';
COMMENT ON COLUMN adm_encounter.class_json IS 'class JSON 快照';
COMMENT ON COLUMN adm_encounter.priority_enum IS 'priority 枚举值';
COMMENT ON COLUMN adm_encounter.type_enum IS 'type 枚举值';
COMMENT ON COLUMN adm_encounter.service_type_id IS 'serviceType 标识';
COMMENT ON COLUMN adm_encounter.subject_status_enum IS 'subjectStatus 枚举值';
COMMENT ON COLUMN adm_encounter.start_time IS 'start 时间';
COMMENT ON COLUMN adm_encounter.end_time IS 'end 时间';
COMMENT ON COLUMN adm_encounter.organization_id IS 'organization 标识';
COMMENT ON COLUMN adm_encounter.display_order IS '显示顺序';
COMMENT ON COLUMN adm_encounter.first_enum IS 'first 枚举值';
COMMENT ON COLUMN adm_encounter.admit_source_code IS 'admitSource 代码';
COMMENT ON COLUMN adm_encounter.in_way_code IS 'inWay 代码';
COMMENT ON COLUMN adm_encounter.reception_time IS 'reception 时间';
COMMENT ON COLUMN adm_encounter.registrar_id IS 'registrar 标识';
COMMENT ON COLUMN adm_encounter.amb_encounter_id IS 'ambEncounter 标识';
COMMENT ON COLUMN adm_encounter.out_way_code IS 'outWay 代码';

-- from V7__adm_encounter_diagnosis.sql
-- adm_encounter_diagnosis（建表）
-- Flyway V7

CREATE TABLE IF NOT EXISTS "public"."adm_encounter_diagnosis" (
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
    "encounter_id" int8 NOT NULL,
    "condition_definition_id" int8 NOT NULL,
    "primary_flag" bool,
    "rank" int4,
    "description" varchar(255),
    "diag_type_code" varchar(32),
    "diag_code" int4,
    "condition_code" int4,
    "med_type" varchar(255),
    "highest_diag_flag" bool,
    "tcm_syndrome_group" varchar(255),
    "tcm_flag" bool,
    CONSTRAINT "adm_encounter_diagnosis_pkey" PRIMARY KEY ("id")
);
COMMENT ON TABLE adm_encounter_diagnosis IS '管理域 encounter diagnosis';
COMMENT ON COLUMN adm_encounter_diagnosis.id IS '主键';
COMMENT ON COLUMN adm_encounter_diagnosis.tenant_id IS '租户标识';
COMMENT ON COLUMN adm_encounter_diagnosis.created_at IS '创建时间';
COMMENT ON COLUMN adm_encounter_diagnosis.updated_at IS '最后更新时间';
COMMENT ON COLUMN adm_encounter_diagnosis.created_by IS '创建人';
COMMENT ON COLUMN adm_encounter_diagnosis.updated_by IS '最后更新人';
COMMENT ON COLUMN adm_encounter_diagnosis.is_deleted IS '软删标记';
COMMENT ON COLUMN adm_encounter_diagnosis.deleted_at IS '删除时间';
COMMENT ON COLUMN adm_encounter_diagnosis.deleted_by IS '删除人';
COMMENT ON COLUMN adm_encounter_diagnosis.version IS '乐观锁版本号';
COMMENT ON COLUMN adm_encounter_diagnosis.encounter_id IS '就诊主键';
COMMENT ON COLUMN adm_encounter_diagnosis.condition_definition_id IS '诊断目录主键（cln_condition_definition.id；不再经 cln_condition）';
COMMENT ON COLUMN adm_encounter_diagnosis.primary_flag IS '是否主诊断';
COMMENT ON COLUMN adm_encounter_diagnosis.rank IS '排序序号';
COMMENT ON COLUMN adm_encounter_diagnosis.description IS '诊断描述';
COMMENT ON COLUMN adm_encounter_diagnosis.diag_type_code IS '诊断类型码（术语 code 字符串，与 ipt_dise_type_code 对齐，不做整型转换）';
COMMENT ON COLUMN adm_encounter_diagnosis.diag_code IS '诊断编码';
COMMENT ON COLUMN adm_encounter_diagnosis.condition_code IS '病情编码';
COMMENT ON COLUMN adm_encounter_diagnosis.med_type IS '医保或诊疗类别';
COMMENT ON COLUMN adm_encounter_diagnosis.highest_diag_flag IS '是否最高诊断';
COMMENT ON COLUMN adm_encounter_diagnosis.tcm_syndrome_group IS '中医证候组';
COMMENT ON COLUMN adm_encounter_diagnosis.tcm_flag IS '是否中医诊断';

-- from V8__adm_encounter_location.sql
-- adm_encounter_location（建表）
-- Flyway V8

CREATE TABLE IF NOT EXISTS "public"."adm_encounter_location" (
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
    "encounter_id" int8 NOT NULL,
    "location_id" int8 NOT NULL,
    "form_enum" varchar(255),
    "status_enum" varchar(255) NOT NULL,
    "start_time" timestamptz(6),
    "end_time" timestamptz(6),
    CONSTRAINT "adm_encounter_location_pkey" PRIMARY KEY ("id")
);
COMMENT ON TABLE adm_encounter_location IS '管理域 encounter location';
COMMENT ON COLUMN adm_encounter_location.id IS '主键';
COMMENT ON COLUMN adm_encounter_location.tenant_id IS '租户标识';
COMMENT ON COLUMN adm_encounter_location.created_at IS '创建时间';
COMMENT ON COLUMN adm_encounter_location.updated_at IS '最后更新时间';
COMMENT ON COLUMN adm_encounter_location.created_by IS '创建人';
COMMENT ON COLUMN adm_encounter_location.updated_by IS '最后更新人';
COMMENT ON COLUMN adm_encounter_location.is_deleted IS '软删标记';
COMMENT ON COLUMN adm_encounter_location.deleted_at IS '删除时间';
COMMENT ON COLUMN adm_encounter_location.deleted_by IS '删除人';
COMMENT ON COLUMN adm_encounter_location.version IS '乐观锁版本号';
COMMENT ON COLUMN adm_encounter_location.encounter_id IS '就诊标识';
COMMENT ON COLUMN adm_encounter_location.location_id IS '位置/科室标识';
COMMENT ON COLUMN adm_encounter_location.form_enum IS 'form枚举值';
COMMENT ON COLUMN adm_encounter_location.status_enum IS '状态枚举值';
COMMENT ON COLUMN adm_encounter_location.start_time IS '开始时间';
COMMENT ON COLUMN adm_encounter_location.end_time IS '结束时间';

-- from V9__adm_encounter_participant.sql
-- adm_encounter_participant（建表）
-- Flyway V9

CREATE TABLE IF NOT EXISTS "public"."adm_encounter_participant" (
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
    "encounter_id" int8 NOT NULL,
    "role_code" varchar(255) NOT NULL,
    "practitioner_id" int8 NOT NULL,
    "status_enum" varchar(255) NOT NULL,
    "start_time" timestamptz(6),
    "end_time" timestamptz(6),
    CONSTRAINT "adm_encounter_participant_pkey" PRIMARY KEY ("id")
);
COMMENT ON TABLE adm_encounter_participant IS '管理域 encounter participant';
COMMENT ON COLUMN adm_encounter_participant.id IS '主键';
COMMENT ON COLUMN adm_encounter_participant.tenant_id IS '租户标识';
COMMENT ON COLUMN adm_encounter_participant.created_at IS '创建时间';
COMMENT ON COLUMN adm_encounter_participant.updated_at IS '最后更新时间';
COMMENT ON COLUMN adm_encounter_participant.created_by IS '创建人';
COMMENT ON COLUMN adm_encounter_participant.updated_by IS '最后更新人';
COMMENT ON COLUMN adm_encounter_participant.is_deleted IS '软删标记';
COMMENT ON COLUMN adm_encounter_participant.deleted_at IS '删除时间';
COMMENT ON COLUMN adm_encounter_participant.deleted_by IS '删除人';
COMMENT ON COLUMN adm_encounter_participant.version IS '乐观锁版本号';
COMMENT ON COLUMN adm_encounter_participant.encounter_id IS '就诊标识';
COMMENT ON COLUMN adm_encounter_participant.role_code IS '角色编码';
COMMENT ON COLUMN adm_encounter_participant.practitioner_id IS '医护人员标识';
COMMENT ON COLUMN adm_encounter_participant.status_enum IS '状态枚举值';
COMMENT ON COLUMN adm_encounter_participant.start_time IS '开始时间';
COMMENT ON COLUMN adm_encounter_participant.end_time IS '结束时间';

-- from V10__adm_encounter_reason.sql
-- adm_encounter_reason（建表）
-- Flyway V10

CREATE TABLE IF NOT EXISTS "public"."adm_encounter_reason" (
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
    "encounter_id" int8 NOT NULL,
    "use_code" varchar(255) NOT NULL,
    "reason_table" varchar(255),
    "reason_id" int8,
    "description" varchar(255),
    CONSTRAINT "adm_encounter_reason_pkey" PRIMARY KEY ("id")
);
COMMENT ON TABLE adm_encounter_reason IS '管理域 encounter reason';
COMMENT ON COLUMN adm_encounter_reason.id IS '主键';
COMMENT ON COLUMN adm_encounter_reason.tenant_id IS '租户标识';
COMMENT ON COLUMN adm_encounter_reason.created_at IS '创建时间';
COMMENT ON COLUMN adm_encounter_reason.updated_at IS '最后更新时间';
COMMENT ON COLUMN adm_encounter_reason.created_by IS '创建人';
COMMENT ON COLUMN adm_encounter_reason.updated_by IS '最后更新人';
COMMENT ON COLUMN adm_encounter_reason.is_deleted IS '软删标记';
COMMENT ON COLUMN adm_encounter_reason.deleted_at IS '删除时间';
COMMENT ON COLUMN adm_encounter_reason.deleted_by IS '删除人';
COMMENT ON COLUMN adm_encounter_reason.version IS '乐观锁版本号';
COMMENT ON COLUMN adm_encounter_reason.encounter_id IS '就诊原因子实体：记录就诊事由的来源表与业务主键及用途编码';
COMMENT ON COLUMN adm_encounter_reason.use_code IS '用途编码（如挂号原因类型）';
COMMENT ON COLUMN adm_encounter_reason.reason_table IS '原因实体所在逻辑表名';
COMMENT ON COLUMN adm_encounter_reason.reason_id IS '原因业务主键';
COMMENT ON COLUMN adm_encounter_reason.description IS '原因描述';

-- from V11__adm_episode_of_care.sql
-- adm_episode_of_care（建表）
-- Flyway V11

CREATE TABLE IF NOT EXISTS "public"."adm_episode_of_care" (
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
    "organization_id" int8 NOT NULL,
    "bus_no" varchar(255) NOT NULL,
    "care_team_id" int8,
    "care_type" varchar(255),
    "care_goal" varchar(255),
    "status_enum" varchar(255) NOT NULL,
    "active_flag" bool NOT NULL,
    "start_time" timestamptz(6),
    "end_time" timestamptz(6),
    CONSTRAINT "adm_episode_of_care_pkey" PRIMARY KEY ("id")
);
COMMENT ON TABLE adm_episode_of_care IS '管理域 episode of care';
COMMENT ON COLUMN adm_episode_of_care.id IS '主键';
COMMENT ON COLUMN adm_episode_of_care.tenant_id IS '租户标识';
COMMENT ON COLUMN adm_episode_of_care.created_at IS '创建时间';
COMMENT ON COLUMN adm_episode_of_care.updated_at IS '最后更新时间';
COMMENT ON COLUMN adm_episode_of_care.created_by IS '创建人';
COMMENT ON COLUMN adm_episode_of_care.updated_by IS '最后更新人';
COMMENT ON COLUMN adm_episode_of_care.is_deleted IS '软删标记';
COMMENT ON COLUMN adm_episode_of_care.deleted_at IS '删除时间';
COMMENT ON COLUMN adm_episode_of_care.deleted_by IS '删除人';
COMMENT ON COLUMN adm_episode_of_care.version IS '乐观锁版本号';
COMMENT ON COLUMN adm_episode_of_care.patient_id IS 'patient 标识';
COMMENT ON COLUMN adm_episode_of_care.organization_id IS 'organization 标识';
COMMENT ON COLUMN adm_episode_of_care.bus_no IS 'bus 编号';
COMMENT ON COLUMN adm_episode_of_care.care_team_id IS 'careTeam 标识';
COMMENT ON COLUMN adm_episode_of_care.care_type IS 'care类型';
COMMENT ON COLUMN adm_episode_of_care.care_goal IS '照护目标';
COMMENT ON COLUMN adm_episode_of_care.status_enum IS 'status 枚举值';
COMMENT ON COLUMN adm_episode_of_care.active_flag IS 'active 标志位';
COMMENT ON COLUMN adm_episode_of_care.start_time IS 'start 时间';
COMMENT ON COLUMN adm_episode_of_care.end_time IS 'end 时间';

-- from V12__adm_episode_of_care_encounter.sql
-- adm_episode_of_care_encounter（建表）
-- Flyway V12

CREATE TABLE IF NOT EXISTS "public"."adm_episode_of_care_encounter" (
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
    "episode_of_care_id" int8 NOT NULL,
    "encounter_id" int8 NOT NULL,
    "join_time" timestamptz(6) NOT NULL,
    "leave_time" timestamptz(6),
    CONSTRAINT "adm_episode_of_care_encounter_pkey" PRIMARY KEY ("id")
);
COMMENT ON TABLE adm_episode_of_care_encounter IS '管理域 episode of care encounter';
COMMENT ON COLUMN adm_episode_of_care_encounter.id IS '主键';
COMMENT ON COLUMN adm_episode_of_care_encounter.tenant_id IS '租户标识';
COMMENT ON COLUMN adm_episode_of_care_encounter.created_at IS '创建时间';
COMMENT ON COLUMN adm_episode_of_care_encounter.updated_at IS '最后更新时间';
COMMENT ON COLUMN adm_episode_of_care_encounter.created_by IS '创建人';
COMMENT ON COLUMN adm_episode_of_care_encounter.updated_by IS '最后更新人';
COMMENT ON COLUMN adm_episode_of_care_encounter.is_deleted IS '软删标记';
COMMENT ON COLUMN adm_episode_of_care_encounter.deleted_at IS '删除时间';
COMMENT ON COLUMN adm_episode_of_care_encounter.deleted_by IS '删除人';
COMMENT ON COLUMN adm_episode_of_care_encounter.version IS '乐观锁版本号';
COMMENT ON COLUMN adm_episode_of_care_encounter.episode_of_care_id IS '疗程-就诊关联实体：记录某次就诊加入/离开疗程的时间';
COMMENT ON COLUMN adm_episode_of_care_encounter.encounter_id IS '就诊主键';
COMMENT ON COLUMN adm_episode_of_care_encounter.join_time IS '纳入疗程时间';
COMMENT ON COLUMN adm_episode_of_care_encounter.leave_time IS '离开疗程时间（未离开时为空）';

-- from V13__adm_healthcare_service.sql
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
    "activity_ids" varchar(512),
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
COMMENT ON COLUMN adm_healthcare_service.yb_no IS '医保编号（yb_no）已废弃业务读写；存量列可空，新业务取绑定诊查费 AD';
COMMENT ON COLUMN adm_healthcare_service.activity_ids IS '诊查费 ActivityDefinition 主键 CSV（逗号分隔）；零元号可空；见 DM-ADM-007 BR-HCS-010～012';
COMMENT ON COLUMN adm_healthcare_service.bus_no IS '业务编号（bus_no），系统内唯一标识医疗服务的人读编码，可为空';
COMMENT ON COLUMN adm_healthcare_service.comment IS '备注说明，对外展示的补充描述，可为空';
COMMENT ON COLUMN adm_healthcare_service.extra_details IS '扩展详情（JSON 字符串），存储结构化附加信息，可为空';
COMMENT ON COLUMN adm_healthcare_service.contact IS '联系方式（JSON 字符串），医疗服务对外联系信息，可为空';

-- seed: adm_healthcare_service
-- 产品种子：复诊0元号（BR-REG-020；写侧按 name=复诊0元号 识别；bus_no 种子默认 HS00000004；零元可不绑 activity_ids）
INSERT INTO adm_healthcare_service (
    id, tenant_id, created_at, updated_at, created_by, updated_by,
    is_deleted, deleted_at, deleted_by, version,
    name, type_code, category_code, offered_org_id, specialty_code, location_id,
    status_enum, active_flag, appointment_required_flag, yb_no, activity_ids, bus_no, comment, extra_details, contact
)
VALUES (
    322957328119180160, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1, 1,
    false, NULL, NULL, 0,
    '复诊0元号', '4', NULL, NULL, NULL, NULL,
    'ACTIVE', true, false, NULL, NULL, 'HS00000004', NULL, NULL, NULL
)
ON CONFLICT (id) DO NOTHING;

-- from V14__adm_invoice.sql
-- adm_invoice（建表）
-- Flyway V14

CREATE TABLE IF NOT EXISTS "public"."adm_invoice" (
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
    "patient_id" int8,
    "reconciliation_id" int8,
    "payment_id" int8,
    "bus_no" varchar(255),
    "bill_no" varchar(255),
    "type_code" varchar(255),
    "cancelled_reason" varchar(255),
    "charge_item_ids" text,
    "payment_terms" varchar(255),
    "amount" numeric(19,6),
    "status_enum" varchar(255),
    "issued_at" timestamptz(6),
    CONSTRAINT "adm_invoice_pkey" PRIMARY KEY ("id")
);
COMMENT ON TABLE adm_invoice IS '管理域 invoice';
COMMENT ON COLUMN adm_invoice.id IS '主键';
COMMENT ON COLUMN adm_invoice.tenant_id IS '租户标识';
COMMENT ON COLUMN adm_invoice.created_at IS '创建时间';
COMMENT ON COLUMN adm_invoice.updated_at IS '最后更新时间';
COMMENT ON COLUMN adm_invoice.created_by IS '创建人';
COMMENT ON COLUMN adm_invoice.updated_by IS '最后更新人';
COMMENT ON COLUMN adm_invoice.is_deleted IS '软删标记';
COMMENT ON COLUMN adm_invoice.deleted_at IS '删除时间';
COMMENT ON COLUMN adm_invoice.deleted_by IS '删除人';
COMMENT ON COLUMN adm_invoice.version IS '乐观锁版本号';
COMMENT ON COLUMN adm_invoice.patient_id IS '票据聚合根：草稿开具等写侧由 { com.openhis.health.domain.administration.invoice.service.InvoiceDomainService} 编排';
COMMENT ON COLUMN adm_invoice.reconciliation_id IS 'reconciliation 标识';
COMMENT ON COLUMN adm_invoice.payment_id IS 'payment 标识';
COMMENT ON COLUMN adm_invoice.bus_no IS '业务流水号（新建必填）';
COMMENT ON COLUMN adm_invoice.bill_no IS 'bill 编号';
COMMENT ON COLUMN adm_invoice.type_code IS 'type 代码';
COMMENT ON COLUMN adm_invoice.cancelled_reason IS 'cancelled原因';
COMMENT ON COLUMN adm_invoice.charge_item_ids IS '收费项ids';
COMMENT ON COLUMN adm_invoice.payment_terms IS '支付terms';
COMMENT ON COLUMN adm_invoice.amount IS '金额';
COMMENT ON COLUMN adm_invoice.status_enum IS 'status 枚举值';
COMMENT ON COLUMN adm_invoice.issued_at IS 'issued 时间';

-- from V15__adm_location.sql
-- adm_location（建表）
-- Flyway V15

CREATE TABLE IF NOT EXISTS "public"."adm_location" (
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
    "status_enum" varchar(255) NOT NULL,
    "operational_enum" varchar(255),
    "form_enum" varchar(255) NOT NULL,
    "mode_enum" varchar(255) NOT NULL,
    "type_json" varchar(255),
    "organization_id" int8 NOT NULL,
    "display_order" int4,
    "py_str" varchar(255),
    "wb_str" varchar(255),
    "parent_id" int8,
    CONSTRAINT "adm_location_pkey" PRIMARY KEY ("id")
);
COMMENT ON TABLE adm_location IS '管理域 location';
COMMENT ON COLUMN adm_location.id IS '主键';
COMMENT ON COLUMN adm_location.tenant_id IS '租户标识';
COMMENT ON COLUMN adm_location.created_at IS '创建时间';
COMMENT ON COLUMN adm_location.updated_at IS '最后更新时间';
COMMENT ON COLUMN adm_location.created_by IS '创建人';
COMMENT ON COLUMN adm_location.updated_by IS '最后更新人';
COMMENT ON COLUMN adm_location.is_deleted IS '软删标记';
COMMENT ON COLUMN adm_location.deleted_at IS '删除时间';
COMMENT ON COLUMN adm_location.deleted_by IS '删除人';
COMMENT ON COLUMN adm_location.version IS '乐观锁版本号';
COMMENT ON COLUMN adm_location.name IS '位置聚合根实体';
COMMENT ON COLUMN adm_location.bus_no IS 'bus 编号';
COMMENT ON COLUMN adm_location.status_enum IS 'status 枚举值';
COMMENT ON COLUMN adm_location.operational_enum IS 'operational 枚举值';
COMMENT ON COLUMN adm_location.form_enum IS 'form 枚举值';
COMMENT ON COLUMN adm_location.mode_enum IS 'mode 枚举值';
COMMENT ON COLUMN adm_location.type_json IS 'type JSON 快照';
COMMENT ON COLUMN adm_location.organization_id IS 'organization 标识';
COMMENT ON COLUMN adm_location.display_order IS '显示顺序';
COMMENT ON COLUMN adm_location.py_str IS '拼音码';
COMMENT ON COLUMN adm_location.wb_str IS '五笔码';
COMMENT ON COLUMN adm_location.parent_id IS 'parent 标识';

-- seed: adm_location

-- from V16__adm_organization.sql
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

-- from V17__adm_patient.sql
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
    "education_level_code" varchar(32),
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
COMMENT ON COLUMN adm_patient.address IS '地址';
COMMENT ON COLUMN adm_patient.address_province IS '地址province';
COMMENT ON COLUMN adm_patient.address_city IS '地址city';
COMMENT ON COLUMN adm_patient.address_district IS '地址district';
COMMENT ON COLUMN adm_patient.address_street IS '地址street';
COMMENT ON COLUMN adm_patient.address_extend_json IS '主要地址';
COMMENT ON COLUMN adm_patient.nationality_code IS '民族编码';
COMMENT ON COLUMN adm_patient.py_str IS '拼音码';
COMMENT ON COLUMN adm_patient.wb_str IS '五笔码';
COMMENT ON COLUMN adm_patient.blood_type_abo IS 'blood类型abo';
COMMENT ON COLUMN adm_patient.blood_type_rh IS 'blood类型rh';
COMMENT ON COLUMN adm_patient.work_company IS '血型';
COMMENT ON COLUMN adm_patient.native_place IS '籍贯';
COMMENT ON COLUMN adm_patient.country_code IS '国家地区编码';
COMMENT ON COLUMN adm_patient.link_name IS 'link名称';
COMMENT ON COLUMN adm_patient.link_relation_code IS 'linkrelation编码';
COMMENT ON COLUMN adm_patient.link_telcom IS '联系电话';
COMMENT ON COLUMN adm_patient.link_jsons IS '主要联系人';
COMMENT ON COLUMN adm_patient.deceased_date IS '死亡时间';
COMMENT ON COLUMN adm_patient.deceased_reason IS '死亡原因';
COMMENT ON COLUMN adm_patient.organization_id IS '建档机构标识';
COMMENT ON COLUMN adm_patient.merged_target_patient_id IS '合并目标患者标识';
COMMENT ON COLUMN adm_patient.merged_at IS '合并时间';
COMMENT ON COLUMN adm_patient.education_level_code IS
    '学历层次字典编码（EducationLevel，如 PREPARATORY/UNDERGRADUATE/POSTGRADUATE/DOCTORAL）';

-- from V18__adm_patient_identifier.sql
-- adm_patient_identifier（建表）
-- Flyway V18

CREATE TABLE IF NOT EXISTS "public"."adm_patient_identifier" (
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
    "type_code" varchar(255) NOT NULL,
    "identifier_no" varchar(255) NOT NULL,
    "state_enum" varchar(255),
    "start_time" timestamptz(6),
    "end_time" timestamptz(6),
    "patient_id" int8 NOT NULL,
    CONSTRAINT "adm_patient_identifier_pkey" PRIMARY KEY ("id")
);
COMMENT ON TABLE adm_patient_identifier IS '管理域 patient identifier';
COMMENT ON COLUMN adm_patient_identifier.id IS '主键';
COMMENT ON COLUMN adm_patient_identifier.tenant_id IS '租户标识';
COMMENT ON COLUMN adm_patient_identifier.created_at IS '创建时间';
COMMENT ON COLUMN adm_patient_identifier.updated_at IS '最后更新时间';
COMMENT ON COLUMN adm_patient_identifier.created_by IS '创建人';
COMMENT ON COLUMN adm_patient_identifier.updated_by IS '最后更新人';
COMMENT ON COLUMN adm_patient_identifier.is_deleted IS '软删标记';
COMMENT ON COLUMN adm_patient_identifier.deleted_at IS '删除时间';
COMMENT ON COLUMN adm_patient_identifier.deleted_by IS '删除人';
COMMENT ON COLUMN adm_patient_identifier.version IS '乐观锁版本号';
COMMENT ON COLUMN adm_patient_identifier.type_code IS '患者非身份证标识子实体';
COMMENT ON COLUMN adm_patient_identifier.identifier_no IS '标识号码';
COMMENT ON COLUMN adm_patient_identifier.state_enum IS '标识状态编码';
COMMENT ON COLUMN adm_patient_identifier.start_time IS '有效开始时间';
COMMENT ON COLUMN adm_patient_identifier.end_time IS '有效结束时间';
COMMENT ON COLUMN adm_patient_identifier.patient_id IS '所属患者标识';

-- adm_patient_student 已迁出 whale-module-campus（cmp_patient_student）；基线不再建表

-- GAP-OPN-002：库房期初关闭态独立表（仅三房 Location 可写行）；AS-BAS-INV-001
CREATE TABLE IF NOT EXISTS "public"."adm_location_inventory_opening" (
    "id" int8 NOT NULL,
    "location_id" int8 NOT NULL,
    "opening_closed" bool NOT NULL DEFAULT false,
    "closed_time" timestamptz(6),
    "closed_by" int8,
    "tenant_id" int8,
    "created_at" timestamptz(6) NOT NULL,
    "updated_at" timestamptz(6),
    "created_by" int8,
    "updated_by" int8,
    "is_deleted" bool NOT NULL DEFAULT false,
    "deleted_at" timestamptz(6),
    "deleted_by" int8,
    "version" int4 NOT NULL DEFAULT 0,
    CONSTRAINT "adm_location_inventory_opening_pkey" PRIMARY KEY ("id"),
    CONSTRAINT "uk_adm_loc_inv_opening_location" UNIQUE ("location_id")
);

COMMENT ON TABLE adm_location_inventory_opening IS '库房库存期初关闭态（独立表；仅药库/药房/科室药房）';
COMMENT ON COLUMN adm_location_inventory_opening.location_id IS '位置主键（=adm_location.id，业务唯一）';
COMMENT ON COLUMN adm_location_inventory_opening.opening_closed IS '是否已关闭期初';
COMMENT ON COLUMN adm_location_inventory_opening.closed_time IS '关闭时间';
COMMENT ON COLUMN adm_location_inventory_opening.closed_by IS '关闭操作人';


-- from V20__adm_pr_assign.sql
-- adm_pr_assign（建表 + 种子数据）
-- Flyway V20

CREATE TABLE IF NOT EXISTS "public"."adm_pr_assign" (
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
    "name" varchar(255),
    "active_flag" bool NOT NULL DEFAULT true,
    "practitioner_id" int8 NOT NULL,
    "org_id" int8,
    "location_id" int8,
    "specialty_code" varchar(64),
    "type_code" int4,
    "role_id" int8 NOT NULL,
    CONSTRAINT "adm_pr_assign_pkey" PRIMARY KEY ("id"),
    CONSTRAINT "fk_adm_pr_assign_whale_role" FOREIGN KEY ("role_id") REFERENCES whale_roles (id)
);

CREATE INDEX IF NOT EXISTS idx_adm_pr_assign_role_id ON adm_pr_assign (role_id);
COMMENT ON TABLE adm_pr_assign IS '管理域 pr assign';
COMMENT ON COLUMN adm_pr_assign.id IS '主键';
COMMENT ON COLUMN adm_pr_assign.tenant_id IS '租户标识';
COMMENT ON COLUMN adm_pr_assign.created_at IS '创建时间';
COMMENT ON COLUMN adm_pr_assign.updated_at IS '最后更新时间';
COMMENT ON COLUMN adm_pr_assign.created_by IS '创建人';
COMMENT ON COLUMN adm_pr_assign.updated_by IS '最后更新人';
COMMENT ON COLUMN adm_pr_assign.is_deleted IS '软删标记';
COMMENT ON COLUMN adm_pr_assign.deleted_at IS '删除时间';
COMMENT ON COLUMN adm_pr_assign.deleted_by IS '删除人';
COMMENT ON COLUMN adm_pr_assign.version IS '乐观锁版本号';
COMMENT ON COLUMN adm_pr_assign.name IS '名称';
COMMENT ON COLUMN adm_pr_assign.active_flag IS '是否启用';
COMMENT ON COLUMN adm_pr_assign.practitioner_id IS '医护人员标识';
COMMENT ON COLUMN adm_pr_assign.org_id IS '机构/科室标识；可空（仅挂角色未配管辖科室时）';
COMMENT ON COLUMN adm_pr_assign.location_id IS '位置标识；可空（仅挂角色未配库房/药房/病区时）';
COMMENT ON COLUMN adm_pr_assign.specialty_code IS 'specialty编码';
COMMENT ON COLUMN adm_pr_assign.type_code IS '类型编码';
COMMENT ON COLUMN adm_pr_assign.role_id IS '岗位分配聚合根（{ adm_pr_assign}）';

-- seed: adm_pr_assign

-- from V21__adm_pr_assign_schedule.sql
-- adm_pr_assign_schedule（建表 + 种子数据）
-- Flyway V21

CREATE TABLE IF NOT EXISTS "public"."adm_pr_assign_schedule" (
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

    "assignment_id" int8 NOT NULL,
    "availability_json" varchar(20000) NOT NULL DEFAULT '{}',

    CONSTRAINT "adm_pr_assign_schedule_pkey" PRIMARY KEY ("id")
);
COMMENT ON TABLE adm_pr_assign_schedule IS '管理域 pr assign schedule';
COMMENT ON COLUMN adm_pr_assign_schedule.id IS '主键';
COMMENT ON COLUMN adm_pr_assign_schedule.tenant_id IS '租户标识';
COMMENT ON COLUMN adm_pr_assign_schedule.created_at IS '创建时间';
COMMENT ON COLUMN adm_pr_assign_schedule.updated_at IS '最后更新时间';
COMMENT ON COLUMN adm_pr_assign_schedule.created_by IS '创建人';
COMMENT ON COLUMN adm_pr_assign_schedule.updated_by IS '最后更新人';
COMMENT ON COLUMN adm_pr_assign_schedule.is_deleted IS '软删标记';
COMMENT ON COLUMN adm_pr_assign_schedule.deleted_at IS '删除时间';
COMMENT ON COLUMN adm_pr_assign_schedule.deleted_by IS '删除人';
COMMENT ON COLUMN adm_pr_assign_schedule.version IS '乐观锁版本号';
COMMENT ON COLUMN adm_pr_assign_schedule.assignment_id IS '岗位排班项子实体，对应表 { adm_pr_assign_schedule}';
COMMENT ON COLUMN adm_pr_assign_schedule.availability_json IS '可用时间 JSON';

-- seed: adm_pr_assign_schedule
-- (empty) no rows in adm_pr_assign_schedule

-- from V22__adm_pr_assign_service.sql
-- adm_pr_assign_service（建表 + 种子数据）
-- Flyway V22

CREATE TABLE IF NOT EXISTS "public"."adm_pr_assign_service" (
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

    "assignment_id" int8 NOT NULL,
    "healthcare_service_id" int8 NOT NULL,

    CONSTRAINT "adm_pr_assign_service_pkey" PRIMARY KEY ("id")
);
COMMENT ON TABLE adm_pr_assign_service IS '管理域 pr assign service';
COMMENT ON COLUMN adm_pr_assign_service.id IS '主键';
COMMENT ON COLUMN adm_pr_assign_service.tenant_id IS '租户标识';
COMMENT ON COLUMN adm_pr_assign_service.created_at IS '创建时间';
COMMENT ON COLUMN adm_pr_assign_service.updated_at IS '最后更新时间';
COMMENT ON COLUMN adm_pr_assign_service.created_by IS '创建人';
COMMENT ON COLUMN adm_pr_assign_service.updated_by IS '最后更新人';
COMMENT ON COLUMN adm_pr_assign_service.is_deleted IS '软删标记';
COMMENT ON COLUMN adm_pr_assign_service.deleted_at IS '删除时间';
COMMENT ON COLUMN adm_pr_assign_service.deleted_by IS '删除人';
COMMENT ON COLUMN adm_pr_assign_service.version IS '乐观锁版本号';
COMMENT ON COLUMN adm_pr_assign_service.assignment_id IS '岗位服务项子实体，对应表 { adm_pr_assign_service}';
COMMENT ON COLUMN adm_pr_assign_service.healthcare_service_id IS '医疗服务标识，关联 { adm_healthcare_service.id}';

-- seed: adm_pr_assign_service
-- 产品种子：全部未删岗位挂上复诊0元号（BR-REG-014；新库若尚无岗位则为 0 行）
INSERT INTO adm_pr_assign_service (
    id, tenant_id, created_at, updated_at, created_by, updated_by,
    is_deleted, deleted_at, deleted_by, version,
    assignment_id, healthcare_service_id
)
SELECT
    a.id + 500000000000000000,
    COALESCE(a.tenant_id, 1),
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    COALESCE(a.created_by, 1),
    COALESCE(a.updated_by, 1),
    false,
    NULL,
    NULL,
    0,
    a.id,
    322957328119180160
FROM adm_pr_assign a
WHERE a.is_deleted = false
  AND NOT EXISTS (
    SELECT 1
    FROM adm_pr_assign_service s
    WHERE s.assignment_id = a.id
      AND s.healthcare_service_id = 322957328119180160
      AND s.is_deleted = false
  );

-- from V23__adm_practitioner.sql
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
COMMENT ON TABLE adm_practitioner IS '医务人员主数据';
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
COMMENT ON COLUMN adm_practitioner.name IS '人员姓名';
COMMENT ON COLUMN adm_practitioner.name_json IS '其他名称 JSON（曾用名、英文名等）';
COMMENT ON COLUMN adm_practitioner.py_str IS '姓名拼音检索码';
COMMENT ON COLUMN adm_practitioner.wb_str IS '姓名五笔检索码';
COMMENT ON COLUMN adm_practitioner.gender_enum IS '行政性别编码';
COMMENT ON COLUMN adm_practitioner.birth_date IS '出生日期';
COMMENT ON COLUMN adm_practitioner.deceased_date IS '死亡时间';
COMMENT ON COLUMN adm_practitioner.phone IS '联系电话';
COMMENT ON COLUMN adm_practitioner.address_province IS '地址-省';
COMMENT ON COLUMN adm_practitioner.address_city IS '地址-市';
COMMENT ON COLUMN adm_practitioner.address_district IS '地址-区/县';
COMMENT ON COLUMN adm_practitioner.address_street IS '地址-街道/详细地址';
COMMENT ON COLUMN adm_practitioner.address_json IS '地址扩展 JSON';
COMMENT ON COLUMN adm_practitioner.status_enum IS '人员状态（ACTIVE / INACTIVE / DISCARDED）';
COMMENT ON COLUMN adm_practitioner.active_flag IS '是否启用（status_enum == ACTIVE 时为 true）';
COMMENT ON COLUMN adm_practitioner.bus_no IS '院内工号/业务编号';
COMMENT ON COLUMN adm_practitioner.yb_no IS '医保人员编码';
COMMENT ON COLUMN adm_practitioner.user_id IS '关联平台用户主键（与 id 独立，无库表外键）';
COMMENT ON COLUMN adm_practitioner.org_id IS '默认登录科室/当前工作科室（Organization 主键）';
COMMENT ON COLUMN adm_practitioner.dr_profttl_code IS '职称编码';
COMMENT ON COLUMN adm_practitioner.phar_prac_cert_no IS '医师/药师执业资格证编号';
COMMENT ON COLUMN adm_practitioner.prsc_dr_cert_code IS '执业证件类型编码';
COMMENT ON COLUMN adm_practitioner.signature IS '医生签名图片 Base64（可含 data:image/*;base64, 前缀）';
COMMENT ON COLUMN adm_practitioner.kpd_code IS '开票点编码';
COMMENT ON COLUMN adm_practitioner.pos_no IS 'POS 机终端编号';

-- seed: adm_practitioner

-- from V67__adm_specimen_definition.sql
-- adm_specimen_definition（建表）
-- Flyway V74

CREATE TABLE IF NOT EXISTS "public"."adm_specimen_definition" (
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
    "py_str" varchar(255),
    "wb_str" varchar(255),
    "type_enum" varchar(255) NOT NULL,
    "status_enum" varchar(255) NOT NULL,
    CONSTRAINT "adm_specimen_definition_pkey" PRIMARY KEY ("id")
);

CREATE UNIQUE INDEX IF NOT EXISTS "uk_adm_specimen_definition_bus_no"
    ON "public"."adm_specimen_definition" ("bus_no")
    WHERE "is_deleted" = false;

CREATE INDEX IF NOT EXISTS "idx_adm_specimen_definition_type_enum"
    ON "public"."adm_specimen_definition" ("type_enum");

CREATE INDEX IF NOT EXISTS "idx_adm_specimen_definition_status_enum"
    ON "public"."adm_specimen_definition" ("status_enum");

-- ----- seed sync from DB (adm_specimen_definition, 1 rows) -----
COMMENT ON TABLE adm_specimen_definition IS '管理域 specimen definition';
COMMENT ON COLUMN adm_specimen_definition.id IS '主键';
COMMENT ON COLUMN adm_specimen_definition.tenant_id IS '租户标识';
COMMENT ON COLUMN adm_specimen_definition.created_at IS '创建时间';
COMMENT ON COLUMN adm_specimen_definition.updated_at IS '最后更新时间';
COMMENT ON COLUMN adm_specimen_definition.created_by IS '创建人';
COMMENT ON COLUMN adm_specimen_definition.updated_by IS '最后更新人';
COMMENT ON COLUMN adm_specimen_definition.is_deleted IS '软删标记';
COMMENT ON COLUMN adm_specimen_definition.deleted_at IS '删除时间';
COMMENT ON COLUMN adm_specimen_definition.deleted_by IS '删除人';
COMMENT ON COLUMN adm_specimen_definition.version IS '乐观锁版本号';
COMMENT ON COLUMN adm_specimen_definition.bus_no IS '样本定义聚合根（{ adm_specimen_definition}，DM-ADM-014）';
COMMENT ON COLUMN adm_specimen_definition.name IS '面向临床与维护页的显示名称';
COMMENT ON COLUMN adm_specimen_definition.py_str IS '拼音检索码';
COMMENT ON COLUMN adm_specimen_definition.wb_str IS '五笔检索码';
COMMENT ON COLUMN adm_specimen_definition.type_enum IS '标本种类（存 { SpecimenType#getCode()}）';
COMMENT ON COLUMN adm_specimen_definition.status_enum IS '发布状态（存 { PublicationStatus#getCode()}）';

-- =============================================================================
-- V11 squash: adm_device
-- =============================================================================
CREATE TABLE IF NOT EXISTS "public"."adm_device" (
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
    "py_str" varchar(255),
    "wb_str" varchar(255),
    "org_id" int8,
    "location_id" int8,
    "status_enum" varchar(255) NOT NULL DEFAULT 'ACTIVE',
    "category_code" varchar(255),
    "model_number" varchar(255),
    "serial_number" varchar(255),
    "manufacturer_id" int8,
    "manufacture_date" date,
    "expiration_date" date,
    "description" varchar(500),
    CONSTRAINT "adm_device_pkey" PRIMARY KEY ("id")
);

CREATE UNIQUE INDEX IF NOT EXISTS "uk_adm_device_bus_no"
    ON "public"."adm_device" ("tenant_id", "bus_no")
    WHERE "is_deleted" = false;

CREATE INDEX IF NOT EXISTS "idx_adm_device_org_id"
    ON "public"."adm_device" ("org_id");

CREATE INDEX IF NOT EXISTS "idx_adm_device_status_enum"
    ON "public"."adm_device" ("status_enum");

COMMENT ON TABLE adm_device IS '管理域检验/科室设备台账（合管设备）';
COMMENT ON COLUMN adm_device.bus_no IS '设备业务编码（BusinessSequence 生成）';
COMMENT ON COLUMN adm_device.org_id IS '归属科室（adm_organization.id，可空）';
COMMENT ON COLUMN adm_device.status_enum IS 'PublicationStatus.code';
COMMENT ON COLUMN adm_device.category_code IS '设备分类（术语 adm_device_category_code）';
COMMENT ON COLUMN adm_device.manufacturer_id IS '生产厂家（cus_supplier.id）';

-- =============================================================================
-- V13 squash: adm report indexes
-- =============================================================================
CREATE INDEX IF NOT EXISTS idx_rpt_adm_ci_dispense
    ON adm_charge_item (dispense_table, dispense_id)
    WHERE is_deleted = FALSE AND dispense_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_rpt_adm_ci_service
    ON adm_charge_item (service_table, service_id)
    WHERE is_deleted = FALSE AND service_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_rpt_adm_ci_entered
    ON adm_charge_item (entered_date, status_enum)
    WHERE is_deleted = FALSE AND entered_date IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_rpt_adm_ci_occurrence
    ON adm_charge_item (occurrence_time, status_enum)
    WHERE is_deleted = FALSE AND occurrence_time IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_rpt_adm_ci_encounter
    ON adm_charge_item (encounter_id, status_enum)
    WHERE is_deleted = FALSE AND encounter_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_rpt_adm_ci_product
    ON adm_charge_item (product_table, product_id)
    WHERE is_deleted = FALSE AND product_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_rpt_adm_ci_enterer
    ON adm_charge_item (enterer_id, entered_date)
    WHERE is_deleted = FALSE AND enterer_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_adm_charge_item_fee_confirmer
    ON adm_charge_item (encounter_id, fee_confirmer_id)
    WHERE is_deleted = false AND fee_confirmer_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_rpt_adm_ci_req_org
    ON adm_charge_item (requesting_org_id, entered_date)
    WHERE is_deleted = FALSE AND requesting_org_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_rpt_adm_enc_class_start
    ON adm_encounter (class_enum, start_time, organization_id)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_rpt_adm_enc_class_end
    ON adm_encounter (class_enum, end_time, organization_id)
    WHERE is_deleted = FALSE AND end_time IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_rpt_adm_enc_org_created
    ON adm_encounter (organization_id, created_at)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_rpt_adm_enc_patient
    ON adm_encounter (patient_id)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_rpt_adm_enc_loc_ward
    ON adm_encounter_location (location_id, form_enum, encounter_id)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_rpt_adm_enc_loc_encounter
    ON adm_encounter_location (encounter_id)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_rpt_adm_enc_part_practitioner
    ON adm_encounter_participant (practitioner_id, encounter_id)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_rpt_adm_enc_part_encounter
    ON adm_encounter_participant (encounter_id)
    WHERE is_deleted = FALSE;
