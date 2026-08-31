-- Squashed V7 ter_cus_ddl

-- from V29__cus_activity_execution_location.sql
-- cus_activity_execution_location（建表）
-- Flyway V29

CREATE TABLE IF NOT EXISTS "public"."cus_activity_execution_location" (
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
    "organization_id" int8 NOT NULL,
    "activity_definition_id" int8,
    "activity_category_code" varchar(255) NOT NULL,
    "execution_org_id" int8 NOT NULL,
    "execution_location_id" int8,
    "start_time" time(6),
    "end_time" time(6),
    "priority" int4 NOT NULL,
    "display_order" int4,
    "remark" varchar(255),
    CONSTRAINT "cus_activity_execution_location_pkey" PRIMARY KEY ("id")
);
COMMENT ON TABLE cus_activity_execution_location IS '自定义 activity execution location（开立科室→执行科室规则）';
COMMENT ON COLUMN cus_activity_execution_location.id IS '主键';
COMMENT ON COLUMN cus_activity_execution_location.tenant_id IS '租户标识';
COMMENT ON COLUMN cus_activity_execution_location.created_at IS '创建时间';
COMMENT ON COLUMN cus_activity_execution_location.updated_at IS '最后更新时间';
COMMENT ON COLUMN cus_activity_execution_location.created_by IS '创建人';
COMMENT ON COLUMN cus_activity_execution_location.updated_by IS '最后更新人';
COMMENT ON COLUMN cus_activity_execution_location.is_deleted IS '软删标记';
COMMENT ON COLUMN cus_activity_execution_location.deleted_at IS '删除时间';
COMMENT ON COLUMN cus_activity_execution_location.deleted_by IS '删除人';
COMMENT ON COLUMN cus_activity_execution_location.version IS '乐观锁版本号';
COMMENT ON COLUMN cus_activity_execution_location.organization_id IS '开立科室标识（与取药科室 organization_id 同口径）';
COMMENT ON COLUMN cus_activity_execution_location.activity_definition_id IS '诊疗定义标识；空表示该开立科室下该诊疗大类默认规则';
COMMENT ON COLUMN cus_activity_execution_location.activity_category_code IS '活动类别编码';
COMMENT ON COLUMN cus_activity_execution_location.execution_org_id IS '执行科室标识';
COMMENT ON COLUMN cus_activity_execution_location.execution_location_id IS '执行位置标识';
COMMENT ON COLUMN cus_activity_execution_location.start_time IS '生效开始时间';
COMMENT ON COLUMN cus_activity_execution_location.end_time IS '生效结束时间';
COMMENT ON COLUMN cus_activity_execution_location.priority IS '优先级（数值越小越优先，1 最优先；与取药科室一致）';
COMMENT ON COLUMN cus_activity_execution_location.display_order IS '展示顺序';
COMMENT ON COLUMN cus_activity_execution_location.remark IS '备注';

CREATE INDEX IF NOT EXISTS idx_cus_act_exec_loc_org_id
    ON cus_activity_execution_location (organization_id)
    WHERE is_deleted = false;

-- seed: cus_activity_execution_location

-- from V30__cus_charge_item_auto_gen.sql
-- cus_charge_item_auto_gen（建表）
-- Flyway V30

CREATE TABLE IF NOT EXISTS "public"."cus_charge_item_auto_gen" (
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
    "rule_name" varchar(255) NOT NULL,
    "status_enum" varchar(255) NOT NULL,
    "trigger_source_table" varchar(255) NOT NULL,
    "trigger_source_id" int8 NOT NULL,
    "charge_definition_id" int8 NOT NULL,
    "performing_org_id" int8,
    "quantity_value" numeric(18,2) NOT NULL,
    "priority" int4 NOT NULL,
    "remark" varchar(255),
    "encounter_id" int8 NOT NULL,
    "business_time" time(6) NOT NULL DEFAULT TIME '23:59:59',
    "billing_practitioner_id" int8,
    CONSTRAINT "cus_charge_item_auto_gen_pkey" PRIMARY KEY ("id")
);

CREATE INDEX IF NOT EXISTS idx_cus_charge_item_auto_gen_encounter_id
    ON cus_charge_item_auto_gen (encounter_id)
    WHERE is_deleted = false;
COMMENT ON TABLE cus_charge_item_auto_gen IS '自定义 charge item auto gen';
COMMENT ON COLUMN cus_charge_item_auto_gen.id IS '主键';
COMMENT ON COLUMN cus_charge_item_auto_gen.tenant_id IS '租户标识';
COMMENT ON COLUMN cus_charge_item_auto_gen.created_at IS '创建时间';
COMMENT ON COLUMN cus_charge_item_auto_gen.updated_at IS '最后更新时间';
COMMENT ON COLUMN cus_charge_item_auto_gen.created_by IS '创建人';
COMMENT ON COLUMN cus_charge_item_auto_gen.updated_by IS '最后更新人';
COMMENT ON COLUMN cus_charge_item_auto_gen.is_deleted IS '软删标记';
COMMENT ON COLUMN cus_charge_item_auto_gen.deleted_at IS '删除时间';
COMMENT ON COLUMN cus_charge_item_auto_gen.deleted_by IS '删除人';
COMMENT ON COLUMN cus_charge_item_auto_gen.version IS '乐观锁版本号';
COMMENT ON COLUMN cus_charge_item_auto_gen.rule_name IS '收费明细自动生成规则聚合根';
COMMENT ON COLUMN cus_charge_item_auto_gen.status_enum IS '状态枚举（发布状态）';
COMMENT ON COLUMN cus_charge_item_auto_gen.trigger_source_table IS '触发来源表名（落库字符串须与来源聚合主表表名一致）';
COMMENT ON COLUMN cus_charge_item_auto_gen.trigger_source_id IS '触发来源主键';
COMMENT ON COLUMN cus_charge_item_auto_gen.charge_definition_id IS '收费项定价标识（adm_charge_item_definition.id）';
COMMENT ON COLUMN cus_charge_item_auto_gen.performing_org_id IS '执行科室标识（为空表示全局规则）';
COMMENT ON COLUMN cus_charge_item_auto_gen.quantity_value IS '默认数量（大于 0）';
COMMENT ON COLUMN cus_charge_item_auto_gen.priority IS '优先级（数值越小越靠前）';
COMMENT ON COLUMN cus_charge_item_auto_gen.remark IS '备注';
COMMENT ON COLUMN cus_charge_item_auto_gen.encounter_id IS '住院就诊标识（adm_encounter.id）';
COMMENT ON COLUMN cus_charge_item_auto_gen.business_time IS '滚费业务时刻（billDate 当日；默认 23:59:59；护理分支不读本列）';
COMMENT ON COLUMN cus_charge_item_auto_gen.billing_practitioner_id IS '滚费计费人（adm_practitioner.id；写入 Charge.performer_id / Procedure 主执行人）';

-- from V31__cus_condition_template.sql
-- cus_condition_template（建表）
-- Flyway V31

