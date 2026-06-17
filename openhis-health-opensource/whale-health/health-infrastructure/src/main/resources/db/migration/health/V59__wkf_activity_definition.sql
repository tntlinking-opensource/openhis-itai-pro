-- wkf_activity_definition（建表 + 种子数据）
-- Flyway V59

CREATE TABLE IF NOT EXISTS "public"."wkf_activity_definition" (
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
    "category_code" varchar(255) NOT NULL,
    "bus_no" varchar(255) NOT NULL,
    "name" varchar(255) NOT NULL,
    "py_str" varchar(255),
    "wb_str" varchar(255),
    "package_unit_code" varchar(255),
    "type_enum" varchar(255) NOT NULL,
    "status_enum" varchar(255) NOT NULL,
    "description_text" varchar(255),
    "body_site_code" varchar(255),
    "specimen_code" varchar(255),
    "org_id" int8,
    "location_id" int8,
    "rule_id" int4,
    "pricing_flag" bool,
    "yb_flag" bool,
    "yb_no" varchar(255),
    "yb_match_flag" bool,
    "chrgitm_lv" int4,
    "children_json" varchar(255),
    "children_flag" bool,
    "activity_version" varchar(255),
    "merchandise_name" varchar(255),
    "merchandise_py_str" varchar(255),
    "merchandise_wb_str" varchar(255),
    "def_encounter_unit_code" varchar(255),
    "def_inhospital_unit_code" varchar(255),
    CONSTRAINT "wkf_activity_definition_pkey" PRIMARY KEY ("id")
);

COMMENT ON TABLE wkf_activity_definition IS '工作流 activity definition';

COMMENT ON COLUMN wkf_activity_definition.id IS '主键';
COMMENT ON COLUMN wkf_activity_definition.tenant_id IS '租户标识';
COMMENT ON COLUMN wkf_activity_definition.created_at IS '创建时间';
COMMENT ON COLUMN wkf_activity_definition.updated_at IS '最后更新时间';
COMMENT ON COLUMN wkf_activity_definition.created_by IS '创建人';
COMMENT ON COLUMN wkf_activity_definition.updated_by IS '最后更新人';
COMMENT ON COLUMN wkf_activity_definition.is_deleted IS '软删标记';
COMMENT ON COLUMN wkf_activity_definition.deleted_at IS '删除时间';
COMMENT ON COLUMN wkf_activity_definition.deleted_by IS '删除人';
COMMENT ON COLUMN wkf_activity_definition.version IS '乐观锁版本号';
COMMENT ON COLUMN wkf_activity_definition.category_code IS '诊疗项目定义聚合根';
COMMENT ON COLUMN wkf_activity_definition.bus_no IS '业务编码（全局唯一）';
COMMENT ON COLUMN wkf_activity_definition.name IS '项目名称';
COMMENT ON COLUMN wkf_activity_definition.py_str IS '拼音检索码';
COMMENT ON COLUMN wkf_activity_definition.wb_str IS '五笔检索码';
COMMENT ON COLUMN wkf_activity_definition.package_unit_code IS '销售/计价单位编码';
COMMENT ON COLUMN wkf_activity_definition.type_enum IS '诊疗类型枚举编码';
COMMENT ON COLUMN wkf_activity_definition.status_enum IS '发布状态枚举编码';
COMMENT ON COLUMN wkf_activity_definition.description_text IS '说明文本';
COMMENT ON COLUMN wkf_activity_definition.body_site_code IS '身体部位编码';
COMMENT ON COLUMN wkf_activity_definition.specimen_code IS '标本编码';
COMMENT ON COLUMN wkf_activity_definition.org_id IS '所属机构标识';
COMMENT ON COLUMN wkf_activity_definition.location_id IS '位置标识';
COMMENT ON COLUMN wkf_activity_definition.rule_id IS '计费规则标识';
COMMENT ON COLUMN wkf_activity_definition.pricing_flag IS '划价标记';
COMMENT ON COLUMN wkf_activity_definition.yb_flag IS '医保标记';
COMMENT ON COLUMN wkf_activity_definition.yb_no IS '医保编码';
COMMENT ON COLUMN wkf_activity_definition.yb_match_flag IS '医保对码标记';
COMMENT ON COLUMN wkf_activity_definition.chrgitm_lv IS '医保收费等级';
COMMENT ON COLUMN wkf_activity_definition.children_json IS '子项 JSON 快照';
COMMENT ON COLUMN wkf_activity_definition.children_flag IS '子项标记（需与子项子表事实一致）';
COMMENT ON COLUMN wkf_activity_definition.activity_version IS '诊疗项目业务版本（非乐观锁 version）';
COMMENT ON COLUMN wkf_activity_definition.merchandise_name IS '商品名称（可与项目名称分列维护）';
COMMENT ON COLUMN wkf_activity_definition.merchandise_py_str IS '商品名拼音检索码';
COMMENT ON COLUMN wkf_activity_definition.merchandise_wb_str IS '商品名五笔检索码';
COMMENT ON COLUMN wkf_activity_definition.def_encounter_unit_code IS '默认门诊/就诊单位编码';
COMMENT ON COLUMN wkf_activity_definition.def_inhospital_unit_code IS '默认住院单位编码';

-- seed: wkf_activity_definition
INSERT INTO "wkf_activity_definition" ("id", "tenant_id", "created_at", "updated_at", "created_by", "updated_by", "is_deleted", "deleted_at", "deleted_by", "version", "category_code", "bus_no", "name", "py_str", "wb_str", "package_unit_code", "type_enum", "status_enum", "description_text", "body_site_code", "specimen_code", "org_id", "location_id", "rule_id", "pricing_flag", "yb_flag", "yb_no", "yb_match_flag", "chrgitm_lv", "children_json", "children_flag", "activity_version", "merchandise_name", "merchandise_py_str", "merchandise_wb_str", "def_encounter_unit_code", "def_inhospital_unit_code")
VALUES
('323001764504670208', '1', '2026-06-10 07:33:41.740+00', '2026-06-10 07:33:41.752+00', '1', '1', false, NULL, NULL, 0, '21', 'AD00000011', '静脉注射', 'JMZS', 'GEIT', '6', '5', 'ACTIVE', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, false, NULL, NULL, NULL, NULL, NULL, NULL),
('323002894106234880', '1', '2026-06-10 07:38:11.058+00', '2026-06-10 07:38:11.065+00', '1', '1', false, NULL, NULL, 0, '22', 'AD00000012', '皮试检查', 'PSJC', 'HYSS', '6', '1', 'ACTIVE', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, false, NULL, NULL, NULL, NULL, NULL, NULL),
('323003446592540672', '1', '2026-06-10 07:40:22.781+00', '2026-06-10 07:40:22.788+00', '1', '1', false, NULL, NULL, 0, '22', 'AD00000013', '肝功13项', 'GG13X', 'EA13A', '6', '1', 'ACTIVE', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, false, NULL, NULL, NULL, NULL, NULL, NULL),
('323004165299113984', '1', '2026-06-10 07:43:14.134+00', '2026-06-10 07:43:14.141+00', '1', '1', false, NULL, NULL, 0, '22', 'AD00000014', '血清总胆红素测定', 'XQZDHSCD', 'TIUEXGIP', '6', '1', 'ACTIVE', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, false, NULL, NULL, NULL, NULL, NULL, NULL)
ON CONFLICT ("id") DO NOTHING;
