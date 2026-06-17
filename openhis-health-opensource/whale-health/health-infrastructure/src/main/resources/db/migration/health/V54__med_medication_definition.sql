-- med_medication_definition（建表 + 种子数据）
-- Flyway V54

CREATE TABLE IF NOT EXISTS "public"."med_medication_definition" (
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
    "domain_enum" varchar(255),
    "med_version" varchar(255),
    "name_en" varchar(255),
    "py_str" varchar(255),
    "wb_str" varchar(255),
    "category_code" varchar(255),
    "merchandise_name" varchar(255),
    "merchandise_py_str" varchar(255),
    "merchandise_wb_str" varchar(255),
    "purchase_unit_code" varchar(255),
    "split_unit_code" varchar(255),
    "comprised_text" varchar(255),
    "ingredient" varchar(255),
    "dose_from" int4,
    "approval_number" varchar(255),
    "yb_match_flag" bool,
    "yb_no" varchar(255),
    "pharmacology_category_code" varchar(255),
    "skin_test_flag" bool,
    "inject_flag" bool,
    "manufacturer_id" int8,
    "manufacturer_text" varchar(255),
    "supply_id" int8,
    "restricted_flag" bool,
    "restricted_scope" varchar(255),
    "active_flag" bool,
    "children_flag" bool,
    "characteristic" int4,
    "item_min_quantity" numeric(18,2),
    "item_max_quantity" numeric(18,2),
    "def_encounter_unit_code" varchar(255),
    "def_inhospital_unit_code" varchar(255),
    "national_drug_code" varchar(255),
    "part_attribute_enum" varchar(255),
    "antibiotic_code" varchar(255),
    "restricted_enum" varchar(255),
    "self_flag" bool,
    "antibiotic_flag" bool,
    "basic_flag" bool,
    "tho_part_attribute_enum" varchar(255),
    "chrgitm_lv" int4,
    "rx_flag" bool,
    "dosage_instruction" varchar(255),
    "drug69_code" varchar(255),
    "total_volume" varchar(255),
    "dose_form_code" varchar(255),
    "dose_unit_code" varchar(255),
    "package_unit_code" varchar(255),
    "stock_unit_code" varchar(255),
    "method_code" varchar(255),
    "rate_code" varchar(255),
    "dose" numeric(18,2),
    "max_unit" numeric(18,2),
    "usage_limit" numeric(18,2),
    "location_id" int8,
    "status_enum" varchar(255) NOT NULL,
    "org_id" int8 NOT NULL,
    "ingredient_item" varchar(255),
    "medicationdefinition" varchar(255),
    "ddd_code" varchar(255),
    "ddd_unit_code" varchar(255),
    "min_rate_code" varchar(255),
    "max_rate_code" varchar(255),
    "dose_unit_ratio" numeric(18,6),
    "purchase_unit_ratio" numeric(18,6),
    "stock_unit_ratio" numeric(18,6),
    "package_unit_ratio" numeric(18,6),
    CONSTRAINT "med_medication_definition_pkey" PRIMARY KEY ("id")
);

COMMENT ON TABLE med_medication_definition IS '药品 medication definition';