CREATE TABLE IF NOT EXISTS "public"."cus_condition_template" (
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
    "object_id" int8 NOT NULL,
    "definition_id" int8 NOT NULL,
    "binding_enum" varchar(255) NOT NULL,
    "sort_weight" int4,
    "remark" varchar(255),
    CONSTRAINT "cus_condition_template_pkey" PRIMARY KEY ("id")
);
COMMENT ON TABLE cus_condition_template IS '自定义 condition template';
COMMENT ON COLUMN cus_condition_template.id IS '主键';
COMMENT ON COLUMN cus_condition_template.tenant_id IS '租户标识';
COMMENT ON COLUMN cus_condition_template.created_at IS '创建时间';
COMMENT ON COLUMN cus_condition_template.updated_at IS '最后更新时间';
COMMENT ON COLUMN cus_condition_template.created_by IS '创建人';
COMMENT ON COLUMN cus_condition_template.updated_by IS '最后更新人';
COMMENT ON COLUMN cus_condition_template.is_deleted IS '软删标记';
COMMENT ON COLUMN cus_condition_template.deleted_at IS '删除时间';
COMMENT ON COLUMN cus_condition_template.deleted_by IS '删除人';
COMMENT ON COLUMN cus_condition_template.version IS '乐观锁版本号';
COMMENT ON COLUMN cus_condition_template.object_id IS '诊断常用模板聚合根';
COMMENT ON COLUMN cus_condition_template.definition_id IS '诊断定义标识';
COMMENT ON COLUMN cus_condition_template.binding_enum IS '绑定类型枚举';
COMMENT ON COLUMN cus_condition_template.sort_weight IS '排序权重';
COMMENT ON COLUMN cus_condition_template.remark IS '备注';

-- from V32__cus_encounter_history.sql
-- cus_encounter_history（建表）
-- Flyway V32

CREATE TABLE IF NOT EXISTS "public"."cus_encounter_history" (
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
    "encounter_id" int8 NOT NULL,
    "request_id" int8,
    "request_table_name" varchar(255),
    "original_organization_id" int8,
    "target_organization_id" int8,
    "original_location_id" int8,
    "target_location_id" int8,
    "out_way_code" varchar(255),
    CONSTRAINT "cus_encounter_history_pkey" PRIMARY KEY ("id")
);
COMMENT ON TABLE cus_encounter_history IS '自定义 encounter history';
COMMENT ON COLUMN cus_encounter_history.id IS '主键';
COMMENT ON COLUMN cus_encounter_history.tenant_id IS '租户标识';
COMMENT ON COLUMN cus_encounter_history.created_at IS '创建时间';
COMMENT ON COLUMN cus_encounter_history.updated_at IS '最后更新时间';
COMMENT ON COLUMN cus_encounter_history.created_by IS '创建人';
COMMENT ON COLUMN cus_encounter_history.updated_by IS '最后更新人';
COMMENT ON COLUMN cus_encounter_history.is_deleted IS '软删标记';
COMMENT ON COLUMN cus_encounter_history.deleted_at IS '删除时间';
COMMENT ON COLUMN cus_encounter_history.deleted_by IS '删除人';
COMMENT ON COLUMN cus_encounter_history.version IS '乐观锁版本号';
COMMENT ON COLUMN cus_encounter_history.name IS '就诊历史里程碑聚合根';
COMMENT ON COLUMN cus_encounter_history.encounter_id IS '就诊标识';
COMMENT ON COLUMN cus_encounter_history.request_id IS '请求标识（如 ServiceRequest）';
COMMENT ON COLUMN cus_encounter_history.request_table_name IS '请求表名（与 requestId 成对出现）';
COMMENT ON COLUMN cus_encounter_history.original_organization_id IS '原科室标识';
COMMENT ON COLUMN cus_encounter_history.target_organization_id IS '目标科室标识';
COMMENT ON COLUMN cus_encounter_history.original_location_id IS '原位置标识（病区/床位等）';
COMMENT ON COLUMN cus_encounter_history.target_location_id IS '目标位置标识（病区/床位等）';
COMMENT ON COLUMN cus_encounter_history.out_way_code IS '出院方式编码（仅离院类里程碑使用）';

-- from V33__cus_frequency.sql
-- cus_frequency（建表 + 种子）
-- Flyway V33

CREATE TABLE IF NOT EXISTS "public"."cus_frequency" (
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
    "rate_code" varchar(255),
    "name" varchar(255) NOT NULL,
    "cycle_type" varchar(32) NOT NULL,
    "day_count" int4,
    "day_interval" int4,
    "day_times" varchar(255),
    "week_cycle_flag" bool,
    "total_execution_count" int4,
    "execution_period" int4,
    "execution_period_unit" varchar(255),
    "applicable_scenes" varchar(255),
    "allow_cross_day" varchar(32),
    "active_flag" bool NOT NULL,
    "sort_no" int4,
    CONSTRAINT "cus_frequency_pkey" PRIMARY KEY ("id")
);
COMMENT ON TABLE cus_frequency IS '自定义 frequency';
COMMENT ON COLUMN cus_frequency.id IS '主键';
COMMENT ON COLUMN cus_frequency.tenant_id IS '租户标识';
COMMENT ON COLUMN cus_frequency.created_at IS '创建时间';
COMMENT ON COLUMN cus_frequency.updated_at IS '最后更新时间';
COMMENT ON COLUMN cus_frequency.created_by IS '创建人';
COMMENT ON COLUMN cus_frequency.updated_by IS '最后更新人';
COMMENT ON COLUMN cus_frequency.is_deleted IS '软删标记';
COMMENT ON COLUMN cus_frequency.deleted_at IS '删除时间';
COMMENT ON COLUMN cus_frequency.deleted_by IS '删除人';
COMMENT ON COLUMN cus_frequency.version IS '乐观锁版本号';
COMMENT ON COLUMN cus_frequency.rate_code IS '频次聚合根实体';
COMMENT ON COLUMN cus_frequency.name IS '频次名称';
COMMENT ON COLUMN cus_frequency.cycle_type IS '周期类型（{ com.openhis.health.domain.share.enums.FrequencyCycleType#getCode()}）';
COMMENT ON COLUMN cus_frequency.day_count IS '日计数';
COMMENT ON COLUMN cus_frequency.day_interval IS '日间隔';
COMMENT ON COLUMN cus_frequency.day_times IS '日执行时点串';
COMMENT ON COLUMN cus_frequency.week_cycle_flag IS '周周期标记';
COMMENT ON COLUMN cus_frequency.total_execution_count IS '总执行次数';
COMMENT ON COLUMN cus_frequency.execution_period IS '执行周期';
COMMENT ON COLUMN cus_frequency.execution_period_unit IS '执行周期单位';
COMMENT ON COLUMN cus_frequency.applicable_scenes IS '适用场景';
COMMENT ON COLUMN cus_frequency.allow_cross_day IS '允许跨日（{ com.openhis.health.domain.share.enums.FrequencyAllowCrossDay#getCode()}）';
COMMENT ON COLUMN cus_frequency.active_flag IS '启用标记';
COMMENT ON COLUMN cus_frequency.sort_no IS '排序号';

-- seed: cus_frequency → 出厂数据在 V10__seed_platform.sql（勿在 DDL 脚本内嵌种子）

-- from V34__cus_order_template.sql
-- cus_order_template（建表）
-- Flyway V34

