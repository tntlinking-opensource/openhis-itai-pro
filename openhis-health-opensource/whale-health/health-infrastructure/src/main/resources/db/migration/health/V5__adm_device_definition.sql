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
    "dose_unit_code" varchar(255),
    "package_unit_code" varchar(255),
    "stock_unit_code" varchar(255),
    "approval_number" varchar(255),
    "manufacturer_id" int8,
    "manufacturer_text" varchar(255),
    "supply_id" int8,
    "description" varchar(255),
    "jurisdiction" varchar(255),
    "device_version" varchar(255),
    "substance_text" varchar(255),
    "allergen_flag" bool,
    "rx_flag" bool,
    "yb_flag" bool,
    "yb_no" varchar(255),
    "yb_org_no" varchar(255),
    "yb_match_flag" bool,
    "chrgitm_lv" int4,
    "status_enum" varchar(255) NOT NULL,
    "active_flag" bool NOT NULL,
    "yb_mapping_code" varchar(255),
    "dose_unit_ratio" numeric(18,6),
    "purchase_unit_ratio" numeric(18,6),
    "stock_unit_ratio" numeric(18,6),
    "package_unit_ratio" numeric(18,6),
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
COMMENT ON COLUMN adm_device_definition.dose_unit_code IS '剂量单位（5类单位之一）';
COMMENT ON COLUMN adm_device_definition.package_unit_code IS '销售单位（5类单位之一）';
COMMENT ON COLUMN adm_device_definition.stock_unit_code IS '库存单位（5类单位之一）';
COMMENT ON COLUMN adm_device_definition.approval_number IS '批准文号';
COMMENT ON COLUMN adm_device_definition.manufacturer_id IS '生产厂家标识';
COMMENT ON COLUMN adm_device_definition.manufacturer_text IS '生产厂家名称';
COMMENT ON COLUMN adm_device_definition.supply_id IS '供应商标识';
COMMENT ON COLUMN adm_device_definition.description IS '器材说明';
COMMENT ON COLUMN adm_device_definition.jurisdiction IS '适用范围描述';
COMMENT ON COLUMN adm_device_definition.device_version IS '器材版本';
COMMENT ON COLUMN adm_device_definition.substance_text IS '主要成分描述';
COMMENT ON COLUMN adm_device_definition.allergen_flag IS '过敏标记';
COMMENT ON COLUMN adm_device_definition.rx_flag IS '处方标志';
COMMENT ON COLUMN adm_device_definition.yb_flag IS '医保标记';
COMMENT ON COLUMN adm_device_definition.yb_no IS '医保编码';
COMMENT ON COLUMN adm_device_definition.yb_org_no IS '医药机构目录编码';
COMMENT ON COLUMN adm_device_definition.yb_match_flag IS '医保对码标记';
COMMENT ON COLUMN adm_device_definition.chrgitm_lv IS '医保等级';
COMMENT ON COLUMN adm_device_definition.status_enum IS '发布状态（存储为整数码）';
COMMENT ON COLUMN adm_device_definition.active_flag IS '是否启用标记';
COMMENT ON COLUMN adm_device_definition.yb_mapping_code IS '医保对码标识';
COMMENT ON COLUMN adm_device_definition.dose_unit_ratio IS '剂量单位→拆零单位换算倍数（拆零单位为1）';
COMMENT ON COLUMN adm_device_definition.purchase_unit_ratio IS '采购单位→拆零单位换算倍数（拆零单位为1）';
COMMENT ON COLUMN adm_device_definition.stock_unit_ratio IS '库存单位→拆零单位换算倍数（拆零单位为1）';
COMMENT ON COLUMN adm_device_definition.package_unit_ratio IS '销售单位→拆零单位换算倍数（拆零单位为1）';

-- seed: adm_device_definition
INSERT INTO "adm_device_definition" ("id", "tenant_id", "created_at", "updated_at", "created_by", "updated_by", "is_deleted", "deleted_at", "deleted_by", "version", "name", "bus_no", "py_str", "wb_str", "category_code", "type_code", "purchase_unit_code", "total_volume", "item_min_quantity", "item_max_quantity", "split_unit_code", "merchandise_name", "merchandise_py_str", "merchandise_wb_str", "def_encounter_unit_code", "def_inhospital_unit_code", "org_id", "location_id", "model_number", "hvcm_flag", "dose_unit_code", "package_unit_code", "stock_unit_code", "approval_number", "manufacturer_id", "manufacturer_text", "supply_id", "description", "jurisdiction", "device_version", "substance_text", "allergen_flag", "rx_flag", "yb_flag", "yb_no", "yb_org_no", "yb_match_flag", "chrgitm_lv", "status_enum", "active_flag", "yb_mapping_code", "dose_unit_ratio", "purchase_unit_ratio", "stock_unit_ratio", "package_unit_ratio")
VALUES
('322999285465485312', '1', '2026-06-10 07:23:50.691+00', '2026-06-10 07:23:50.691+00', '322932010666430464', '322932010666430464', false, NULL, NULL, 0, '一次性使用无菌注射器', 'DD0007', 'YCXSYWJZSQ', 'GUNWEFAITK', '11', NULL, '7', '5ml', NULL, NULL, '9', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, false, '9', '9', '9', NULL, NULL, '河北康卫士医疗科技有限公司', NULL, NULL, NULL, NULL, NULL, false, NULL, false, NULL, NULL, false, NULL, 'ACTIVE', true, NULL, '1.000000', '100.000000', '1.000000', '1.000000'),
('323000109906268160', '1', '2026-06-10 07:27:07.254+00', '2026-06-10 07:27:07.254+00', '322932010666430464', '322932010666430464', false, NULL, NULL, 0, '一次性使用精密输液器（带针）', 'DD0008', 'YCXSYJMSYQ（DZ）', 'GUNWEOPLIK（GQ）', '11', NULL, '7', 'ZJ-B-BV23(400支/件)', NULL, NULL, '9', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, false, '9', '9', '9', NULL, NULL, '江西三鑫医疗科技股份有限公司', NULL, NULL, NULL, NULL, NULL, false, NULL, false, NULL, NULL, false, NULL, 'ACTIVE', true, NULL, '1.000000', '200.000000', '1.000000', '1.000000')
ON CONFLICT ("id") DO NOTHING;