COMMENT ON COLUMN med_medication_definition.id IS '主键';
COMMENT ON COLUMN med_medication_definition.tenant_id IS '租户标识';
COMMENT ON COLUMN med_medication_definition.created_at IS '创建时间';
COMMENT ON COLUMN med_medication_definition.updated_at IS '最后更新时间';
COMMENT ON COLUMN med_medication_definition.created_by IS '创建人';
COMMENT ON COLUMN med_medication_definition.updated_by IS '最后更新人';
COMMENT ON COLUMN med_medication_definition.is_deleted IS '软删标记';
COMMENT ON COLUMN med_medication_definition.deleted_at IS '删除时间';
COMMENT ON COLUMN med_medication_definition.deleted_by IS '删除人';
COMMENT ON COLUMN med_medication_definition.version IS '乐观锁版本号';
COMMENT ON COLUMN med_medication_definition.bus_no IS '药品定义聚合根';
COMMENT ON COLUMN med_medication_definition.name IS '药品名称';
COMMENT ON COLUMN med_medication_definition.domain_enum IS '适用范围编码';
COMMENT ON COLUMN med_medication_definition.med_version IS '药品版本';
COMMENT ON COLUMN med_medication_definition.name_en IS '英文药名';
COMMENT ON COLUMN med_medication_definition.py_str IS '药品名称拼音码';
COMMENT ON COLUMN med_medication_definition.wb_str IS '药品名称五笔码';
COMMENT ON COLUMN med_medication_definition.category_code IS '药品分类编码';
COMMENT ON COLUMN med_medication_definition.merchandise_name IS '商品名称';
COMMENT ON COLUMN med_medication_definition.merchandise_py_str IS '商品名拼音码';
COMMENT ON COLUMN med_medication_definition.merchandise_wb_str IS '商品名五笔码';
COMMENT ON COLUMN med_medication_definition.purchase_unit_code IS '采购单位（5类单位之一）';
COMMENT ON COLUMN med_medication_definition.split_unit_code IS '拆零单位（5类单位之一）';
COMMENT ON COLUMN med_medication_definition.comprised_text IS '所含耗材描述';
COMMENT ON COLUMN med_medication_definition.ingredient IS '成分';
COMMENT ON COLUMN med_medication_definition.dose_from IS '剂量形式编码';
COMMENT ON COLUMN med_medication_definition.approval_number IS '批准文号';
COMMENT ON COLUMN med_medication_definition.yb_match_flag IS '医保是否已对码';
COMMENT ON COLUMN med_medication_definition.yb_no IS '医保编码';
COMMENT ON COLUMN med_medication_definition.pharmacology_category_code IS '药理作用分类编码';
COMMENT ON COLUMN med_medication_definition.skin_test_flag IS '是否皮试药';
COMMENT ON COLUMN med_medication_definition.inject_flag IS '是否注射药物';
COMMENT ON COLUMN med_medication_definition.manufacturer_id IS '生产厂家标识';
COMMENT ON COLUMN med_medication_definition.manufacturer_text IS '生产厂商文本';
COMMENT ON COLUMN med_medication_definition.supply_id IS '供应商标识';
COMMENT ON COLUMN med_medication_definition.restricted_flag IS '是否限制使用';
COMMENT ON COLUMN med_medication_definition.restricted_scope IS '限制使用范围';
COMMENT ON COLUMN med_medication_definition.active_flag IS '启用标记（true=启用，false=停用）';
COMMENT ON COLUMN med_medication_definition.children_flag IS '儿童用药标志';
COMMENT ON COLUMN med_medication_definition.characteristic IS '产品特性编码';
COMMENT ON COLUMN med_medication_definition.item_min_quantity IS '最小库存警戒数量';
COMMENT ON COLUMN med_medication_definition.item_max_quantity IS '最大库存警戒数量';
COMMENT ON COLUMN med_medication_definition.def_encounter_unit_code IS '默认门诊单位编码';
COMMENT ON COLUMN med_medication_definition.def_inhospital_unit_code IS '默认住院单位编码';
COMMENT ON COLUMN med_medication_definition.national_drug_code IS '国家药品贯标编码';
COMMENT ON COLUMN med_medication_definition.part_attribute_enum IS '拆分属性编码';
COMMENT ON COLUMN med_medication_definition.antibiotic_code IS '抗生素分类编码';
COMMENT ON COLUMN med_medication_definition.restricted_enum IS '权限限制枚举编码';
COMMENT ON COLUMN med_medication_definition.self_flag IS '是否自制';
COMMENT ON COLUMN med_medication_definition.antibiotic_flag IS '是否抗生素';
COMMENT ON COLUMN med_medication_definition.basic_flag IS '是否基药';
COMMENT ON COLUMN med_medication_definition.tho_part_attribute_enum IS '住院临时医嘱拆分属性编码';
COMMENT ON COLUMN med_medication_definition.chrgitm_lv IS '医保等级';
COMMENT ON COLUMN med_medication_definition.rx_flag IS '处方药标志';
COMMENT ON COLUMN med_medication_definition.dosage_instruction IS '用药说明';
COMMENT ON COLUMN med_medication_definition.drug69_code IS '药品 69 码';
COMMENT ON COLUMN med_medication_definition.total_volume IS '规格';
COMMENT ON COLUMN med_medication_definition.dose_form_code IS '剂型编码';
COMMENT ON COLUMN med_medication_definition.dose_unit_code IS '剂量单位（5类单位之一）';
COMMENT ON COLUMN med_medication_definition.package_unit_code IS '销售单位（5类单位之一）';
COMMENT ON COLUMN med_medication_definition.stock_unit_code IS '库存单位（5类单位之一）';
COMMENT ON COLUMN med_medication_definition.method_code IS '默认用法编码';
COMMENT ON COLUMN med_medication_definition.rate_code IS '默认频次编码';
COMMENT ON COLUMN med_medication_definition.dose IS '单次剂量';
COMMENT ON COLUMN med_medication_definition.max_unit IS '单次最大剂量';
COMMENT ON COLUMN med_medication_definition.usage_limit IS '用量限定';
COMMENT ON COLUMN med_medication_definition.location_id IS '采购入库位置（他域事实列）';
COMMENT ON COLUMN med_medication_definition.status_enum IS '生命周期状态编码（DRAFT/ACTIVE/INACTIVE/DISCARDED）';
COMMENT ON COLUMN med_medication_definition.org_id IS '机构标识';
COMMENT ON COLUMN med_medication_definition.ingredient_item IS '成分项';
COMMENT ON COLUMN med_medication_definition.medicationdefinition IS '药品说明';
COMMENT ON COLUMN med_medication_definition.ddd_code IS 'DDD（日限定剂量）编码';
COMMENT ON COLUMN med_medication_definition.ddd_unit_code IS 'DDD（日限定剂量）单位编码';
COMMENT ON COLUMN med_medication_definition.min_rate_code IS '最小频次编码';
COMMENT ON COLUMN med_medication_definition.max_rate_code IS '最大频次编码';
COMMENT ON COLUMN med_medication_definition.dose_unit_ratio IS '剂量单位→拆零单位换算倍数（拆零单位为1）';
COMMENT ON COLUMN med_medication_definition.purchase_unit_ratio IS '采购单位→拆零单位换算倍数（拆零单位为1）';
COMMENT ON COLUMN med_medication_definition.stock_unit_ratio IS '库存单位→拆零单位换算倍数（拆零单位为1）';
COMMENT ON COLUMN med_medication_definition.package_unit_ratio IS '销售单位→拆零单位换算倍数（拆零单位为1）';