CREATE TABLE IF NOT EXISTS "public"."cus_order_template" (
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
    "object_id" int8 NOT NULL,
    "binding_enum" varchar(255) NOT NULL,
    "scene_enum" varchar(255) NOT NULL,
    "template_code" varchar(255),
    "name" varchar(255) NOT NULL,
    "description" varchar(255),
    "sort_weight" int4,
    "status_enum" varchar(255) NOT NULL,
    "remark" varchar(255),
    "template_kind_enum" varchar(255) NOT NULL DEFAULT 'GENERAL',
    "default_chinese_herbs_dose_quantity" numeric(18, 2),
    "default_suffering_flag" bool,
    "template_source_enum" varchar(255) NOT NULL DEFAULT 'MAINTAINED',
    CONSTRAINT "cus_order_template_pkey" PRIMARY KEY ("id")
);

CREATE INDEX IF NOT EXISTS "idx_cus_order_template_source_list"
    ON "public"."cus_order_template" ("tenant_id", "object_id", "binding_enum", "template_source_enum", "template_kind_enum", "status_enum")
    WHERE "is_deleted" = false;
COMMENT ON TABLE cus_order_template IS '自定义 order template';
COMMENT ON COLUMN cus_order_template.id IS '主键';
COMMENT ON COLUMN cus_order_template.tenant_id IS '租户标识';
COMMENT ON COLUMN cus_order_template.created_at IS '创建时间';
COMMENT ON COLUMN cus_order_template.updated_at IS '最后更新时间';
COMMENT ON COLUMN cus_order_template.created_by IS '创建人';
COMMENT ON COLUMN cus_order_template.updated_by IS '最后更新人';
COMMENT ON COLUMN cus_order_template.is_deleted IS '软删标记';
COMMENT ON COLUMN cus_order_template.deleted_at IS '删除时间';
COMMENT ON COLUMN cus_order_template.deleted_by IS '删除人';
COMMENT ON COLUMN cus_order_template.version IS '乐观锁版本号';
COMMENT ON COLUMN cus_order_template.object_id IS '组套模板头聚合根';
COMMENT ON COLUMN cus_order_template.binding_enum IS '绑定类型（个人/科室/全院）';
COMMENT ON COLUMN cus_order_template.scene_enum IS '适用场景（门诊/住院/通用）';
COMMENT ON COLUMN cus_order_template.template_code IS '模板编码（可空，非空时建议唯一）';
COMMENT ON COLUMN cus_order_template.name IS '模板名称';
COMMENT ON COLUMN cus_order_template.description IS '描述';
COMMENT ON COLUMN cus_order_template.sort_weight IS '列表排序权重（同层级内升序；数值越小越靠前）';
COMMENT ON COLUMN cus_order_template.status_enum IS '状态（草稿/启用/停用）';
COMMENT ON COLUMN cus_order_template.remark IS '备注';
COMMENT ON COLUMN cus_order_template.template_kind_enum IS '组套种类（GENERAL/TCM/PRICING）';
COMMENT ON COLUMN cus_order_template.default_chinese_herbs_dose_quantity IS 'TCM 组套默认中药付数';
COMMENT ON COLUMN cus_order_template.default_suffering_flag IS 'TCM 组套默认代煎标志';
COMMENT ON COLUMN cus_order_template.template_source_enum IS '模板来源（MAINTAINED=维护组套，FROM_ADVICE=医嘱模板/开嘱另存）';

-- ----- seed sync from DB (cus_order_template, 4 rows) -----

-- from V35__cus_order_template_line.sql
-- cus_order_template_line（建表）
-- Flyway V35

CREATE TABLE IF NOT EXISTS "public"."cus_order_template_line" (
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
    "order_template_id" int8 NOT NULL,
    "line_kind_enum" varchar(255) NOT NULL,
    "medication_definition_id" int8,
    "activity_definition_id" int8,
    "device_definition_id" int8,
    "group_key" varchar(255),
    "sort_order" int4 NOT NULL,
    "default_quantity" numeric(18,2),
    "default_unit_code" varchar(255),
    "default_days" int4,
    "frequency_id" int8,
    "route_code" varchar(255),
    "dosage_text" varchar(255),
    "default_perform_org_id" int8,
    "line_remark" varchar(255),
    "chinese_herbs_dose_quantity" numeric(18, 2),
    "suffering_flag" bool,
    CONSTRAINT "cus_order_template_line_pkey" PRIMARY KEY ("id")
);
COMMENT ON TABLE cus_order_template_line IS '自定义 order template line';
COMMENT ON COLUMN cus_order_template_line.id IS '主键';
COMMENT ON COLUMN cus_order_template_line.tenant_id IS '租户标识';
COMMENT ON COLUMN cus_order_template_line.created_at IS '创建时间';
COMMENT ON COLUMN cus_order_template_line.updated_at IS '最后更新时间';
COMMENT ON COLUMN cus_order_template_line.created_by IS '创建人';
COMMENT ON COLUMN cus_order_template_line.updated_by IS '最后更新人';
COMMENT ON COLUMN cus_order_template_line.is_deleted IS '软删标记';
COMMENT ON COLUMN cus_order_template_line.deleted_at IS '删除时间';
COMMENT ON COLUMN cus_order_template_line.deleted_by IS '删除人';
COMMENT ON COLUMN cus_order_template_line.version IS '乐观锁版本号';
COMMENT ON COLUMN cus_order_template_line.order_template_id IS 'order模板标识';
COMMENT ON COLUMN cus_order_template_line.line_kind_enum IS 'linekind枚举值';
COMMENT ON COLUMN cus_order_template_line.medication_definition_id IS '药品定义标识';
COMMENT ON COLUMN cus_order_template_line.activity_definition_id IS '活动定义标识';
COMMENT ON COLUMN cus_order_template_line.device_definition_id IS '设备定义标识';
COMMENT ON COLUMN cus_order_template_line.group_key IS '分组key';
COMMENT ON COLUMN cus_order_template_line.sort_order IS '排序号';
COMMENT ON COLUMN cus_order_template_line.default_quantity IS 'default数量';
COMMENT ON COLUMN cus_order_template_line.default_unit_code IS 'default单元编码';
COMMENT ON COLUMN cus_order_template_line.default_days IS '默认天数';
COMMENT ON COLUMN cus_order_template_line.frequency_id IS '频次标识';
COMMENT ON COLUMN cus_order_template_line.route_code IS 'route编码';
COMMENT ON COLUMN cus_order_template_line.dosage_text IS 'dosage文本';
COMMENT ON COLUMN cus_order_template_line.default_perform_org_id IS 'defaultperformorg标识';
COMMENT ON COLUMN cus_order_template_line.line_remark IS '行备注';
COMMENT ON COLUMN cus_order_template_line.chinese_herbs_dose_quantity IS '组套模板行（聚合内子实体）';
COMMENT ON COLUMN cus_order_template_line.suffering_flag IS 'TCM 行级代煎标志';

-- ----- seed sync from DB (cus_order_template_line, 13 rows) -----

-- from V36__cus_price_adjustment.sql
-- cus_price_adjustment（建表 · 单据型调价项目行最终态）
-- Flyway V36

CREATE TABLE IF NOT EXISTS "public"."cus_price_adjustment" (
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
    "item_table" varchar(255) NOT NULL,
    "item_id" int8 NOT NULL,
    "new_purchase_amount" numeric(19, 6),
    "new_selling_amount" numeric(19, 6),
    "adjustment_reason" varchar(255) NOT NULL,
    "adjustment_time" timestamp(6) NOT NULL,
    "status_enum" varchar(255) NOT NULL,
    "submitter_id" int8,
    "submit_time" timestamp(6),
    "approver_id" int8,
    "approve_time" timestamp(6),
    "approve_comment" varchar(255),
    "operator_id" int8 NOT NULL,
    "org_id" int8 NOT NULL,
    CONSTRAINT "cus_price_adjustment_pkey" PRIMARY KEY ("id")
);

