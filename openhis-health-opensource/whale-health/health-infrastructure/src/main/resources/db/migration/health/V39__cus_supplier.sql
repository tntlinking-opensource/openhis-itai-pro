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
COMMENT ON COLUMN cus_supplier.bus_no IS 'bus no';
COMMENT ON COLUMN cus_supplier.name IS 'name';
COMMENT ON COLUMN cus_supplier.type_enum IS 'type enum';
COMMENT ON COLUMN cus_supplier.credit_code IS 'credit code';
COMMENT ON COLUMN cus_supplier.legal_representative IS 'legal representative';
COMMENT ON COLUMN cus_supplier.address IS 'address';
COMMENT ON COLUMN cus_supplier.py_str IS 'py str';
COMMENT ON COLUMN cus_supplier.wb_str IS 'wb str';
COMMENT ON COLUMN cus_supplier.phone IS 'phone';
COMMENT ON COLUMN cus_supplier.email IS 'email';
COMMENT ON COLUMN cus_supplier.bank_name IS 'bank name';
COMMENT ON COLUMN cus_supplier.bank_account IS 'bank account';
COMMENT ON COLUMN cus_supplier.org_id IS 'org id';
COMMENT ON COLUMN cus_supplier.display_order IS 'display order';
COMMENT ON COLUMN cus_supplier.status_enum IS 'status enum';
COMMENT ON COLUMN cus_supplier.note IS 'note';