-- seed: med_medication_definition
INSERT INTO "med_medication_definition" ("id", "tenant_id", "created_at", "updated_at", "created_by", "updated_by", "is_deleted", "deleted_at", "deleted_by", "version", "bus_no", "name", "domain_enum", "med_version", "name_en", "py_str", "wb_str", "category_code", "merchandise_name", "merchandise_py_str", "merchandise_wb_str", "purchase_unit_code", "split_unit_code", "comprised_text", "ingredient", "dose_from", "approval_number", "yb_match_flag", "yb_no", "pharmacology_category_code", "skin_test_flag", "inject_flag", "manufacturer_id", "manufacturer_text", "supply_id", "restricted_flag", "restricted_scope", "active_flag", "children_flag", "characteristic", "item_min_quantity", "item_max_quantity", "def_encounter_unit_code", "def_inhospital_unit_code", "national_drug_code", "part_attribute_enum", "antibiotic_code", "restricted_enum", "self_flag", "antibiotic_flag", "basic_flag", "tho_part_attribute_enum", "chrgitm_lv", "rx_flag", "dosage_instruction", "drug69_code", "total_volume", "dose_form_code", "dose_unit_code", "package_unit_code", "stock_unit_code", "method_code", "rate_code", "dose", "max_unit", "usage_limit", "location_id", "status_enum", "org_id", "ingredient_item", "medicationdefinition", "ddd_code", "ddd_unit_code", "min_rate_code", "max_rate_code", "dose_unit_ratio", "purchase_unit_ratio", "stock_unit_ratio", "package_unit_ratio")
VALUES
('322994171216400384', '1', '2026-06-10 07:03:31.359+00', '2026-06-10 07:03:31.359+00', '322932010666430464', '322932010666430464', false, NULL, NULL, 0, 'MD0021', '头孢克肟胶囊', NULL, '1', NULL, 'TBKWJN', 'UBDEEG', '2', '头孢克肟胶囊', 'TBKWJN', 'UBDEEG', '2', '5', NULL, NULL, NULL, NULL, NULL, NULL, '1', false, false, NULL, '华北制药河北华民药业有限责任公司', '4100000000000000004', false, NULL, true, false, NULL, NULL, NULL, '2', '2', NULL, '2', NULL, NULL, false, false, false, '2', NULL, false, NULL, NULL, '0.1g', '5', '10', '2', '2', NULL, NULL, NULL, NULL, NULL, NULL, 'ACTIVE', '322663105141149696', NULL, NULL, NULL, NULL, NULL, NULL, '0.100000', '20.000000', '20.000000', '20.000000'),
('322994478289784832', '1', '2026-06-10 07:04:44.571+00', '2026-06-10 07:04:44.571+00', '322932010666430464', '322932010666430464', false, NULL, NULL, 0, 'MD0022', '替米沙坦片', NULL, '1', NULL, 'TMSTP', 'FOIFT', '2', '嘉瑟宜', 'JSY', 'FGP', '2', '1', NULL, NULL, NULL, NULL, NULL, NULL, '1', false, false, NULL, '上海信谊天平药业有限公司', '4100000000000000004', false, NULL, true, false, NULL, NULL, NULL, '2', '2', NULL, '2', NULL, NULL, false, false, false, '2', NULL, false, NULL, NULL, '40mg', '1', '4', '2', '2', NULL, NULL, NULL, NULL, NULL, NULL, 'ACTIVE', '322663105141149696', NULL, NULL, NULL, NULL, NULL, NULL, '40.000000', '28.000000', '28.000000', '28.000000'),
('322994878225059840', '1', '2026-06-10 07:06:19.923+00', '2026-06-10 07:06:19.923+00', '322932010666430464', '322932010666430464', false, NULL, NULL, 0, 'MD0023', '瑞格列奈片', NULL, '1', NULL, 'RGLNP', 'GSGDT', '2', '孚来迪', 'FLD', 'EGM', '2', '1', NULL, NULL, NULL, NULL, NULL, NULL, '1', false, false, NULL, '江苏豪森药业集团有限公司', '4100000000000000004', false, NULL, true, false, NULL, NULL, NULL, '2', '2', NULL, '2', NULL, NULL, false, false, false, '2', NULL, false, NULL, NULL, '1mg', '1', '4', '2', '2', NULL, NULL, NULL, NULL, NULL, NULL, 'ACTIVE', '322663105141149696', NULL, NULL, NULL, NULL, NULL, NULL, '1.000000', '30.000000', '30.000000', '30.000000'),
('322995328642977792', '1', '2026-06-10 07:08:07.311+00', '2026-06-10 07:08:07.311+00', '322932010666430464', '322932010666430464', false, NULL, NULL, 0, 'MD0024', '阿托伐他汀钙片', NULL, '1', NULL, 'ATFTTGP', 'BRWWIQT', '2', '立普妥', 'LPT', 'UUE', '2', '1', NULL, NULL, NULL, NULL, NULL, NULL, '1', false, false, NULL, '晖致制药(大连)有限公司', '4100000000000000004', false, NULL, true, false, NULL, NULL, NULL, '2', '2', NULL, '2', NULL, NULL, false, false, false, '2', NULL, false, NULL, NULL, '20mg', '1', '4', '2', '2', NULL, NULL, NULL, NULL, NULL, NULL, 'ACTIVE', '322663105141149696', NULL, NULL, NULL, NULL, NULL, NULL, '20.000000', '7.000000', '7.000000', '7.000000'),
('322995606410760192', '1', '2026-06-10 07:09:13.536+00', '2026-06-10 07:09:13.536+00', '322932010666430464', '322932010666430464', false, NULL, NULL, 0, 'MD0025', '布洛芬混悬液', NULL, '1', NULL, 'BLFHXY', 'DIAIEI', '2', '美林', 'ML', 'US', '2', '8', NULL, NULL, NULL, NULL, NULL, NULL, '1', false, false, NULL, '上海强生制药有限公司', '4100000000000000004', false, NULL, true, false, NULL, NULL, NULL, '2', '2', NULL, '2', NULL, NULL, false, false, false, '2', NULL, false, NULL, NULL, '100ml:2g', '19', '3', '2', '2', NULL, NULL, NULL, NULL, NULL, NULL, 'ACTIVE', '322663105141149696', NULL, NULL, NULL, NULL, NULL, NULL, '100.000000', '1.000000', '1.000000', '1.000000'),
('322996752743403520', '1', '2026-06-10 07:13:46.843+00', '2026-06-10 07:20:34.998+00', '322932010666430464', '322932010666430464', false, NULL, NULL, 0, 'MD0026', '氯化钠注射液', NULL, '2', NULL, 'LHNZSY', 'RWQITI', '2', '可立袋', 'KLD', 'SUW', '7', '8', NULL, NULL, NULL, NULL, false, NULL, '6', false, true, NULL, '四川科伦药业股份有限公司', '4100000000000000004', false, NULL, true, false, NULL, NULL, NULL, '2', '2', NULL, '2', NULL, NULL, false, false, false, '2', NULL, false, NULL, NULL, '100ml:0.9g', '4', '3', '8', '8', NULL, NULL, NULL, NULL, NULL, NULL, 'ACTIVE', '322663105141149696', NULL, NULL, NULL, NULL, NULL, NULL, '100.000000', '100.000000', '1.000000', '1.000000'),
('322996998399594496', '1', '2026-06-10 07:14:45.412+00', '2026-06-10 07:20:31.772+00', '322932010666430464', '322932010666430464', false, NULL, NULL, 0, 'MD0027', '氯化钠注射液', NULL, '2', NULL, 'LHNZSY', 'RWQITI', '2', '可立袋', 'KLD', 'SUW', '7', '8', NULL, NULL, NULL, NULL, false, NULL, '6', false, true, NULL, '四川科伦药业股份有限公司', '4100000000000000004', false, NULL, true, false, NULL, NULL, NULL, '2', '2', NULL, '2', NULL, NULL, false, false, false, '2', NULL, false, NULL, NULL, '250ml:2.25g', '4', '3', '8', '8', NULL, NULL, NULL, NULL, NULL, NULL, 'ACTIVE', '322663105141149696', NULL, NULL, NULL, NULL, NULL, NULL, '250.000000', '100.000000', '1.000000', '1.000000'),
('322997883812974592', '1', '2026-06-10 07:18:16.511+00', '2026-06-10 07:20:27.739+00', '322932010666430464', '322932010666430464', false, NULL, NULL, 0, 'MD0028', '注射用头孢呋辛钠', NULL, '3', NULL, 'ZSYTBFXN', 'ITEUBKUQ', '2', '注射用头孢呋辛钠', 'ZSYTBFXN', 'ITEUBKUQ', '2', '8', NULL, NULL, NULL, NULL, false, NULL, '1', false, true, NULL, '山东润泽制药有限公司', '4100000000000000004', false, NULL, true, false, NULL, NULL, NULL, '2', '2', NULL, '2', NULL, NULL, false, false, false, '2', NULL, false, NULL, NULL, '0.75g', '4', '10', '8', '8', NULL, NULL, NULL, NULL, NULL, NULL, 'ACTIVE', '322663105141149696', NULL, NULL, NULL, NULL, NULL, NULL, '0.750000', '10.000000', '1.000000', '1.000000'),
('322998385762111488', '1', '2026-06-10 07:20:16.185+00', '2026-06-10 07:20:16.185+00', '322932010666430464', '322932010666430464', false, NULL, NULL, 0, 'MD0029', '盐酸氨溴索注射液', NULL, '1', NULL, 'YSAXSZSY', 'FSRIFITI', '2', '盐酸氨溴索注射液', 'YSAXSZSY', 'FSRIFITI', '2', '9', NULL, NULL, NULL, NULL, NULL, NULL, '1', false, true, NULL, '湖北科伦药业有限公司', '4100000000000000004', false, NULL, true, false, NULL, NULL, NULL, '2', '2', NULL, '2', NULL, NULL, false, false, false, '2', NULL, false, NULL, NULL, '2ml:15mg', '4', '3', '9', '9', NULL, NULL, NULL, NULL, NULL, NULL, 'ACTIVE', '322663105141149696', NULL, NULL, NULL, NULL, NULL, NULL, '2.000000', '10.000000', '1.000000', '1.000000'),
('323999010000000001', '1', '2026-06-17 08:00:00.000+00', '2026-06-17 08:00:00.000+00', '322932010666430464', '322932010666430464', false, NULL, NULL, 0, 'MD0030', '黄芪', NULL, '1', NULL, 'HQ', 'AY', '4', '黄芪', 'HQ', 'AY', '11', '10', NULL, NULL, NULL, NULL, NULL, NULL, NULL, false, false, NULL, '安国市中药材市场', '4100000000000000003', false, NULL, true, false, NULL, NULL, NULL, '10', '10', NULL, '2', NULL, NULL, false, false, false, '2', NULL, false, NULL, NULL, '饮片', '11', '10', '10', '10', NULL, NULL, NULL, NULL, NULL, NULL, 'ACTIVE', '322663105141149696', NULL, NULL, NULL, NULL, NULL, NULL, '1.000000', '1000.000000', '1.000000', '1.000000')
ON CONFLICT ("id") DO NOTHING;