CREATE INDEX IF NOT EXISTS idx_cus_price_adjustment_bus_no ON cus_price_adjustment (bus_no);
CREATE INDEX IF NOT EXISTS idx_cus_price_adjustment_item_pending ON cus_price_adjustment (item_table, item_id, status_enum);

COMMENT ON TABLE cus_price_adjustment IS '调价单项目行（同 bus_no 多行）';
COMMENT ON COLUMN cus_price_adjustment.id IS '主键';
COMMENT ON COLUMN cus_price_adjustment.tenant_id IS '租户标识';
COMMENT ON COLUMN cus_price_adjustment.created_at IS '创建时间';
COMMENT ON COLUMN cus_price_adjustment.updated_at IS '最后更新时间';
COMMENT ON COLUMN cus_price_adjustment.created_by IS '创建人';
COMMENT ON COLUMN cus_price_adjustment.updated_by IS '最后更新人';
COMMENT ON COLUMN cus_price_adjustment.is_deleted IS '软删标记';
COMMENT ON COLUMN cus_price_adjustment.deleted_at IS '删除时间';
COMMENT ON COLUMN cus_price_adjustment.deleted_by IS '删除人';
COMMENT ON COLUMN cus_price_adjustment.version IS '乐观锁版本号';
COMMENT ON COLUMN cus_price_adjustment.bus_no IS '调价单号';
COMMENT ON COLUMN cus_price_adjustment.item_table IS '项目物理表（药品/耗材定义表）';
COMMENT ON COLUMN cus_price_adjustment.item_id IS '项目定义主键';
COMMENT ON COLUMN cus_price_adjustment.new_purchase_amount IS '目标进价（可空表示不调进价）';
COMMENT ON COLUMN cus_price_adjustment.new_selling_amount IS '目标售价（可空表示不调售价）';
COMMENT ON COLUMN cus_price_adjustment.adjustment_reason IS '调价原因';
COMMENT ON COLUMN cus_price_adjustment.adjustment_time IS '调价时间';
COMMENT ON COLUMN cus_price_adjustment.status_enum IS '状态枚举值';
COMMENT ON COLUMN cus_price_adjustment.submitter_id IS 'submitter标识';
COMMENT ON COLUMN cus_price_adjustment.submit_time IS 'submit时间';
COMMENT ON COLUMN cus_price_adjustment.approver_id IS 'approver标识';
COMMENT ON COLUMN cus_price_adjustment.approve_time IS 'approve时间';
COMMENT ON COLUMN cus_price_adjustment.approve_comment IS '审批意见';
COMMENT ON COLUMN cus_price_adjustment.operator_id IS '操作人标识';
COMMENT ON COLUMN cus_price_adjustment.org_id IS '机构标识';

-- from V37__cus_service_device_template.sql
-- cus_service_device_template（建表）
-- Flyway V37

CREATE TABLE IF NOT EXISTS "public"."cus_service_device_template" (
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
    "range_code" varchar(255) NOT NULL,
    "type_code" varchar(255) NOT NULL,
    "item_table" varchar(255) NOT NULL,
    "item_id" varchar(255) NOT NULL,
    "dev_act_table" varchar(255) NOT NULL,
    "dev_act_id" int8 NOT NULL,
    "quantity" numeric(18,2) NOT NULL,
    "unit_code" varchar(255) NOT NULL,
    "status_enum" varchar(255) NOT NULL,
    CONSTRAINT "cus_service_device_template_pkey" PRIMARY KEY ("id")
);
COMMENT ON TABLE cus_service_device_template IS '自定义 service device template';
COMMENT ON COLUMN cus_service_device_template.id IS '主键';
COMMENT ON COLUMN cus_service_device_template.tenant_id IS '租户标识';
COMMENT ON COLUMN cus_service_device_template.created_at IS '创建时间';
COMMENT ON COLUMN cus_service_device_template.updated_at IS '最后更新时间';
COMMENT ON COLUMN cus_service_device_template.created_by IS '创建人';
COMMENT ON COLUMN cus_service_device_template.updated_by IS '最后更新人';
COMMENT ON COLUMN cus_service_device_template.is_deleted IS '软删标记';
COMMENT ON COLUMN cus_service_device_template.deleted_at IS '删除时间';
COMMENT ON COLUMN cus_service_device_template.deleted_by IS '删除人';
COMMENT ON COLUMN cus_service_device_template.version IS '乐观锁版本号';
COMMENT ON COLUMN cus_service_device_template.range_code IS '诊疗耗材绑定聚合根';
COMMENT ON COLUMN cus_service_device_template.type_code IS '绑定类型编码（1 用法/2 诊疗/3 号源）';
COMMENT ON COLUMN cus_service_device_template.item_table IS '来源表名';
COMMENT ON COLUMN cus_service_device_template.item_id IS '来源项标识（跨表主键类型不一，持久化为 String）';
COMMENT ON COLUMN cus_service_device_template.dev_act_table IS '目标表名（诊疗定义表或耗材定义表）';
COMMENT ON COLUMN cus_service_device_template.dev_act_id IS '目标项标识（诊疗或耗材定义主键）';
COMMENT ON COLUMN cus_service_device_template.quantity IS '绑定数量（> 0）';
COMMENT ON COLUMN cus_service_device_template.unit_code IS '单位编码';
COMMENT ON COLUMN cus_service_device_template.status_enum IS '发布状态（active/retired）';

-- seed: cus_service_device_template
-- ----- seed sync from DB (cus_service_device_template, 1 rows) -----

-- from V38__cus_service_location_template.sql
-- cus_service_location_template（建表）
-- Flyway V38

CREATE TABLE IF NOT EXISTS "public"."cus_service_location_template" (
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
    "organization_id" int8 NOT NULL,
    "item_table" varchar(255) NOT NULL,
    "distribution_category_code" varchar(255) NOT NULL,
    "def_location_id" int8 NOT NULL,
    "item_definition_id" int8,
    "method_code" varchar(255),
    "start_time" time(6),
    "end_time" time(6),
    "priority" int4 NOT NULL,
    "display_order" int4,
    "remark" varchar(255),
    CONSTRAINT "cus_service_location_template_pkey" PRIMARY KEY ("id")
);
COMMENT ON TABLE cus_service_location_template IS '自定义 service location template';
COMMENT ON COLUMN cus_service_location_template.id IS '主键';
COMMENT ON COLUMN cus_service_location_template.tenant_id IS '租户标识';
COMMENT ON COLUMN cus_service_location_template.created_at IS '创建时间';
COMMENT ON COLUMN cus_service_location_template.updated_at IS '最后更新时间';
COMMENT ON COLUMN cus_service_location_template.created_by IS '创建人';
COMMENT ON COLUMN cus_service_location_template.updated_by IS '最后更新人';
COMMENT ON COLUMN cus_service_location_template.is_deleted IS '软删标记';
COMMENT ON COLUMN cus_service_location_template.deleted_at IS '删除时间';
COMMENT ON COLUMN cus_service_location_template.deleted_by IS '删除人';
COMMENT ON COLUMN cus_service_location_template.version IS '乐观锁版本号';
COMMENT ON COLUMN cus_service_location_template.organization_id IS '机构标识';
COMMENT ON COLUMN cus_service_location_template.item_table IS '项table';
COMMENT ON COLUMN cus_service_location_template.distribution_category_code IS 'distributioncategory编码';
COMMENT ON COLUMN cus_service_location_template.def_location_id IS 'def位置标识';
COMMENT ON COLUMN cus_service_location_template.item_definition_id IS '项定义标识';
COMMENT ON COLUMN cus_service_location_template.method_code IS 'method编码';
COMMENT ON COLUMN cus_service_location_template.start_time IS '开始时间';
COMMENT ON COLUMN cus_service_location_template.end_time IS '结束时间';
COMMENT ON COLUMN cus_service_location_template.priority IS '优先级';
COMMENT ON COLUMN cus_service_location_template.display_order IS '显示顺序';
COMMENT ON COLUMN cus_service_location_template.remark IS '备注';