-- seed: cus_supplier
INSERT INTO "cus_supplier" ("id", "tenant_id", "created_at", "updated_at", "created_by", "updated_by", "is_deleted", "deleted_at", "deleted_by", "version", "bus_no", "name", "type_enum", "credit_code", "legal_representative", "address", "py_str", "wb_str", "phone", "email", "bank_name", "bank_account", "org_id", "display_order", "status_enum", "note")
VALUES
('4100000000000000002', '1', '2026-05-03 06:07:04.593+00', '2026-06-03 11:22:58.190+00', '1', '1', false, NULL, NULL, 0, 'SUPP002', '华东医疗器械制造有限公司', '1', '91310115MA1K000002', '李明', '上海市浦东新区张江路88号', 'HDYLYQZZ', 'HDYLYQZZ', '021-66660002', 'contact@hd-device.com', '中国建设银行上海张江支行', '3103010000000000002', '1', 2, 'ACTIVE', '默认启用厂家（器械）'),
('4100000000000000003', '1', '2026-05-03 06:07:04.593+00', '2026-06-03 11:22:52.470+00', '1', '1', false, NULL, NULL, 0, 'SUPP003', '华南中药饮片供应中心', '2', '91440101MA5C000003', '王芳', '广州市天河区科韵路66号', 'HNZYYPGYZX', 'HNZYYPGYZX', '020-77770003', 'sales@hn-tcm.com', '中国农业银行广州天河支行', '4404010000000000003', '1', 3, 'ACTIVE', '草稿供应商，供流程测试'),
('4100000000000000004', '1', '2026-05-03 06:07:04.593+00', '2026-05-03 06:07:04.593+00', '1', '1', false, NULL, NULL, 0, 'SUPP004', '华中药品配送有限公司', '2', '91420100MA4K000004', '赵强', '武汉市东湖高新区光谷大道99号', 'HZYPS', 'HZYPS', '027-88880004', 'biz@hz-pharm.com', '中国银行武汉光谷支行', '4205010000000000004', '1', 4, 'ACTIVE', '默认启用供应商（中成药）'),
('4100000000000000005', '1', '2026-05-03 06:07:04.593+00', '2026-05-03 06:07:04.593+00', '1', '1', false, NULL, NULL, 0, 'SUPP005', '西南医用耗材制造有限公司', '1', '91500112MA5U000005', '陈杰', '重庆市渝北区两路工业园28号', 'XNYYHCZZ', 'XNYYHCZZ', '023-88880005', 'info@xn-consumable.com', '交通银行重庆渝北支行', '5006010000000000005', '1', 5, 'ACTIVE', '默认启用厂家（医用耗材）'),
('4100000000000000006', '1', '2026-05-03 06:07:04.593+00', '2026-05-03 06:07:04.593+00', '1', '1', false, NULL, NULL, 0, 'SUPP006', '华北诊断试剂制造厂', '1', '91130100MA07A00006', '刘洋', '石家庄市高新区天山大街66号', 'HBZDSJZZC', 'HBZDSJZZC', '0311-88880006', 'service@hb-reagent.com', '招商银行石家庄高新支行', '1307010000000000006', '1', 6, 'ACTIVE', '默认启用厂家（体外诊断）'),
('4100000000000000007', '1', '2026-05-03 06:07:04.593+00', '2026-05-03 06:07:04.593+00', '1', '1', false, NULL, NULL, 0, 'SUPP007', '东部医疗物流供应链有限公司', '2', '91330200MA2A000007', '周敏', '宁波市鄞州区创新路18号', 'DBYLWL', 'DBYLWL', '0574-88880007', 'ops@db-logistics.com', '浦发银行宁波鄞州支行', '3308010000000000007', '1', 7, 'ACTIVE', '默认启用供应商（物流配送）'),
('4100000000000000008', '1', '2026-05-03 06:07:04.593+00', '2026-06-03 11:22:53.748+00', '1', '1', false, NULL, NULL, 0, 'SUPP008', '华南医疗设备制造有限公司', '1', '91440300MA5F000008', '孙磊', '深圳市南山区科技园中区5栋', 'HNYLSBZZ', 'HNYLSBZZ', '0755-88880008', 'contact@hn-device.com', '平安银行深圳科技园支行', '4409010000000000008', '1', 8, 'ACTIVE', '草稿厂家，供审批流测试'),
('4100000000000000009', '1', '2026-05-03 06:07:04.593+00', '2026-05-03 06:07:04.593+00', '1', '1', false, NULL, NULL, 0, 'SUPP009', '北方疫苗冷链供应有限公司', '2', '91120116MA06B00009', '高峰', '天津市滨海新区海洋路88号', 'BFYMLL', 'BFYMLL', '022-88880009', 'vaccine@bf-coldchain.com', '中国邮政储蓄银行天津滨海支行', '1210010000000000009', '1', 9, 'ACTIVE', '默认启用供应商（冷链）'),
('4100000000000000010', '1', '2026-05-03 06:07:04.593+00', '2026-05-03 06:07:04.593+00', '1', '1', false, NULL, NULL, 0, 'SUPP010', '华东一次性耗材制造有限公司', '1', '91320583MA1N000010', '马超', '苏州市昆山市开发区同丰路188号', 'HDYCXHCZZ', 'HDYCXHCZZ', '0512-88880010', 'sales@hd-disposable.com', '江苏银行昆山开发区支行', '3211010000000000010', '1', 10, 'ACTIVE', '默认启用厂家（一次性耗材）'),
('4100000000000000011', '1', '2026-05-03 06:07:04.593+00', '2026-06-03 11:22:54.837+00', '1', '1', false, NULL, NULL, 0, 'SUPP011', '中原中药材供应有限公司', '2', '91410100MA3X000011', '何静', '郑州市郑东新区商务内环路36号', 'ZYZYC', 'ZYZYC', '0371-88880011', 'service@zy-herb.com', '中信银行郑州郑东新区支行', '4112010000000000011', '1', 11, 'ACTIVE', '停用供应商样例'),
('4100000000000000012', '1', '2026-05-03 06:07:04.593+00', '2026-05-03 06:07:04.593+00', '1', '1', false, NULL, NULL, 0, 'SUPP012', '华西影像设备制造有限公司', '1', '91510100MA62A00012', '唐军', '成都市高新区天府大道北段999号', 'HXYXSBZZ', 'HXYXSBZZ', '028-88880012', 'market@hx-imaging.com', '中国民生银行成都高新支行', '5113010000000000012', '1', 12, 'ACTIVE', '默认启用厂家（影像设备）')
ON CONFLICT ("id") DO NOTHING;