-- seed: cus_service_location_template

-- from V39__cus_supplier.sql
-- cus_supplier（建表 + 种子数据）
-- Flyway V39

CREATE TABLE IF NOT EXISTS "public"."cus_supplier" (
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
    "type_enum" varchar(255) NOT NULL,
    "credit_code" varchar(255) NOT NULL,
    "legal_representative" varchar(255),
    "address" varchar(255),
    "py_str" varchar(255) NOT NULL,
    "wb_str" varchar(255) NOT NULL,
    "phone" varchar(255),
    "email" varchar(255),
    "bank_name" varchar(255),
    "bank_account" varchar(255),
    "org_id" int8 NOT NULL,
    "display_order" int4,
    "status_enum" varchar(255) NOT NULL,
    "note" varchar(255),
    CONSTRAINT "cus_supplier_pkey" PRIMARY KEY ("id")
);
COMMENT ON TABLE cus_supplier IS '自定义 supplier';
COMMENT ON COLUMN cus_supplier.id IS '主键';
COMMENT ON COLUMN cus_supplier.tenant_id IS '租户标识';
COMMENT ON COLUMN cus_supplier.created_at IS '创建时间';
COMMENT ON COLUMN cus_supplier.updated_at IS '最后更新时间';
COMMENT ON COLUMN cus_supplier.created_by IS '创建人';
COMMENT ON COLUMN cus_supplier.updated_by IS '最后更新人';
COMMENT ON COLUMN cus_supplier.is_deleted IS '软删标记';
COMMENT ON COLUMN cus_supplier.deleted_at IS '删除时间';
COMMENT ON COLUMN cus_supplier.deleted_by IS '删除人';
COMMENT ON COLUMN cus_supplier.version IS '乐观锁版本号';
COMMENT ON COLUMN cus_supplier.bus_no IS '业务编号';
COMMENT ON COLUMN cus_supplier.name IS '名称';
COMMENT ON COLUMN cus_supplier.type_enum IS '类型枚举值';
COMMENT ON COLUMN cus_supplier.credit_code IS 'credit编码';
COMMENT ON COLUMN cus_supplier.legal_representative IS '法定代表人';
COMMENT ON COLUMN cus_supplier.address IS '地址';
COMMENT ON COLUMN cus_supplier.py_str IS '拼音码';
COMMENT ON COLUMN cus_supplier.wb_str IS '五笔码';
COMMENT ON COLUMN cus_supplier.phone IS '电话';
COMMENT ON COLUMN cus_supplier.email IS '邮箱';
COMMENT ON COLUMN cus_supplier.bank_name IS 'bank名称';
COMMENT ON COLUMN cus_supplier.bank_account IS 'bank账户';
COMMENT ON COLUMN cus_supplier.org_id IS '机构标识';
COMMENT ON COLUMN cus_supplier.display_order IS '显示顺序';
COMMENT ON COLUMN cus_supplier.status_enum IS '状态枚举值';
COMMENT ON COLUMN cus_supplier.note IS '备注说明';

-- seed: cus_supplier

-- from V40__cus_terminology_code_mapping.sql
-- cus_terminology_code_mapping（建表）
-- Flyway V40

CREATE TABLE IF NOT EXISTS "public"."cus_terminology_code_mapping" (
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
    "source_item_table" varchar(255) NOT NULL,
    "source_item_no" varchar(255) NOT NULL,
    "target_item_no" varchar(255) NOT NULL,
    "target_name" varchar(255),
    "active_flag" bool NOT NULL,
    CONSTRAINT "cus_terminology_code_mapping_pkey" PRIMARY KEY ("id")
);
COMMENT ON TABLE cus_terminology_code_mapping IS '自定义 terminology code mapping';
COMMENT ON COLUMN cus_terminology_code_mapping.id IS '主键';
COMMENT ON COLUMN cus_terminology_code_mapping.tenant_id IS '租户标识';
COMMENT ON COLUMN cus_terminology_code_mapping.created_at IS '创建时间';
COMMENT ON COLUMN cus_terminology_code_mapping.updated_at IS '最后更新时间';
COMMENT ON COLUMN cus_terminology_code_mapping.created_by IS '创建人';
COMMENT ON COLUMN cus_terminology_code_mapping.updated_by IS '最后更新人';
COMMENT ON COLUMN cus_terminology_code_mapping.is_deleted IS '软删标记';
COMMENT ON COLUMN cus_terminology_code_mapping.deleted_at IS '删除时间';
COMMENT ON COLUMN cus_terminology_code_mapping.deleted_by IS '删除人';
COMMENT ON COLUMN cus_terminology_code_mapping.version IS '乐观锁版本号';
COMMENT ON COLUMN cus_terminology_code_mapping.source_item_table IS '来源项table';
COMMENT ON COLUMN cus_terminology_code_mapping.source_item_no IS '来源项编号';
COMMENT ON COLUMN cus_terminology_code_mapping.target_item_no IS 'target项编号';
COMMENT ON COLUMN cus_terminology_code_mapping.target_name IS 'target名称';
COMMENT ON COLUMN cus_terminology_code_mapping.active_flag IS '是否启用';

-- from V50__ter_code_system.sql
-- ter_code_system（建表 + 种子数据）
-- Flyway V57

CREATE TABLE IF NOT EXISTS ter_code_system (
    id BIGINT PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    title VARCHAR(200),
    description TEXT,
    version VARCHAR(50) DEFAULT '1.0',
    status VARCHAR(255) NOT NULL,
    tenant_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,
    deleted_by BIGINT
);

CREATE INDEX IF NOT EXISTS idx_code_system_tenant_id ON ter_code_system(tenant_id);

CREATE INDEX IF NOT EXISTS idx_code_system_status ON ter_code_system(status);

CREATE INDEX IF NOT EXISTS idx_code_system_created_at ON ter_code_system(created_at);

CREATE INDEX IF NOT EXISTS idx_code_system_is_deleted ON ter_code_system(is_deleted);
COMMENT ON TABLE ter_code_system IS '编码系统';
COMMENT ON COLUMN ter_code_system.id IS '主键';
COMMENT ON COLUMN ter_code_system.name IS '编码系统';
COMMENT ON COLUMN ter_code_system.title IS '编码系统标题';
COMMENT ON COLUMN ter_code_system.description IS '描述';
COMMENT ON COLUMN ter_code_system.version IS '乐观锁版本号';
COMMENT ON COLUMN ter_code_system.status IS '状态：draft';
COMMENT ON COLUMN ter_code_system.tenant_id IS '租户标识';
COMMENT ON COLUMN ter_code_system.created_at IS '创建时间';
COMMENT ON COLUMN ter_code_system.updated_at IS '最后更新时间';
COMMENT ON COLUMN ter_code_system.created_by IS '创建人';
COMMENT ON COLUMN ter_code_system.updated_by IS '最后更新人';
COMMENT ON COLUMN ter_code_system.is_deleted IS '软删标记';
COMMENT ON COLUMN ter_code_system.deleted_at IS '删除时间';
COMMENT ON COLUMN ter_code_system.deleted_by IS '删除人';

-- seed: ter_code_system

-- from V51__ter_concept.sql
-- ter_concept（建表 + 种子数据）
-- Flyway V58

CREATE TABLE IF NOT EXISTS ter_concept (
    id BIGINT PRIMARY KEY,
    code_system_id BIGINT NOT NULL,
    code VARCHAR(100) NOT NULL,
    display VARCHAR(500) NOT NULL,
    definition TEXT,
    parent_id BIGINT,
    sort_order BIGINT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    css_class VARCHAR(100),
    list_class VARCHAR(100),
    is_default VARCHAR(1),
    tenant_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,
    deleted_by BIGINT,
    CONSTRAINT fk_concept_code_system FOREIGN KEY (code_system_id) REFERENCES ter_code_system(id),
    CONSTRAINT fk_concept_parent FOREIGN KEY (parent_id) REFERENCES ter_concept(id),
    CONSTRAINT uk_concept_code_system_code UNIQUE (code_system_id, code)
);

CREATE INDEX IF NOT EXISTS idx_concept_code ON ter_concept(code);

CREATE INDEX IF NOT EXISTS idx_concept_parent ON ter_concept(parent_id);

CREATE INDEX IF NOT EXISTS idx_concept_is_active ON ter_concept(is_active);

CREATE INDEX IF NOT EXISTS idx_concept_sort_order ON ter_concept(sort_order);

CREATE INDEX IF NOT EXISTS idx_concept_created_at ON ter_concept(created_at);

CREATE INDEX IF NOT EXISTS idx_concept_is_deleted ON ter_concept(is_deleted);
COMMENT ON TABLE ter_concept IS '编码概念';
COMMENT ON COLUMN ter_concept.id IS '主键';
COMMENT ON COLUMN ter_concept.code_system_id IS '编码概念';
COMMENT ON COLUMN ter_concept.code IS '编码';
COMMENT ON COLUMN ter_concept.display IS '显示名称';
COMMENT ON COLUMN ter_concept.definition IS '定义';
COMMENT ON COLUMN ter_concept.parent_id IS 'parent标识';
COMMENT ON COLUMN ter_concept.sort_order IS '父概念（支持层级结构，可选） / private Concept parent';
COMMENT ON COLUMN ter_concept.is_active IS '是否启用';
COMMENT ON COLUMN ter_concept.css_class IS 'CSS样式类';
COMMENT ON COLUMN ter_concept.list_class IS '列表样式类';
COMMENT ON COLUMN ter_concept.is_default IS '是否默认';
COMMENT ON COLUMN ter_concept.tenant_id IS '租户标识';
COMMENT ON COLUMN ter_concept.created_at IS '创建时间';
COMMENT ON COLUMN ter_concept.updated_at IS '最后更新时间';
COMMENT ON COLUMN ter_concept.created_by IS '创建人';
COMMENT ON COLUMN ter_concept.updated_by IS '最后更新人';
COMMENT ON COLUMN ter_concept.is_deleted IS '软删标记';
COMMENT ON COLUMN ter_concept.deleted_at IS '删除时间';
COMMENT ON COLUMN ter_concept.deleted_by IS '删除人';

-- seed: ter_concept
-- tcm_method_code：常用中药用法（默认「水煎服」）+ 参照 method_code 补充
-- ----- seed sync from DB (ter_concept, 29 rows) -----

-- from V77__cus_activity_observation_link.sql
-- cus_activity_observation_link（建表）
-- Flyway V78 · DM-CUS-013

CREATE TABLE IF NOT EXISTS "public"."cus_activity_observation_link" (
    "id" int8 NOT NULL,
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
    "activity_definition_id" int8 NOT NULL,
    "observation_definition_id" int8 NOT NULL,
    "sort_num" int4 NOT NULL DEFAULT 0,
    "required_flag" bool NOT NULL DEFAULT true,
    CONSTRAINT "cus_activity_observation_link_pkey" PRIMARY KEY ("id")
);
CREATE UNIQUE INDEX IF NOT EXISTS "uk_aol_activity_od"
    ON "public"."cus_activity_observation_link" ("activity_definition_id", "observation_definition_id")
    WHERE "is_deleted" = false;
CREATE INDEX IF NOT EXISTS idx_aol_od_id
    ON cus_activity_observation_link (observation_definition_id)
    WHERE is_deleted = FALSE;
COMMENT ON INDEX idx_aol_od_id IS
    'AOL：按小项反查绑定项目';

COMMENT ON TABLE cus_activity_observation_link IS '体检项目 AD ↔ 小项 OD 绑定';
COMMENT ON COLUMN cus_activity_observation_link.id IS '主键';
COMMENT ON COLUMN cus_activity_observation_link.tenant_id IS '租户标识';
COMMENT ON COLUMN cus_activity_observation_link.org_id IS '机构标识';
COMMENT ON COLUMN cus_activity_observation_link.created_at IS '创建时间';
COMMENT ON COLUMN cus_activity_observation_link.updated_at IS '最后更新时间';
COMMENT ON COLUMN cus_activity_observation_link.created_by IS '创建人';
COMMENT ON COLUMN cus_activity_observation_link.updated_by IS '最后更新人';
COMMENT ON COLUMN cus_activity_observation_link.is_deleted IS '软删标记';
COMMENT ON COLUMN cus_activity_observation_link.deleted_at IS '删除时间';
COMMENT ON COLUMN cus_activity_observation_link.deleted_by IS '删除人';
COMMENT ON COLUMN cus_activity_observation_link.version IS '乐观锁版本号';
COMMENT ON COLUMN cus_activity_observation_link.activity_definition_id IS '活动定义标识';
COMMENT ON COLUMN cus_activity_observation_link.observation_definition_id IS '观察项定义标识';
COMMENT ON COLUMN cus_activity_observation_link.sort_num IS '排序号';
COMMENT ON COLUMN cus_activity_observation_link.required_flag IS '是否必填';

-- from V85__cus_price_adjustment_detail.sql
-- cus_price_adjustment_detail（建表）
-- Flyway V86

CREATE TABLE IF NOT EXISTS cus_price_adjustment_detail (
    id int8 NOT NULL,
    tenant_id int8,
    created_at timestamptz(6) NOT NULL,
    updated_at timestamptz(6),
    created_by int8,
    updated_by int8,
    is_deleted bool NOT NULL DEFAULT false,
    deleted_at timestamptz(6),
    deleted_by int8,
    version int4 NOT NULL DEFAULT 0,
    adjustment_id int8 NOT NULL,
    lot_number varchar(255) NOT NULL,
    quantity numeric(18, 2) NOT NULL,
    old_purchase_amount numeric(19, 6),
    old_selling_amount numeric(19, 6),
    purchase_impact numeric(19, 6),
    selling_impact numeric(19, 6),
    location_id int8,
    CONSTRAINT cus_price_adjustment_detail_pkey PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_cus_price_adjustment_detail_adjustment_id ON cus_price_adjustment_detail (adjustment_id);
CREATE INDEX IF NOT EXISTS idx_cus_price_adjustment_detail_location_id
    ON cus_price_adjustment_detail (location_id)
    WHERE location_id IS NOT NULL AND is_deleted = false;

COMMENT ON TABLE cus_price_adjustment_detail IS '调价批号明细（提交时快照盈亏）';
COMMENT ON COLUMN cus_price_adjustment_detail.id IS '主键';
COMMENT ON COLUMN cus_price_adjustment_detail.tenant_id IS '租户标识';
COMMENT ON COLUMN cus_price_adjustment_detail.created_at IS '创建时间';
COMMENT ON COLUMN cus_price_adjustment_detail.updated_at IS '最后更新时间';
COMMENT ON COLUMN cus_price_adjustment_detail.created_by IS '创建人';
COMMENT ON COLUMN cus_price_adjustment_detail.updated_by IS '最后更新人';
COMMENT ON COLUMN cus_price_adjustment_detail.is_deleted IS '软删标记';
COMMENT ON COLUMN cus_price_adjustment_detail.deleted_at IS '删除时间';
COMMENT ON COLUMN cus_price_adjustment_detail.deleted_by IS '删除人';
COMMENT ON COLUMN cus_price_adjustment_detail.version IS '乐观锁版本号';
COMMENT ON COLUMN cus_price_adjustment_detail.adjustment_id IS '调价项目行主键';
COMMENT ON COLUMN cus_price_adjustment_detail.lot_number IS '批号';
COMMENT ON COLUMN cus_price_adjustment_detail.quantity IS '提交时刻影响数量（拆零）';
COMMENT ON COLUMN cus_price_adjustment_detail.old_purchase_amount IS '调前进价';
COMMENT ON COLUMN cus_price_adjustment_detail.old_selling_amount IS '调前售价';
COMMENT ON COLUMN cus_price_adjustment_detail.purchase_impact IS '进价盈亏';
COMMENT ON COLUMN cus_price_adjustment_detail.selling_impact IS '售价盈亏';
COMMENT ON COLUMN cus_price_adjustment_detail.location_id IS '库位 Location.id；物流有库存时落库；诊疗/未入库/无库位可空；仅查询与日结按库过滤，不参与写价公式';

-- from V100__doc_inventory_item_snapshot.sql
-- V100：库存明细快照静表（DM-DOC-001 · doc_inventory_item_snapshot）

CREATE TABLE IF NOT EXISTS "public"."doc_inventory_item_snapshot" (
    "id" int8 NOT NULL,
    "tenant_id" int8 NOT NULL,
    "bus_no" varchar(32) NOT NULL,
    "snapshot_time" timestamptz(6) NOT NULL,
    "inventory_item_id" int8 NOT NULL,
    "item_table" varchar(64) NOT NULL,
    "item_id" int8 NOT NULL,
    "charge_item_definition_id" int8,
    "warehouse_id" int8 NOT NULL,
    "supplier_id" int8 NOT NULL,
    "product_bus_no" varchar(64),
    "name" varchar(255) NOT NULL,
    "merchandise_name" varchar(255),
    "total_volume" varchar(255),
    "manufacturer" varchar(255),
    "approval_number" varchar(128),
    "yb_no" varchar(64),
    "trace_no" varchar(50000),
    "py_str" varchar(255),
    "wb_str" varchar(255),
    "lot_number" varchar(255) NOT NULL,
    "quantity" numeric(18, 2) NOT NULL,
    "package_unit_ratio" numeric(18, 4),
    "package_unit_code" varchar(64),
    "lot_number_cost_amount" numeric(18, 4),
    "lot_number_price_amount" numeric(18, 4),
    "line_cost_total" numeric(18, 4),
    "line_price_total" numeric(18, 4),
    "production_date" timestamptz(6),
    "expiration_date" timestamptz(6),
    "remaining_days" int4,
    "inventory_status_enum" varchar(32) NOT NULL,
    "chrgitm_lv" int4,
    "medication_category_code" varchar(64),
    "device_category_code" varchar(64),
    "dose_form_code" varchar(64),
    "created_at" timestamptz(6) NOT NULL,
    "updated_at" timestamptz(6),
    "created_by" int8,
    "updated_by" int8,
    "is_deleted" bool NOT NULL DEFAULT false,
    "deleted_at" timestamptz(6),
    "deleted_by" int8,
    CONSTRAINT "doc_inventory_item_snapshot_pkey" PRIMARY KEY ("id")
);

COMMENT ON TABLE doc_inventory_item_snapshot IS '库存明细快照静表（DM-DOC-001）';
COMMENT ON COLUMN doc_inventory_item_snapshot.id IS '主键';
COMMENT ON COLUMN doc_inventory_item_snapshot.tenant_id IS '租户标识';
COMMENT ON COLUMN doc_inventory_item_snapshot.bus_no IS '批次单号（ABU+yyyyMMdd+2位序号）';
COMMENT ON COLUMN doc_inventory_item_snapshot.snapshot_time IS '快照时刻';
COMMENT ON COLUMN doc_inventory_item_snapshot.inventory_item_id IS '源库存项标识（写入时刻 wkf_inventory_item.id）';
COMMENT ON COLUMN doc_inventory_item_snapshot.item_table IS '物品定义表名';
COMMENT ON COLUMN doc_inventory_item_snapshot.item_id IS '物品定义标识';
COMMENT ON COLUMN doc_inventory_item_snapshot.charge_item_definition_id IS '收费项定义标识';
COMMENT ON COLUMN doc_inventory_item_snapshot.warehouse_id IS '库房标识（不含货位）';
COMMENT ON COLUMN doc_inventory_item_snapshot.supplier_id IS '供应商标识';
COMMENT ON COLUMN doc_inventory_item_snapshot.product_bus_no IS '项目编码';
COMMENT ON COLUMN doc_inventory_item_snapshot.name IS '项目名称';
COMMENT ON COLUMN doc_inventory_item_snapshot.merchandise_name IS '商品名称';
COMMENT ON COLUMN doc_inventory_item_snapshot.total_volume IS '规格';
COMMENT ON COLUMN doc_inventory_item_snapshot.manufacturer IS '厂家/产地';
COMMENT ON COLUMN doc_inventory_item_snapshot.approval_number IS '批准文号';
COMMENT ON COLUMN doc_inventory_item_snapshot.yb_no IS '医保码';
COMMENT ON COLUMN doc_inventory_item_snapshot.trace_no IS '追溯码';
COMMENT ON COLUMN doc_inventory_item_snapshot.py_str IS '拼音码（备份关键字/排序）';
COMMENT ON COLUMN doc_inventory_item_snapshot.wb_str IS '五笔码（备份关键字）';
COMMENT ON COLUMN doc_inventory_item_snapshot.lot_number IS '生产批号';
COMMENT ON COLUMN doc_inventory_item_snapshot.quantity IS '账面数量（拆零最小单位）';
COMMENT ON COLUMN doc_inventory_item_snapshot.package_unit_ratio IS '拆零↔包装倍数（1包装=ratio拆零）';
COMMENT ON COLUMN doc_inventory_item_snapshot.package_unit_code IS '包装单位编码';
COMMENT ON COLUMN doc_inventory_item_snapshot.lot_number_cost_amount IS '批号进价（包装整包）';
COMMENT ON COLUMN doc_inventory_item_snapshot.lot_number_price_amount IS '批号售价（包装整包）';
COMMENT ON COLUMN doc_inventory_item_snapshot.line_cost_total IS '进价行总额（写入时刻冻结）';
COMMENT ON COLUMN doc_inventory_item_snapshot.line_price_total IS '售价行总额（写入时刻冻结）';
COMMENT ON COLUMN doc_inventory_item_snapshot.production_date IS '生产日期';
COMMENT ON COLUMN doc_inventory_item_snapshot.expiration_date IS '效期';
COMMENT ON COLUMN doc_inventory_item_snapshot.remaining_days IS '剩余过期天数（写入时刻冻结）';
COMMENT ON COLUMN doc_inventory_item_snapshot.inventory_status_enum IS '供应状态（PublicationStatus code）';
COMMENT ON COLUMN doc_inventory_item_snapshot.chrgitm_lv IS '医保等级码';
COMMENT ON COLUMN doc_inventory_item_snapshot.medication_category_code IS '药品分类编码';
COMMENT ON COLUMN doc_inventory_item_snapshot.device_category_code IS '耗材分类编码';
COMMENT ON COLUMN doc_inventory_item_snapshot.dose_form_code IS '剂型编码';
COMMENT ON COLUMN doc_inventory_item_snapshot.created_at IS '创建时间';
COMMENT ON COLUMN doc_inventory_item_snapshot.updated_at IS '最后更新时间';
COMMENT ON COLUMN doc_inventory_item_snapshot.created_by IS '创建人';
COMMENT ON COLUMN doc_inventory_item_snapshot.updated_by IS '最后更新人';
COMMENT ON COLUMN doc_inventory_item_snapshot.is_deleted IS '软删标记';
COMMENT ON COLUMN doc_inventory_item_snapshot.deleted_at IS '删除时间';
COMMENT ON COLUMN doc_inventory_item_snapshot.deleted_by IS '删除人';

CREATE UNIQUE INDEX IF NOT EXISTS "uk_doc_inv_snap_tenant_bus_item"
    ON "public"."doc_inventory_item_snapshot" ("tenant_id", "bus_no", "inventory_item_id")
    WHERE "is_deleted" = false;

CREATE INDEX IF NOT EXISTS "idx_doc_inv_snap_tenant_bus"
    ON "public"."doc_inventory_item_snapshot" ("tenant_id", "bus_no")
    WHERE "is_deleted" = false;

CREATE INDEX IF NOT EXISTS "idx_doc_inv_snap_tenant_bus_py"
    ON "public"."doc_inventory_item_snapshot" ("tenant_id", "bus_no", "py_str")
    WHERE "is_deleted" = false;

-- from V101__cus_trace_number.sql
-- cus_trace_number + cus_trace_number_record（建表）
-- DM-CUS-010 v2.0.4：主档 cus_trace_number + 子表 cus_trace_number_record（领域实体 TraceNumberRecord）

CREATE TABLE IF NOT EXISTS "public"."cus_trace_number" (
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
    "trace_no" varchar(255) NOT NULL,
    "item_table" varchar(255) NOT NULL,
    "item_id" int8 NOT NULL,
    "lot_number" varchar(255) NOT NULL,
    "trace_unit_code" varchar(64) NOT NULL,
    "trace_unit_ratio" numeric(18, 6),
    "remaining_split_quantity" numeric(18, 4) NOT NULL DEFAULT 0,
    "in_hospital_status_enum" varchar(32) NOT NULL,
    CONSTRAINT "cus_trace_number_pkey" PRIMARY KEY ("id")
);

COMMENT ON TABLE cus_trace_number IS '追溯码主档（DM-CUS-010）';
COMMENT ON COLUMN cus_trace_number.trace_no IS '单码；租户内业务唯一';
COMMENT ON COLUMN cus_trace_number.item_table IS '品项物理表名（本期仅 med_medication_definition）';
COMMENT ON COLUMN cus_trace_number.item_id IS '药品定义标识';
COMMENT ON COLUMN cus_trace_number.lot_number IS '批号';
COMMENT ON COLUMN cus_trace_number.trace_unit_code IS '追溯码所贴包装单位（建码快照；发退换算优先 ratio）';
COMMENT ON COLUMN cus_trace_number.trace_unit_ratio IS '建码时 1 个追溯码单位对应的拆零数量快照；发退优先本列';
COMMENT ON COLUMN cus_trace_number.remaining_split_quantity IS '拆零剩余量（最小单位）';
COMMENT ON COLUMN cus_trace_number.in_hospital_status_enum IS 'IN_HOSPITAL / NOT_IN_HOSPITAL';

CREATE UNIQUE INDEX IF NOT EXISTS uk_cus_trace_number_tenant_trace_no
    ON cus_trace_number (tenant_id, trace_no)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_cus_trace_number_item
    ON cus_trace_number (tenant_id, item_table, item_id, lot_number);

CREATE TABLE IF NOT EXISTS "public"."cus_trace_number_record" (
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
    "trace_number_id" int8 NOT NULL,
    "trace_no" varchar(255) NOT NULL,
    "record_category_enum" varchar(32) NOT NULL,
    "record_direction_enum" varchar(16) NOT NULL,
    "location_type_enum" varchar(64),
    "location_id" int8,
    "location_store_id" int8,
    "lot_number" varchar(255) NOT NULL,
    "quantity" numeric(18, 4) NOT NULL,
    "unit_code" varchar(64) NOT NULL,
    "split_quantity" numeric(18, 4),
    "operation_type" varchar(64) NOT NULL,
    "bus_no" varchar(255),
    CONSTRAINT "cus_trace_number_record_pkey" PRIMARY KEY ("id"),
    CONSTRAINT "fk_cus_tn_record_trace_number_id"
        FOREIGN KEY ("trace_number_id") REFERENCES "cus_trace_number" ("id")
);

COMMENT ON TABLE cus_trace_number_record IS '追溯码记录 TraceNumberRecord（只 INSERT，DM-CUS-010）';
COMMENT ON COLUMN cus_trace_number_record.record_category_enum IS 'HOSPITAL_IO / INTERNAL_TRANSFER';
COMMENT ON COLUMN cus_trace_number_record.record_direction_enum IS 'IN / OUT';
COMMENT ON COLUMN cus_trace_number_record.split_quantity IS '本条记录拆零量（写侧换算后落库，库位轧差直读）';

CREATE INDEX IF NOT EXISTS idx_cus_tn_record_trace_number_id
    ON cus_trace_number_record (trace_number_id);

CREATE INDEX IF NOT EXISTS idx_cus_tn_record_loc_trace
    ON cus_trace_number_record (location_id, trace_no)
    WHERE location_id IS NOT NULL AND is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_cus_tn_record_bus_no
    ON cus_trace_number_record (bus_no)
    WHERE bus_no IS NOT NULL AND is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_cus_tn_record_trace_no_created
    ON cus_trace_number_record (trace_no, created_at);
