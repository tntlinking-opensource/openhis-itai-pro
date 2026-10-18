-- =============================================================================
-- 老体检套餐导入（幂等 · 同项目口径）
-- 套餐 89；原价=je（不用 yj）；子项按 xm.code ↔ PEIS 项目 bus_no
-- 同 code 多套餐：bus_no = code_性别（如 20260417_MALE）
-- ID 段：套餐 AD 911230000000000000 / 价 911231000000000000 / 子项行 911232000000000000
-- =============================================================================
BEGIN;

CREATE TABLE IF NOT EXISTS tmp_peis_legacy_package (
  legacy_id varchar(64) PRIMARY KEY,
  intended_activity_id int8 NOT NULL,
  bus_no varchar(64) NOT NULL,
  display varchar(255) NOT NULL,
  py_str varchar(128),
  permitted_gender_enum varchar(32) NOT NULL,
  sort_order int4,
  sale_price numeric(18,4),
  activity_id int8
);
TRUNCATE tmp_peis_legacy_package;

CREATE TABLE IF NOT EXISTS tmp_peis_legacy_package_item (
  package_legacy_id varchar(64) NOT NULL,
  sort_num int4 NOT NULL,
  child_bus_no varchar(64) NOT NULL,
  child_name varchar(255),
  PRIMARY KEY (package_legacy_id, sort_num)
);
TRUNCATE tmp_peis_legacy_package_item;

INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('09d7abe10c9a4a3caef9c93f6cb464dd', 911230000000000001, '25101803', '2025教师体检（女2）', '2025jstj（n2）', 'FEMALE', 1, 0.0);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('09d7abe10c9a4a3caef9c93f6cb464dd', 1, 'ZL000062', '血常规（迪瑞BF-6900）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('09d7abe10c9a4a3caef9c93f6cb464dd', 2, 'ZL000066', '肝功十五项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('09d7abe10c9a4a3caef9c93f6cb464dd', 3, 'ZL000074', '尿常规（迪瑞FUS-1000）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('09d7abe10c9a4a3caef9c93f6cb464dd', 4, 'ZL000069', '血糖');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('09d7abe10c9a4a3caef9c93f6cb464dd', 5, '12030', '肺部CT、颅脑CT（不出片）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('09d7abe10c9a4a3caef9c93f6cb464dd', 6, '66011', '妇科彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('09d7abe10c9a4a3caef9c93f6cb464dd', 7, '71447', 'C14呼气试验');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('09d7abe10c9a4a3caef9c93f6cb464dd', 8, '72016', '糖类抗原CA15-3');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('09d7abe10c9a4a3caef9c93f6cb464dd', 9, '72555', '耳穴压丸');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('09d7abe10c9a4a3caef9c93f6cb464dd', 10, 'ZL000071', '血脂六项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('09d7abe10c9a4a3caef9c93f6cb464dd', 11, 'ZL000068', '肾功三项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('09d7abe10c9a4a3caef9c93f6cb464dd', 12, '01002', '一般检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('09d7abe10c9a4a3caef9c93f6cb464dd', 13, '05002', '皮肤科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('09d7abe10c9a4a3caef9c93f6cb464dd', 14, '06002', '口腔科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('09d7abe10c9a4a3caef9c93f6cb464dd', 15, '07001', '中医内科问诊');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('09d7abe10c9a4a3caef9c93f6cb464dd', 16, '10001', '腹部彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('09d7abe10c9a4a3caef9c93f6cb464dd', 17, '10003', '甲状腺彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('09d7abe10c9a4a3caef9c93f6cb464dd', 18, '10008', '乳腺彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('09d7abe10c9a4a3caef9c93f6cb464dd', 19, '11001', '心电图');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('09d7abe10c9a4a3caef9c93f6cb464dd', 20, '11005', '骨密度检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('09d7abe10c9a4a3caef9c93f6cb464dd', 21, '12019', '颅脑CT（不出片）');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('1848f1201222471e88a0d90f10bf8576', 911230000000000002, '2026072301', '团体体检', 'tttj', 'BOTH', 1, 0.0);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('1848f1201222471e88a0d90f10bf8576', 1, 'ZL000121', '肝功十三项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('1848f1201222471e88a0d90f10bf8576', 2, 'ZL000074', '尿常规（迪瑞FUS-1000）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('1848f1201222471e88a0d90f10bf8576', 3, 'ZL000071', '血脂六项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('1848f1201222471e88a0d90f10bf8576', 4, 'ZL000068', '肾功三项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('1848f1201222471e88a0d90f10bf8576', 5, '01002', '一般检查');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('2a07ed26fa9648fda3477405c8d27a5b', 911230000000000003, '2024102002', '女性套餐（40岁及以上人员）', 'nxtc（40sjysry）', 'FEMALE', 1, 0.0);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('2a07ed26fa9648fda3477405c8d27a5b', 1, 'ZL000070', '血脂四项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('2a07ed26fa9648fda3477405c8d27a5b', 2, 'ZL000143', 'IV型胶原蛋白');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('2a07ed26fa9648fda3477405c8d27a5b', 3, 'ZL000062', '血常规（迪瑞BF-6900）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('2a07ed26fa9648fda3477405c8d27a5b', 4, 'ZL000066', '肝功十五项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('2a07ed26fa9648fda3477405c8d27a5b', 5, 'ZL000074', '尿常规（迪瑞FUS-1000）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('2a07ed26fa9648fda3477405c8d27a5b', 6, '66278', '外科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('2a07ed26fa9648fda3477405c8d27a5b', 7, 'ZL000069', '血糖');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('2a07ed26fa9648fda3477405c8d27a5b', 8, 'TJ000001', '载脂蛋白AI、载脂蛋白B');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('2a07ed26fa9648fda3477405c8d27a5b', 9, '69175', '阴道微生态');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('2a07ed26fa9648fda3477405c8d27a5b', 10, '71447', 'C14呼气试验');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('2a07ed26fa9648fda3477405c8d27a5b', 11, '72016', '糖类抗原CA15-3');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('2a07ed26fa9648fda3477405c8d27a5b', 12, 'ZL000068', '肾功三项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('2a07ed26fa9648fda3477405c8d27a5b', 13, '01002', '一般检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('2a07ed26fa9648fda3477405c8d27a5b', 14, '05002', '皮肤科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('2a07ed26fa9648fda3477405c8d27a5b', 15, '06002', '口腔科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('2a07ed26fa9648fda3477405c8d27a5b', 16, '07001', '中医内科问诊');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('2a07ed26fa9648fda3477405c8d27a5b', 17, '09001', '妇科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('2a07ed26fa9648fda3477405c8d27a5b', 18, '11001', '心电图');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('2b698ebc841345b1a1bfbc9d5efb8740', 911230000000000004, '20251215', '健康体检套餐（男）', 'jktjtc（n）', 'MALE', 1, 0.0);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('2b698ebc841345b1a1bfbc9d5efb8740', 1, 'ZL000066', '肝功十五项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('2b698ebc841345b1a1bfbc9d5efb8740', 2, 'ZL000062', '血常规（迪瑞BF-6900）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('2b698ebc841345b1a1bfbc9d5efb8740', 3, '68754', '总前列腺特异性抗原');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('2b698ebc841345b1a1bfbc9d5efb8740', 4, 'ZL000074', '尿常规（迪瑞FUS-1000）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('2b698ebc841345b1a1bfbc9d5efb8740', 5, '66278', '外科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('2b698ebc841345b1a1bfbc9d5efb8740', 6, 'ZL000069', '血糖');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('2b698ebc841345b1a1bfbc9d5efb8740', 7, '12030', '肺部CT、颅脑CT（不出片）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('2b698ebc841345b1a1bfbc9d5efb8740', 8, '66165', '糖化血红蛋白');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('2b698ebc841345b1a1bfbc9d5efb8740', 9, '71447', 'C14呼气试验');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('2b698ebc841345b1a1bfbc9d5efb8740', 10, '72555', '耳穴压丸');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('2b698ebc841345b1a1bfbc9d5efb8740', 11, 'ZL000071', '血脂六项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('2b698ebc841345b1a1bfbc9d5efb8740', 12, 'ZL000068', '肾功三项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('2b698ebc841345b1a1bfbc9d5efb8740', 13, '01002', '一般检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('2b698ebc841345b1a1bfbc9d5efb8740', 14, '05002', '皮肤科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('2b698ebc841345b1a1bfbc9d5efb8740', 15, '06002', '口腔科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('2b698ebc841345b1a1bfbc9d5efb8740', 16, '07001', '中医内科问诊');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('2b698ebc841345b1a1bfbc9d5efb8740', 17, '10003', '甲状腺彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('2b698ebc841345b1a1bfbc9d5efb8740', 18, '10004', '泌尿系统彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('2b698ebc841345b1a1bfbc9d5efb8740', 19, '10011', '消化系统彩超（肝胆胰脾）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('2b698ebc841345b1a1bfbc9d5efb8740', 20, '10032', '双侧颈动脉彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('2b698ebc841345b1a1bfbc9d5efb8740', 21, '11001', '心电图');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('2b698ebc841345b1a1bfbc9d5efb8740', 22, '11005', '骨密度检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('2b698ebc841345b1a1bfbc9d5efb8740', 23, '12019', '颅脑CT（不出片）');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('49f958334751495e939b3408e541cdfb', 911230000000000005, '2025082101', '2025年政府体检（男）', '2025nzftj（n）', 'MALE', 1, 500.0);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('49f958334751495e939b3408e541cdfb', 1, 'ZL000066', '肝功十五项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('49f958334751495e939b3408e541cdfb', 2, 'ZL000062', '血常规（迪瑞BF-6900）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('49f958334751495e939b3408e541cdfb', 3, '68754', '总前列腺特异性抗原');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('49f958334751495e939b3408e541cdfb', 4, 'ZL000074', '尿常规（迪瑞FUS-1000）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('49f958334751495e939b3408e541cdfb', 5, '66278', '外科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('49f958334751495e939b3408e541cdfb', 6, 'ZL000069', '血糖');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('49f958334751495e939b3408e541cdfb', 7, '12030', '肺部CT、颅脑CT（不出片）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('49f958334751495e939b3408e541cdfb', 8, '71447', 'C14呼气试验');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('49f958334751495e939b3408e541cdfb', 9, '72555', '耳穴压丸');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('49f958334751495e939b3408e541cdfb', 10, 'ZL000071', '血脂六项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('49f958334751495e939b3408e541cdfb', 11, 'ZL000068', '肾功三项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('49f958334751495e939b3408e541cdfb', 12, '01002', '一般检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('49f958334751495e939b3408e541cdfb', 13, '05002', '皮肤科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('49f958334751495e939b3408e541cdfb', 14, '06002', '口腔科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('49f958334751495e939b3408e541cdfb', 15, '07001', '中医内科问诊');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('49f958334751495e939b3408e541cdfb', 16, '10003', '甲状腺彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('49f958334751495e939b3408e541cdfb', 17, '10004', '泌尿系统彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('49f958334751495e939b3408e541cdfb', 18, '10011', '消化系统彩超（肝胆胰脾）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('49f958334751495e939b3408e541cdfb', 19, '10032', '双侧颈动脉彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('49f958334751495e939b3408e541cdfb', 20, '11001', '心电图');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('49f958334751495e939b3408e541cdfb', 21, '11005', '骨密度检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('49f958334751495e939b3408e541cdfb', 22, '12019', '颅脑CT（不出片）');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('4e678c06ff104d8fada3a763d04eeabf', 911230000000000006, '2025082102', '2025年政府体检（女）', '2025nzftj（n）', 'FEMALE', 1, 0.0);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('4e678c06ff104d8fada3a763d04eeabf', 1, 'ZL000066', '肝功十五项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('4e678c06ff104d8fada3a763d04eeabf', 2, 'ZL000062', '血常规（迪瑞BF-6900）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('4e678c06ff104d8fada3a763d04eeabf', 3, 'ZL000074', '尿常规（迪瑞FUS-1000）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('4e678c06ff104d8fada3a763d04eeabf', 4, '66278', '外科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('4e678c06ff104d8fada3a763d04eeabf', 5, 'ZL000069', '血糖');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('4e678c06ff104d8fada3a763d04eeabf', 6, '12030', '肺部CT、颅脑CT（不出片）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('4e678c06ff104d8fada3a763d04eeabf', 7, '66011', '妇科彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('4e678c06ff104d8fada3a763d04eeabf', 8, '71447', 'C14呼气试验');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('4e678c06ff104d8fada3a763d04eeabf', 9, '72016', '糖类抗原CA15-3');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('4e678c06ff104d8fada3a763d04eeabf', 10, '72555', '耳穴压丸');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('4e678c06ff104d8fada3a763d04eeabf', 11, 'ZL000071', '血脂六项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('4e678c06ff104d8fada3a763d04eeabf', 12, 'ZL000068', '肾功三项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('4e678c06ff104d8fada3a763d04eeabf', 13, '01002', '一般检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('4e678c06ff104d8fada3a763d04eeabf', 14, '05002', '皮肤科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('4e678c06ff104d8fada3a763d04eeabf', 15, '06002', '口腔科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('4e678c06ff104d8fada3a763d04eeabf', 16, '07001', '中医内科问诊');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('4e678c06ff104d8fada3a763d04eeabf', 17, '09001', '妇科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('4e678c06ff104d8fada3a763d04eeabf', 18, '10001', '腹部彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('4e678c06ff104d8fada3a763d04eeabf', 19, '10003', '甲状腺彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('4e678c06ff104d8fada3a763d04eeabf', 20, '10008', '乳腺彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('4e678c06ff104d8fada3a763d04eeabf', 21, '11001', '心电图');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('4e678c06ff104d8fada3a763d04eeabf', 22, '11005', '骨密度检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('4e678c06ff104d8fada3a763d04eeabf', 23, '12019', '颅脑CT（不出片）');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('510a4326cd624e55a815c1260ccab31d', 911230000000000007, '25101802', '2025教师体检（女1）', '2025jstj（n1）', 'FEMALE', 1, 0.0);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('510a4326cd624e55a815c1260ccab31d', 1, 'ZL000062', '血常规（迪瑞BF-6900）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('510a4326cd624e55a815c1260ccab31d', 2, 'ZL000066', '肝功十五项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('510a4326cd624e55a815c1260ccab31d', 3, 'ZL000074', '尿常规（迪瑞FUS-1000）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('510a4326cd624e55a815c1260ccab31d', 4, 'ZL000069', '血糖');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('510a4326cd624e55a815c1260ccab31d', 5, '12030', '肺部CT、颅脑CT（不出片）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('510a4326cd624e55a815c1260ccab31d', 6, '66011', '妇科彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('510a4326cd624e55a815c1260ccab31d', 7, '66085', '宫颈癌筛查HPV+TCT');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('510a4326cd624e55a815c1260ccab31d', 8, '71447', 'C14呼气试验');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('510a4326cd624e55a815c1260ccab31d', 9, '72555', '耳穴压丸');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('510a4326cd624e55a815c1260ccab31d', 10, 'ZL000071', '血脂六项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('510a4326cd624e55a815c1260ccab31d', 11, 'ZL000068', '肾功三项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('510a4326cd624e55a815c1260ccab31d', 12, '01002', '一般检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('510a4326cd624e55a815c1260ccab31d', 13, '05002', '皮肤科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('510a4326cd624e55a815c1260ccab31d', 14, '06002', '口腔科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('510a4326cd624e55a815c1260ccab31d', 15, '07001', '中医内科问诊');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('510a4326cd624e55a815c1260ccab31d', 16, '10001', '腹部彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('510a4326cd624e55a815c1260ccab31d', 17, '10003', '甲状腺彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('510a4326cd624e55a815c1260ccab31d', 18, '10008', '乳腺彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('510a4326cd624e55a815c1260ccab31d', 19, '11001', '心电图');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('510a4326cd624e55a815c1260ccab31d', 20, '11005', '骨密度检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('510a4326cd624e55a815c1260ccab31d', 21, '12019', '颅脑CT（不出片）');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('726f6ec1c7c344198c7a6a0e9ceca945', 911230000000000008, '2024102003', '男性套餐（40岁以下人员）', 'nxtc（40syxry）', 'MALE', 1, 0.0);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('726f6ec1c7c344198c7a6a0e9ceca945', 1, 'ZL000066', '肝功十五项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('726f6ec1c7c344198c7a6a0e9ceca945', 2, 'ZL000143', 'IV型胶原蛋白');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('726f6ec1c7c344198c7a6a0e9ceca945', 3, 'ZL000062', '血常规（迪瑞BF-6900）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('726f6ec1c7c344198c7a6a0e9ceca945', 4, 'ZL000070', '血脂四项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('726f6ec1c7c344198c7a6a0e9ceca945', 5, 'ZL000074', '尿常规（迪瑞FUS-1000）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('726f6ec1c7c344198c7a6a0e9ceca945', 6, '66278', '外科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('726f6ec1c7c344198c7a6a0e9ceca945', 7, 'ZL000069', '血糖');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('726f6ec1c7c344198c7a6a0e9ceca945', 8, 'TJ000001', '载脂蛋白AI、载脂蛋白B');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('726f6ec1c7c344198c7a6a0e9ceca945', 9, '71447', 'C14呼气试验');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('726f6ec1c7c344198c7a6a0e9ceca945', 10, 'ZL000068', '肾功三项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('726f6ec1c7c344198c7a6a0e9ceca945', 11, '01002', '一般检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('726f6ec1c7c344198c7a6a0e9ceca945', 12, '05002', '皮肤科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('726f6ec1c7c344198c7a6a0e9ceca945', 13, '06002', '口腔科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('726f6ec1c7c344198c7a6a0e9ceca945', 14, '07001', '中医内科问诊');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('726f6ec1c7c344198c7a6a0e9ceca945', 15, '11001', '心电图');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('7c879dbdb354492cb6ba83a86cca1a86', 911230000000000009, '2026062401', '入职体检套餐', 'rztjtc', 'BOTH', 1, 400.0);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('7c879dbdb354492cb6ba83a86cca1a86', 1, '68096', '胸正位 DR');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('7c879dbdb354492cb6ba83a86cca1a86', 2, '68105', '抗人类免疫缺陷病毒抗体（HIV）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('7c879dbdb354492cb6ba83a86cca1a86', 3, 'jygp0690', '肝功十三项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('7c879dbdb354492cb6ba83a86cca1a86', 4, 'ZL000062', '血常规（迪瑞BF-6900）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('7c879dbdb354492cb6ba83a86cca1a86', 5, 'ZL000070', '血脂四项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('7c879dbdb354492cb6ba83a86cca1a86', 6, 'ZL000074', '尿常规（迪瑞FUS-1000）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('7c879dbdb354492cb6ba83a86cca1a86', 7, 'ZL000069', '血糖');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('7c879dbdb354492cb6ba83a86cca1a86', 8, '68106', '梅毒螺旋体抗体');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('7c879dbdb354492cb6ba83a86cca1a86', 9, 'ZL000068', '肾功三项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('7c879dbdb354492cb6ba83a86cca1a86', 10, '76134', '梅毒螺旋非特异性抗体不加热血清反应素试验');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('7c879dbdb354492cb6ba83a86cca1a86', 11, '01002', '一般检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('7c879dbdb354492cb6ba83a86cca1a86', 12, '10011', '消化系统彩超（肝胆胰脾）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('7c879dbdb354492cb6ba83a86cca1a86', 13, '11001', '心电图');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('87a220a0b44f4ebb8a7a5df5d73d6b36', 911230000000000010, '20260402', '2026健康套餐', '2026jktc', 'BOTH', 1, 0.0);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('87a220a0b44f4ebb8a7a5df5d73d6b36', 1, 'ZL000062', '血常规（迪瑞BF-6900）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('87a220a0b44f4ebb8a7a5df5d73d6b36', 2, 'ZL000066', '肝功十五项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('87a220a0b44f4ebb8a7a5df5d73d6b36', 3, 'ZL000220', '离子四项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('87a220a0b44f4ebb8a7a5df5d73d6b36', 4, 'ZL000074', '尿常规（迪瑞FUS-1000）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('87a220a0b44f4ebb8a7a5df5d73d6b36', 5, 'ZL000069', '血糖');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('87a220a0b44f4ebb8a7a5df5d73d6b36', 6, '12030', '肺部CT、颅脑CT（不出片）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('87a220a0b44f4ebb8a7a5df5d73d6b36', 7, 'ZL000071', '血脂六项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('87a220a0b44f4ebb8a7a5df5d73d6b36', 8, 'ZL000068', '肾功三项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('87a220a0b44f4ebb8a7a5df5d73d6b36', 9, '10001', '腹部彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('87a220a0b44f4ebb8a7a5df5d73d6b36', 10, '11001', '心电图');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('87a220a0b44f4ebb8a7a5df5d73d6b36', 11, '12019', '颅脑CT（不出片）');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('967d8980857b45f0b78ba16be5a9d0c5', 911230000000000011, '2026070101', '2026体检套餐（男）', 'tjtc（n）', 'BOTH', 1, 0.0);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('967d8980857b45f0b78ba16be5a9d0c5', 1, 'ZL000066', '肝功十五项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('967d8980857b45f0b78ba16be5a9d0c5', 2, 'ZL000062', '血常规（迪瑞BF-6900）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('967d8980857b45f0b78ba16be5a9d0c5', 3, 'ZL000074', '尿常规（迪瑞FUS-1000）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('967d8980857b45f0b78ba16be5a9d0c5', 4, '66278', '外科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('967d8980857b45f0b78ba16be5a9d0c5', 5, 'ZL000069', '血糖');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('967d8980857b45f0b78ba16be5a9d0c5', 6, '12030', '肺部CT、颅脑CT（不出片）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('967d8980857b45f0b78ba16be5a9d0c5', 7, '66165', '糖化血红蛋白');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('967d8980857b45f0b78ba16be5a9d0c5', 8, '72555', '耳穴压丸');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('967d8980857b45f0b78ba16be5a9d0c5', 9, 'ZL000071', '血脂六项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('967d8980857b45f0b78ba16be5a9d0c5', 10, 'ZL000068', '肾功三项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('967d8980857b45f0b78ba16be5a9d0c5', 11, '01002', '一般检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('967d8980857b45f0b78ba16be5a9d0c5', 12, '05002', '皮肤科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('967d8980857b45f0b78ba16be5a9d0c5', 13, '06002', '口腔科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('967d8980857b45f0b78ba16be5a9d0c5', 14, '07001', '中医内科问诊');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('967d8980857b45f0b78ba16be5a9d0c5', 15, '10003', '甲状腺彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('967d8980857b45f0b78ba16be5a9d0c5', 16, '10004', '泌尿系统彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('967d8980857b45f0b78ba16be5a9d0c5', 17, '10011', '消化系统彩超（肝胆胰脾）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('967d8980857b45f0b78ba16be5a9d0c5', 18, '10032', '双侧颈动脉彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('967d8980857b45f0b78ba16be5a9d0c5', 19, '11001', '心电图');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('967d8980857b45f0b78ba16be5a9d0c5', 20, '11005', '骨密度检查');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('a017ea1112d34591bbf4de30223af91b', 911230000000000012, '2026070102', '2026体检套餐（女）', 'tjtc（n）', 'FEMALE', 1, 0.0);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('a017ea1112d34591bbf4de30223af91b', 1, 'ZL000062', '血常规（迪瑞BF-6900）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('a017ea1112d34591bbf4de30223af91b', 2, 'ZL000066', '肝功十五项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('a017ea1112d34591bbf4de30223af91b', 3, 'ZL000074', '尿常规（迪瑞FUS-1000）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('a017ea1112d34591bbf4de30223af91b', 4, '66278', '外科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('a017ea1112d34591bbf4de30223af91b', 5, 'ZL000069', '血糖');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('a017ea1112d34591bbf4de30223af91b', 6, '12030', '肺部CT、颅脑CT（不出片）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('a017ea1112d34591bbf4de30223af91b', 7, '66011', '妇科彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('a017ea1112d34591bbf4de30223af91b', 8, '66165', '糖化血红蛋白');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('a017ea1112d34591bbf4de30223af91b', 9, '72555', '耳穴压丸');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('a017ea1112d34591bbf4de30223af91b', 10, 'ZL000071', '血脂六项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('a017ea1112d34591bbf4de30223af91b', 11, 'ZL000068', '肾功三项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('a017ea1112d34591bbf4de30223af91b', 12, '01002', '一般检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('a017ea1112d34591bbf4de30223af91b', 13, '05002', '皮肤科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('a017ea1112d34591bbf4de30223af91b', 14, '06002', '口腔科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('a017ea1112d34591bbf4de30223af91b', 15, '07001', '中医内科问诊');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('a017ea1112d34591bbf4de30223af91b', 16, '09001', '妇科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('a017ea1112d34591bbf4de30223af91b', 17, '10001', '腹部彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('a017ea1112d34591bbf4de30223af91b', 18, '10003', '甲状腺彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('a017ea1112d34591bbf4de30223af91b', 19, '10008', '乳腺彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('a017ea1112d34591bbf4de30223af91b', 20, '11001', '心电图');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('a017ea1112d34591bbf4de30223af91b', 21, '11005', '骨密度检查');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('a0b605371ebd4364b09b1bf5861229cf', 911230000000000013, '25101805', '2025教师体检（女不选）', '2025jstj（nbx）', 'FEMALE', 1, 0.0);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('a0b605371ebd4364b09b1bf5861229cf', 1, 'ZL000062', '血常规（迪瑞BF-6900）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('a0b605371ebd4364b09b1bf5861229cf', 2, 'ZL000066', '肝功十五项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('a0b605371ebd4364b09b1bf5861229cf', 3, 'ZL000074', '尿常规（迪瑞FUS-1000）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('a0b605371ebd4364b09b1bf5861229cf', 4, 'ZL000069', '血糖');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('a0b605371ebd4364b09b1bf5861229cf', 5, '12030', '肺部CT、颅脑CT（不出片）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('a0b605371ebd4364b09b1bf5861229cf', 6, '66011', '妇科彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('a0b605371ebd4364b09b1bf5861229cf', 7, '71447', 'C14呼气试验');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('a0b605371ebd4364b09b1bf5861229cf', 8, '72555', '耳穴压丸');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('a0b605371ebd4364b09b1bf5861229cf', 9, 'ZL000071', '血脂六项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('a0b605371ebd4364b09b1bf5861229cf', 10, 'ZL000068', '肾功三项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('a0b605371ebd4364b09b1bf5861229cf', 11, '01002', '一般检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('a0b605371ebd4364b09b1bf5861229cf', 12, '05002', '皮肤科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('a0b605371ebd4364b09b1bf5861229cf', 13, '06002', '口腔科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('a0b605371ebd4364b09b1bf5861229cf', 14, '07001', '中医内科问诊');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('a0b605371ebd4364b09b1bf5861229cf', 15, '10001', '腹部彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('a0b605371ebd4364b09b1bf5861229cf', 16, '10003', '甲状腺彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('a0b605371ebd4364b09b1bf5861229cf', 17, '10008', '乳腺彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('a0b605371ebd4364b09b1bf5861229cf', 18, '11001', '心电图');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('a0b605371ebd4364b09b1bf5861229cf', 19, '11005', '骨密度检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('a0b605371ebd4364b09b1bf5861229cf', 20, '12019', '颅脑CT（不出片）');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('ac3cea3cca1e4c0cb24a67ad09e53f23', 911230000000000014, '25112401', '2025年社工部（男）', '2025nsgb（n）', 'MALE', 1, 0.0);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('ac3cea3cca1e4c0cb24a67ad09e53f23', 1, 'ZL000066', '肝功十五项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('ac3cea3cca1e4c0cb24a67ad09e53f23', 2, 'ZL000062', '血常规（迪瑞BF-6900）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('ac3cea3cca1e4c0cb24a67ad09e53f23', 3, 'ZL000074', '尿常规（迪瑞FUS-1000）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('ac3cea3cca1e4c0cb24a67ad09e53f23', 4, 'ZL000069', '血糖');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('ac3cea3cca1e4c0cb24a67ad09e53f23', 5, '12030', '肺部CT、颅脑CT（不出片）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('ac3cea3cca1e4c0cb24a67ad09e53f23', 6, 'ZL000071', '血脂六项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('ac3cea3cca1e4c0cb24a67ad09e53f23', 7, 'ZL000068', '肾功三项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('ac3cea3cca1e4c0cb24a67ad09e53f23', 8, '10003', '甲状腺彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('ac3cea3cca1e4c0cb24a67ad09e53f23', 9, '10004', '泌尿系统彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('ac3cea3cca1e4c0cb24a67ad09e53f23', 10, '10011', '消化系统彩超（肝胆胰脾）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('ac3cea3cca1e4c0cb24a67ad09e53f23', 11, '11001', '心电图');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('ac3cea3cca1e4c0cb24a67ad09e53f23', 12, '11005', '骨密度检查');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('b7d2a88bdc324f0595618ebb64a36771', 911230000000000015, '20250408', '体检套餐', 'tjtc', 'BOTH', 1, 0.0);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('b7d2a88bdc324f0595618ebb64a36771', 1, 'ZL000066', '肝功十五项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('b7d2a88bdc324f0595618ebb64a36771', 2, 'ZL000062', '血常规（迪瑞BF-6900）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('b7d2a88bdc324f0595618ebb64a36771', 3, 'ZL000074', '尿常规（迪瑞FUS-1000）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('b7d2a88bdc324f0595618ebb64a36771', 4, 'ZL000069', '血糖');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('b7d2a88bdc324f0595618ebb64a36771', 5, '12030', '肺部CT、颅脑CT（不出片）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('b7d2a88bdc324f0595618ebb64a36771', 6, 'ZL000071', '血脂六项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('b7d2a88bdc324f0595618ebb64a36771', 7, 'ZL000068', '肾功三项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('b7d2a88bdc324f0595618ebb64a36771', 8, '10001', '腹部彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('b7d2a88bdc324f0595618ebb64a36771', 9, '10004', '泌尿系统彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('b7d2a88bdc324f0595618ebb64a36771', 10, '10006', '心脏彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('b7d2a88bdc324f0595618ebb64a36771', 11, '10032', '双侧颈动脉彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('b7d2a88bdc324f0595618ebb64a36771', 12, '11001', '心电图');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('b7d2a88bdc324f0595618ebb64a36771', 13, '12019', '颅脑CT（不出片）');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('be5f4bf4148b43019db52f7adf41817e', 911230000000000016, '25101801', '2025教师体检（男）', '2025jstjtc（n）', 'MALE', 1, 0.0);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('be5f4bf4148b43019db52f7adf41817e', 1, 'ZL000062', '血常规（迪瑞BF-6900）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('be5f4bf4148b43019db52f7adf41817e', 2, 'ZL000066', '肝功十五项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('be5f4bf4148b43019db52f7adf41817e', 3, '68754', '总前列腺特异性抗原');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('be5f4bf4148b43019db52f7adf41817e', 4, 'ZL000074', '尿常规（迪瑞FUS-1000）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('be5f4bf4148b43019db52f7adf41817e', 5, 'ZL000069', '血糖');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('be5f4bf4148b43019db52f7adf41817e', 6, '12030', '肺部CT、颅脑CT（不出片）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('be5f4bf4148b43019db52f7adf41817e', 7, '71447', 'C14呼气试验');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('be5f4bf4148b43019db52f7adf41817e', 8, '72555', '耳穴压丸');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('be5f4bf4148b43019db52f7adf41817e', 9, 'ZL000071', '血脂六项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('be5f4bf4148b43019db52f7adf41817e', 10, 'ZL000068', '肾功三项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('be5f4bf4148b43019db52f7adf41817e', 11, '01002', '一般检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('be5f4bf4148b43019db52f7adf41817e', 12, '05002', '皮肤科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('be5f4bf4148b43019db52f7adf41817e', 13, '06002', '口腔科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('be5f4bf4148b43019db52f7adf41817e', 14, '07001', '中医内科问诊');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('be5f4bf4148b43019db52f7adf41817e', 15, '10003', '甲状腺彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('be5f4bf4148b43019db52f7adf41817e', 16, '10004', '泌尿系统彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('be5f4bf4148b43019db52f7adf41817e', 17, '10011', '消化系统彩超（肝胆胰脾）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('be5f4bf4148b43019db52f7adf41817e', 18, '10032', '双侧颈动脉彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('be5f4bf4148b43019db52f7adf41817e', 19, '11001', '心电图');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('be5f4bf4148b43019db52f7adf41817e', 20, '11005', '骨密度检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('be5f4bf4148b43019db52f7adf41817e', 21, '12019', '颅脑CT（不出片）');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('d70e195afe764bfd93a36c3394f8fe9c', 911230000000000017, '2024102004', '女性套餐（40岁以下人员）', 'nxtc（40syxry）', 'FEMALE', 1, 0.0);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('d70e195afe764bfd93a36c3394f8fe9c', 1, 'ZL000070', '血脂四项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('d70e195afe764bfd93a36c3394f8fe9c', 2, 'ZL000062', '血常规（迪瑞BF-6900）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('d70e195afe764bfd93a36c3394f8fe9c', 3, 'ZL000066', '肝功十五项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('d70e195afe764bfd93a36c3394f8fe9c', 4, 'ZL000143', 'IV型胶原蛋白');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('d70e195afe764bfd93a36c3394f8fe9c', 5, 'ZL000074', '尿常规（迪瑞FUS-1000）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('d70e195afe764bfd93a36c3394f8fe9c', 6, '66278', '外科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('d70e195afe764bfd93a36c3394f8fe9c', 7, 'ZL000069', '血糖');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('d70e195afe764bfd93a36c3394f8fe9c', 8, 'TJ000001', '载脂蛋白AI、载脂蛋白B');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('d70e195afe764bfd93a36c3394f8fe9c', 9, '69175', '阴道微生态');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('d70e195afe764bfd93a36c3394f8fe9c', 10, '71447', 'C14呼气试验');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('d70e195afe764bfd93a36c3394f8fe9c', 11, 'ZL000068', '肾功三项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('d70e195afe764bfd93a36c3394f8fe9c', 12, '01002', '一般检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('d70e195afe764bfd93a36c3394f8fe9c', 13, '05002', '皮肤科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('d70e195afe764bfd93a36c3394f8fe9c', 14, '06002', '口腔科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('d70e195afe764bfd93a36c3394f8fe9c', 15, '07001', '中医内科问诊');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('d70e195afe764bfd93a36c3394f8fe9c', 16, '09001', '妇科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('d70e195afe764bfd93a36c3394f8fe9c', 17, '11001', '心电图');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('e00e955635ef4ea099670d216d52942e', 911230000000000018, '2024102001', '男性套餐（40岁及以上人员）', 'nxtc（40sjysry）', 'MALE', 1, 0.0);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('e00e955635ef4ea099670d216d52942e', 1, 'ZL000143', 'IV型胶原蛋白');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('e00e955635ef4ea099670d216d52942e', 2, 'ZL000062', '血常规（迪瑞BF-6900）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('e00e955635ef4ea099670d216d52942e', 3, 'ZL000066', '肝功十五项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('e00e955635ef4ea099670d216d52942e', 4, 'ZL000070', '血脂四项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('e00e955635ef4ea099670d216d52942e', 5, 'ZL000074', '尿常规（迪瑞FUS-1000）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('e00e955635ef4ea099670d216d52942e', 6, '68754', '总前列腺特异性抗原');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('e00e955635ef4ea099670d216d52942e', 7, '66278', '外科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('e00e955635ef4ea099670d216d52942e', 8, 'ZL000069', '血糖');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('e00e955635ef4ea099670d216d52942e', 9, 'TJ000001', '载脂蛋白AI、载脂蛋白B');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('e00e955635ef4ea099670d216d52942e', 10, '71447', 'C14呼气试验');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('e00e955635ef4ea099670d216d52942e', 11, 'ZL000068', '肾功三项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('e00e955635ef4ea099670d216d52942e', 12, '01002', '一般检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('e00e955635ef4ea099670d216d52942e', 13, '05002', '皮肤科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('e00e955635ef4ea099670d216d52942e', 14, '06002', '口腔科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('e00e955635ef4ea099670d216d52942e', 15, '07001', '中医内科问诊');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('e00e955635ef4ea099670d216d52942e', 16, '11001', '心电图');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('ceb455bfd35d4973b1efa1b2893ecfa5', 911230000000000019, '2024080401', '2024湖西街道体检套餐（男）', '2024hxjdtjtc（n）', 'MALE', 2, 0.0);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('ceb455bfd35d4973b1efa1b2893ecfa5', 1, 'ZL000062', '血常规（迪瑞BF-6900）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('ceb455bfd35d4973b1efa1b2893ecfa5', 2, 'ZL000070', '血脂四项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('ceb455bfd35d4973b1efa1b2893ecfa5', 3, 'ZL000066', '肝功十五项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('ceb455bfd35d4973b1efa1b2893ecfa5', 4, 'ZL000074', '尿常规（迪瑞FUS-1000）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('ceb455bfd35d4973b1efa1b2893ecfa5', 5, '66278', '外科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('ceb455bfd35d4973b1efa1b2893ecfa5', 6, 'ZL000069', '血糖');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('ceb455bfd35d4973b1efa1b2893ecfa5', 7, 'TJ000001', '载脂蛋白AI、载脂蛋白B');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('ceb455bfd35d4973b1efa1b2893ecfa5', 8, '71447', 'C14呼气试验');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('ceb455bfd35d4973b1efa1b2893ecfa5', 9, 'ZL000068', '肾功三项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('ceb455bfd35d4973b1efa1b2893ecfa5', 10, '01002', '一般检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('ceb455bfd35d4973b1efa1b2893ecfa5', 11, '05002', '皮肤科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('ceb455bfd35d4973b1efa1b2893ecfa5', 12, '06002', '口腔科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('ceb455bfd35d4973b1efa1b2893ecfa5', 13, '07001', '中医内科问诊');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('ceb455bfd35d4973b1efa1b2893ecfa5', 14, '11001', '心电图');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('27b26f4177e8418a9a4258c3260c2df9', 911230000000000020, '2024050802', '2024年朝阳区政府（女）', '2024ncyqzf（n）', 'FEMALE', 2, 0.0);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('27b26f4177e8418a9a4258c3260c2df9', 1, 'ZL000143', 'IV型胶原蛋白');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('27b26f4177e8418a9a4258c3260c2df9', 2, 'ZL000066', '肝功十五项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('27b26f4177e8418a9a4258c3260c2df9', 3, 'ZL000070', '血脂四项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('27b26f4177e8418a9a4258c3260c2df9', 4, 'ZL000062', '血常规（迪瑞BF-6900）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('27b26f4177e8418a9a4258c3260c2df9', 5, 'ZL000074', '尿常规（迪瑞FUS-1000）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('27b26f4177e8418a9a4258c3260c2df9', 6, '66278', '外科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('27b26f4177e8418a9a4258c3260c2df9', 7, 'ZL000069', '血糖');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('27b26f4177e8418a9a4258c3260c2df9', 8, '66085', '宫颈癌筛查HPV+TCT');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('27b26f4177e8418a9a4258c3260c2df9', 9, 'TJ000001', '载脂蛋白AI、载脂蛋白B');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('27b26f4177e8418a9a4258c3260c2df9', 10, 'ZL000068', '肾功三项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('27b26f4177e8418a9a4258c3260c2df9', 11, '01002', '一般检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('27b26f4177e8418a9a4258c3260c2df9', 12, '05002', '皮肤科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('27b26f4177e8418a9a4258c3260c2df9', 13, '06002', '口腔科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('27b26f4177e8418a9a4258c3260c2df9', 14, '07001', '中医内科问诊');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('27b26f4177e8418a9a4258c3260c2df9', 15, '09001', '妇科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('27b26f4177e8418a9a4258c3260c2df9', 16, '11001', '心电图');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('17e40fec25c9457aa83da76199127bcd', 911230000000000021, '2024041602', '2024职工体检（男50岁及以上）', '2024zgtj（n50s）', 'MALE', 2, 0.0);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('17e40fec25c9457aa83da76199127bcd', 1, 'ZL000070', '血脂四项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('17e40fec25c9457aa83da76199127bcd', 2, 'ZL000066', '肝功十五项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('17e40fec25c9457aa83da76199127bcd', 3, 'ZL000062', '血常规（迪瑞BF-6900）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('17e40fec25c9457aa83da76199127bcd', 4, 'ZL000074', '尿常规（迪瑞FUS-1000）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('17e40fec25c9457aa83da76199127bcd', 5, '68754', '总前列腺特异性抗原');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('17e40fec25c9457aa83da76199127bcd', 6, '66278', '外科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('17e40fec25c9457aa83da76199127bcd', 7, 'ZL000069', '血糖');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('17e40fec25c9457aa83da76199127bcd', 8, '12030', '肺部CT、颅脑CT（不出片）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('17e40fec25c9457aa83da76199127bcd', 9, 'TJ000001', '载脂蛋白AI、载脂蛋白B');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('17e40fec25c9457aa83da76199127bcd', 10, 'ZL000068', '肾功三项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('17e40fec25c9457aa83da76199127bcd', 11, '01002', '一般检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('17e40fec25c9457aa83da76199127bcd', 12, '06002', '口腔科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('17e40fec25c9457aa83da76199127bcd', 13, '07001', '中医内科问诊');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('17e40fec25c9457aa83da76199127bcd', 14, '11001', '心电图');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('17e40fec25c9457aa83da76199127bcd', 15, '11005', '骨密度检查');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('15649f8ebdf3405585df04b926284306', 911230000000000022, '2024080402', '2024湖西街道体检套餐（女）', '2024hxjdtjtc（n）', 'FEMALE', 2, 0.0);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('15649f8ebdf3405585df04b926284306', 1, 'ZL000066', '肝功十五项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('15649f8ebdf3405585df04b926284306', 2, 'ZL000070', '血脂四项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('15649f8ebdf3405585df04b926284306', 3, 'ZL000062', '血常规（迪瑞BF-6900）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('15649f8ebdf3405585df04b926284306', 4, 'ZL000074', '尿常规（迪瑞FUS-1000）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('15649f8ebdf3405585df04b926284306', 5, '66278', '外科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('15649f8ebdf3405585df04b926284306', 6, 'ZL000069', '血糖');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('15649f8ebdf3405585df04b926284306', 7, 'TJ000001', '载脂蛋白AI、载脂蛋白B');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('15649f8ebdf3405585df04b926284306', 8, '71447', 'C14呼气试验');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('15649f8ebdf3405585df04b926284306', 9, 'ZL000068', '肾功三项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('15649f8ebdf3405585df04b926284306', 10, '01002', '一般检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('15649f8ebdf3405585df04b926284306', 11, '05002', '皮肤科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('15649f8ebdf3405585df04b926284306', 12, '06002', '口腔科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('15649f8ebdf3405585df04b926284306', 13, '07001', '中医内科问诊');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('15649f8ebdf3405585df04b926284306', 14, '09001', '妇科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('15649f8ebdf3405585df04b926284306', 15, '11001', '心电图');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('1610fd7321384d8d93c5773acbe1006f', 911230000000000023, '2024041705', '吉林省和信科技有限公司体检套餐', '2024dwtj', 'BOTH', 3, 0.0);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('1610fd7321384d8d93c5773acbe1006f', 1, 'ZL000103', '血糖(贝克曼库尔特DxC700AU)');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('1610fd7321384d8d93c5773acbe1006f', 2, 'ZL000062', '血常规（迪瑞BF-6900）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('1610fd7321384d8d93c5773acbe1006f', 3, 'ZL000100', '血脂六项（贝克曼库尔特DxC700AU）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('1610fd7321384d8d93c5773acbe1006f', 4, 'ZL000101', '肝功十五项(贝克曼库尔特DxC700AU)');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('1610fd7321384d8d93c5773acbe1006f', 5, 'ZL000102', '肾功三项（贝克曼库尔特DxC700AU）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('1610fd7321384d8d93c5773acbe1006f', 6, 'ZL000074', '尿常规（迪瑞FUS-1000）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('1610fd7321384d8d93c5773acbe1006f', 7, '12030', '肺部CT、颅脑CT（不出片）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('1610fd7321384d8d93c5773acbe1006f', 8, '10001', '腹部彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('1610fd7321384d8d93c5773acbe1006f', 9, '10003', '甲状腺彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('1610fd7321384d8d93c5773acbe1006f', 10, '11001', '心电图');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('1610fd7321384d8d93c5773acbe1006f', 11, '12019', '颅脑CT（不出片）');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('4b4f8f1c1eba4545a5759d64fea39263', 911230000000000024, '20240707', '2024体检套餐（1）', '2024tjtc（1）', 'BOTH', 3, 0.0);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('4b4f8f1c1eba4545a5759d64fea39263', 1, 'jygp0115', '乙肝两对半（胶体金法）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('4b4f8f1c1eba4545a5759d64fea39263', 2, 'ZL000066', '肝功十五项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('4b4f8f1c1eba4545a5759d64fea39263', 3, 'ZL000062', '血常规（迪瑞BF-6900）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('4b4f8f1c1eba4545a5759d64fea39263', 4, 'ZL000074', '尿常规（迪瑞FUS-1000）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('4b4f8f1c1eba4545a5759d64fea39263', 5, 'ZL000069', '血糖');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('4b4f8f1c1eba4545a5759d64fea39263', 6, '12030', '肺部CT、颅脑CT（不出片）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('4b4f8f1c1eba4545a5759d64fea39263', 7, 'ZL000071', '血脂六项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('4b4f8f1c1eba4545a5759d64fea39263', 8, 'ZL000068', '肾功三项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('4b4f8f1c1eba4545a5759d64fea39263', 9, '10001', '腹部彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('4b4f8f1c1eba4545a5759d64fea39263', 10, '10003', '甲状腺彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('4b4f8f1c1eba4545a5759d64fea39263', 11, '10006', '心脏彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('4b4f8f1c1eba4545a5759d64fea39263', 12, '11001', '心电图');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('4b4f8f1c1eba4545a5759d64fea39263', 13, '12019', '颅脑CT（不出片）');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('cb713f9d8ab94f66b0e72892c889ff5f', 911230000000000025, '2021050801', '2024年朝阳区政府（男）', '2024ncyqzf（n）', 'MALE', 3, 0.0);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('cb713f9d8ab94f66b0e72892c889ff5f', 1, 'ZL000066', '肝功十五项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('cb713f9d8ab94f66b0e72892c889ff5f', 2, 'ZL000070', '血脂四项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('cb713f9d8ab94f66b0e72892c889ff5f', 3, 'ZL000062', '血常规（迪瑞BF-6900）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('cb713f9d8ab94f66b0e72892c889ff5f', 4, 'ZL000143', 'IV型胶原蛋白');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('cb713f9d8ab94f66b0e72892c889ff5f', 5, '68754', '总前列腺特异性抗原');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('cb713f9d8ab94f66b0e72892c889ff5f', 6, 'ZL000074', '尿常规（迪瑞FUS-1000）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('cb713f9d8ab94f66b0e72892c889ff5f', 7, '66278', '外科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('cb713f9d8ab94f66b0e72892c889ff5f', 8, 'ZL000069', '血糖');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('cb713f9d8ab94f66b0e72892c889ff5f', 9, 'TJ000001', '载脂蛋白AI、载脂蛋白B');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('cb713f9d8ab94f66b0e72892c889ff5f', 10, 'ZL000068', '肾功三项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('cb713f9d8ab94f66b0e72892c889ff5f', 11, '01002', '一般检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('cb713f9d8ab94f66b0e72892c889ff5f', 12, '05002', '皮肤科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('cb713f9d8ab94f66b0e72892c889ff5f', 13, '06002', '口腔科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('cb713f9d8ab94f66b0e72892c889ff5f', 14, '07001', '中医内科问诊');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('cb713f9d8ab94f66b0e72892c889ff5f', 15, '11001', '心电图');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('cd5ba067049e4dd5b818614f0d1c1f22', 911230000000000026, '2024041601', '2024职工体检（男）', '2024zgtj（n）', 'MALE', 3, 0.0);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('cd5ba067049e4dd5b818614f0d1c1f22', 1, 'ZL000070', '血脂四项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('cd5ba067049e4dd5b818614f0d1c1f22', 2, 'ZL000066', '肝功十五项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('cd5ba067049e4dd5b818614f0d1c1f22', 3, 'ZL000062', '血常规（迪瑞BF-6900）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('cd5ba067049e4dd5b818614f0d1c1f22', 4, 'ZL000074', '尿常规（迪瑞FUS-1000）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('cd5ba067049e4dd5b818614f0d1c1f22', 5, '66278', '外科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('cd5ba067049e4dd5b818614f0d1c1f22', 6, 'ZL000069', '血糖');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('cd5ba067049e4dd5b818614f0d1c1f22', 7, '12030', '肺部CT、颅脑CT（不出片）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('cd5ba067049e4dd5b818614f0d1c1f22', 8, 'TJ000001', '载脂蛋白AI、载脂蛋白B');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('cd5ba067049e4dd5b818614f0d1c1f22', 9, 'ZL000068', '肾功三项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('cd5ba067049e4dd5b818614f0d1c1f22', 10, '01002', '一般检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('cd5ba067049e4dd5b818614f0d1c1f22', 11, '06002', '口腔科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('cd5ba067049e4dd5b818614f0d1c1f22', 12, '07001', '中医内科问诊');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('cd5ba067049e4dd5b818614f0d1c1f22', 13, '11001', '心电图');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('cd5ba067049e4dd5b818614f0d1c1f22', 14, '11005', '骨密度检查');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('abe11d0e6ea44e02ac25ab6e03de2de9', 911230000000000027, '202608', '2026肿瘤风险筛查套餐（男）', '2026zlfxsctc（n）', 'BOTH', 3, 1082.7);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('abe11d0e6ea44e02ac25ab6e03de2de9', 1, 'ZL000066', '肝功十五项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('abe11d0e6ea44e02ac25ab6e03de2de9', 2, 'ZL000062', '血常规（迪瑞BF-6900）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('abe11d0e6ea44e02ac25ab6e03de2de9', 3, 'ZL000074', '尿常规（迪瑞FUS-1000）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('abe11d0e6ea44e02ac25ab6e03de2de9', 4, 'ZL000069', '血糖');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('abe11d0e6ea44e02ac25ab6e03de2de9', 5, '12030', '肺部CT、颅脑CT（不出片）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('abe11d0e6ea44e02ac25ab6e03de2de9', 6, 'jygp1197', '男性肿瘤标志物五项（贝克曼库尔特DxI 800）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('abe11d0e6ea44e02ac25ab6e03de2de9', 7, '71447', 'C14呼气试验');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('abe11d0e6ea44e02ac25ab6e03de2de9', 8, 'ZL000071', '血脂六项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('abe11d0e6ea44e02ac25ab6e03de2de9', 9, 'ZL000068', '肾功三项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('abe11d0e6ea44e02ac25ab6e03de2de9', 10, '01002', '一般检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('abe11d0e6ea44e02ac25ab6e03de2de9', 11, '10001', '腹部彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('abe11d0e6ea44e02ac25ab6e03de2de9', 12, '10003', '甲状腺彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('abe11d0e6ea44e02ac25ab6e03de2de9', 13, '10004', '泌尿系统彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('abe11d0e6ea44e02ac25ab6e03de2de9', 14, '11001', '心电图');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('764bf62b5fff4b8690955cb0031bf8b3', 911230000000000028, '2024041701', '2024职工体检（女）（未婚）', '2024zgtj（n）（wh）', 'FEMALE', 3, 0.0);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('764bf62b5fff4b8690955cb0031bf8b3', 1, 'ZL000062', '血常规（迪瑞BF-6900）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('764bf62b5fff4b8690955cb0031bf8b3', 2, 'ZL000066', '肝功十五项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('764bf62b5fff4b8690955cb0031bf8b3', 3, 'ZL000070', '血脂四项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('764bf62b5fff4b8690955cb0031bf8b3', 4, 'ZL000074', '尿常规（迪瑞FUS-1000）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('764bf62b5fff4b8690955cb0031bf8b3', 5, '66278', '外科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('764bf62b5fff4b8690955cb0031bf8b3', 6, 'ZL000069', '血糖');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('764bf62b5fff4b8690955cb0031bf8b3', 7, '12030', '肺部CT、颅脑CT（不出片）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('764bf62b5fff4b8690955cb0031bf8b3', 8, 'TJ000001', '载脂蛋白AI、载脂蛋白B');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('764bf62b5fff4b8690955cb0031bf8b3', 9, 'ZL000068', '肾功三项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('764bf62b5fff4b8690955cb0031bf8b3', 10, '01002', '一般检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('764bf62b5fff4b8690955cb0031bf8b3', 11, '06002', '口腔科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('764bf62b5fff4b8690955cb0031bf8b3', 12, '07001', '中医内科问诊');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('764bf62b5fff4b8690955cb0031bf8b3', 13, '09001', '妇科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('764bf62b5fff4b8690955cb0031bf8b3', 14, '11001', '心电图');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('764bf62b5fff4b8690955cb0031bf8b3', 15, '11005', '骨密度检查');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('e9c6bf6b641b40ef990b8ec2c8cc5475', 911230000000000029, '2024061701', '体检套餐', 'tjtc', 'BOTH', 3, 600.0);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('e9c6bf6b641b40ef990b8ec2c8cc5475', 1, 'ZL000062', '血常规（迪瑞BF-6900）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('e9c6bf6b641b40ef990b8ec2c8cc5475', 2, 'ZL000066', '肝功十五项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('e9c6bf6b641b40ef990b8ec2c8cc5475', 3, 'ZL000070', '血脂四项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('e9c6bf6b641b40ef990b8ec2c8cc5475', 4, 'ZL000069', '血糖');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('e9c6bf6b641b40ef990b8ec2c8cc5475', 5, '12030', '肺部CT、颅脑CT（不出片）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('e9c6bf6b641b40ef990b8ec2c8cc5475', 6, 'ZL000068', '肾功三项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('e9c6bf6b641b40ef990b8ec2c8cc5475', 7, '10032', '双侧颈动脉彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('e9c6bf6b641b40ef990b8ec2c8cc5475', 8, '12019', '颅脑CT（不出片）');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('ed97acc88d7a4944b2a28bd4765ec227', 911230000000000030, '2024041702', '2024职工体检（女）（已婚）', '2024zgtj（n）（yh）', 'FEMALE', 4, 0.0);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('ed97acc88d7a4944b2a28bd4765ec227', 1, 'ZL000066', '肝功十五项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('ed97acc88d7a4944b2a28bd4765ec227', 2, 'ZL000070', '血脂四项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('ed97acc88d7a4944b2a28bd4765ec227', 3, 'ZL000062', '血常规（迪瑞BF-6900）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('ed97acc88d7a4944b2a28bd4765ec227', 4, 'ZL000074', '尿常规（迪瑞FUS-1000）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('ed97acc88d7a4944b2a28bd4765ec227', 5, '66278', '外科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('ed97acc88d7a4944b2a28bd4765ec227', 6, 'ZL000069', '血糖');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('ed97acc88d7a4944b2a28bd4765ec227', 7, '12030', '肺部CT、颅脑CT（不出片）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('ed97acc88d7a4944b2a28bd4765ec227', 8, '66085', '宫颈癌筛查HPV+TCT');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('ed97acc88d7a4944b2a28bd4765ec227', 9, 'TJ000001', '载脂蛋白AI、载脂蛋白B');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('ed97acc88d7a4944b2a28bd4765ec227', 10, 'ZL000068', '肾功三项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('ed97acc88d7a4944b2a28bd4765ec227', 11, '01002', '一般检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('ed97acc88d7a4944b2a28bd4765ec227', 12, '06002', '口腔科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('ed97acc88d7a4944b2a28bd4765ec227', 13, '07001', '中医内科问诊');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('ed97acc88d7a4944b2a28bd4765ec227', 14, '09001', '妇科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('ed97acc88d7a4944b2a28bd4765ec227', 15, '11001', '心电图');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('ed97acc88d7a4944b2a28bd4765ec227', 16, '11005', '骨密度检查');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('3ff38f6eebc74e57b32340ce6d1a49c2', 911230000000000031, '2026030601', '超声波洁治术（全口洗牙）', 'csbjzs（qkxy）', 'BOTH', 4, 38.0);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('3ff38f6eebc74e57b32340ce6d1a49c2', 1, '75856', '超声波洁治术（全口洗牙）');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('3731483bdb2a4748b79dbc64ede08306', 911230000000000032, '2026070805', '2026永春肿瘤筛查男', '2026zzftj', 'BOTH', 4, 0.0);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('3731483bdb2a4748b79dbc64ede08306', 1, 'ZL000066', '肝功十五项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('3731483bdb2a4748b79dbc64ede08306', 2, 'ZL000062', '血常规（迪瑞BF-6900）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('3731483bdb2a4748b79dbc64ede08306', 3, 'ZL000074', '尿常规（迪瑞FUS-1000）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('3731483bdb2a4748b79dbc64ede08306', 4, 'ZL000069', '血糖');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('3731483bdb2a4748b79dbc64ede08306', 5, '12030', '肺部CT、颅脑CT（不出片）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('3731483bdb2a4748b79dbc64ede08306', 6, 'jygp1197', '男性肿瘤标志物五项（贝克曼库尔特DxI 800）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('3731483bdb2a4748b79dbc64ede08306', 7, '71447', 'C14呼气试验');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('3731483bdb2a4748b79dbc64ede08306', 8, 'ZL000071', '血脂六项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('3731483bdb2a4748b79dbc64ede08306', 9, 'ZL000068', '肾功三项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('3731483bdb2a4748b79dbc64ede08306', 10, '01002', '一般检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('3731483bdb2a4748b79dbc64ede08306', 11, '10001', '腹部彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('3731483bdb2a4748b79dbc64ede08306', 12, '10003', '甲状腺彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('3731483bdb2a4748b79dbc64ede08306', 13, '10004', '泌尿系统彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('3731483bdb2a4748b79dbc64ede08306', 14, '11001', '心电图');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('318efcce8eb94f6f8ab85ffac7010441', 911230000000000033, '2024372', '2026女神节优惠套餐（一）', 'nsjyhtc（y）', 'FEMALE', 6, 320.0);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('318efcce8eb94f6f8ab85ffac7010441', 1, '68021', '阴道分泌物检测');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('318efcce8eb94f6f8ab85ffac7010441', 2, '66085', '宫颈癌筛查HPV+TCT');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('318efcce8eb94f6f8ab85ffac7010441', 3, '09001', '妇科检查');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('69b81324b0e441cabe0cae5ff1c8629c', 911230000000000034, '202437', '女神节中医套餐（一）', 'nsjzytc（y）', 'BOTH', 6, 388.0);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('69b81324b0e441cabe0cae5ff1c8629c', 1, '68013', '刮痧治疗（1次）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('69b81324b0e441cabe0cae5ff1c8629c', 2, '68017', '小儿推拿（10次）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('69b81324b0e441cabe0cae5ff1c8629c', 3, '66078', '火罐治疗（1次）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('69b81324b0e441cabe0cae5ff1c8629c', 4, '68016', '磁振热治疗（1次）');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('77418648b31a4dd8a5cca965d3f65333', 911230000000000035, '2024371', '女神节中医套餐（二）', 'nsjzytc（e）', 'BOTH', 6, 380.0);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('77418648b31a4dd8a5cca965d3f65333', 1, '68013', '刮痧治疗（1次）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('77418648b31a4dd8a5cca965d3f65333', 2, '68018', '局部艾灸（10次）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('77418648b31a4dd8a5cca965d3f65333', 3, '66075', '头部针灸（5次）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('77418648b31a4dd8a5cca965d3f65333', 4, '66077', '四肢针灸（5次）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('77418648b31a4dd8a5cca965d3f65333', 5, '66078', '火罐治疗（1次）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('77418648b31a4dd8a5cca965d3f65333', 6, '68016', '磁振热治疗（1次）');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('c2c29d825cc74c4a82f8e06419c960b7', 911230000000000036, '20243803', '体验套餐（一）', 'tytc（y）', 'BOTH', 6, 3.8);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('c2c29d825cc74c4a82f8e06419c960b7', 1, '68033', '小儿推拿体验卡（10分钟）');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('caa5af311a2b4179b1dc3a53f1349ef0', 911230000000000037, '20243701', '2026女神节优惠套餐（二）', 'nsjyhtc（e）', 'FEMALE', 6, 374.0);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('caa5af311a2b4179b1dc3a53f1349ef0', 1, '66085', '宫颈癌筛查HPV+TCT');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('caa5af311a2b4179b1dc3a53f1349ef0', 2, '09001', '妇科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('caa5af311a2b4179b1dc3a53f1349ef0', 3, '10007', '阴道彩超（或腹部彩超）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('caa5af311a2b4179b1dc3a53f1349ef0', 4, '10008', '乳腺彩超');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('d40c815feaf946cd89a0cfbe116f0ffa', 911230000000000038, '20243703', '2026女神节优惠套餐（三）', 'nsjyhtc（s）', 'FEMALE', 6, 439.0);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('d40c815feaf946cd89a0cfbe116f0ffa', 1, '68022', '阴道彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('d40c815feaf946cd89a0cfbe116f0ffa', 2, '66553', '甲功三项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('d40c815feaf946cd89a0cfbe116f0ffa', 3, '66560', '性激素六项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('d40c815feaf946cd89a0cfbe116f0ffa', 4, '10003', '甲状腺彩超');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('ff9ec870491f436c84e5164e8f3a0915', 911230000000000039, '2026070806', '2026永春肿瘤筛查女', '2026yczlscn', 'BOTH', 6, 0.0);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('ff9ec870491f436c84e5164e8f3a0915', 1, 'ZL000062', '血常规（迪瑞BF-6900）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('ff9ec870491f436c84e5164e8f3a0915', 2, 'ZL000066', '肝功十五项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('ff9ec870491f436c84e5164e8f3a0915', 3, 'ZL000074', '尿常规（迪瑞FUS-1000）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('ff9ec870491f436c84e5164e8f3a0915', 4, 'ZL000069', '血糖');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('ff9ec870491f436c84e5164e8f3a0915', 5, '12030', '肺部CT、颅脑CT（不出片）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('ff9ec870491f436c84e5164e8f3a0915', 6, 'jygp1198', '女性肿瘤标志物五项（贝克曼库尔特DxI 800）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('ff9ec870491f436c84e5164e8f3a0915', 7, '71447', 'C14呼气试验');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('ff9ec870491f436c84e5164e8f3a0915', 8, 'ZL000071', '血脂六项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('ff9ec870491f436c84e5164e8f3a0915', 9, 'ZL000068', '肾功三项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('ff9ec870491f436c84e5164e8f3a0915', 10, '01002', '一般检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('ff9ec870491f436c84e5164e8f3a0915', 11, '10001', '腹部彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('ff9ec870491f436c84e5164e8f3a0915', 12, '10003', '甲状腺彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('ff9ec870491f436c84e5164e8f3a0915', 13, '10004', '泌尿系统彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('ff9ec870491f436c84e5164e8f3a0915', 14, '11001', '心电图');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('dbd0fe5def9a44208f2475259b122697', 911230000000000040, '20243801', '体验套餐（二）', 'tytc（e）', 'BOTH', 7, 38.0);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('dbd0fe5def9a44208f2475259b122697', 1, '68028', '洗牙');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('66364ec8dada4a6b93001f2065d56f49', 911230000000000041, '5255545', '妇保套餐（2）', 'fbtc（2）', 'FEMALE', 7, 0.0);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('66364ec8dada4a6b93001f2065d56f49', 1, '64805', '血常规（体检）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('66364ec8dada4a6b93001f2065d56f49', 2, '64851', '尿常规');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('6f72dee195914b68aa8b60731960d553', 911230000000000042, '20240226', '入职体检（暂停）', 'cyhwtj', 'BOTH', 8, 100.0);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('6f72dee195914b68aa8b60731960d553', 1, 'ZL000070', '血脂四项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('6f72dee195914b68aa8b60731960d553', 2, 'jygp0115', '乙肝两对半（胶体金法）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('6f72dee195914b68aa8b60731960d553', 3, 'ZL000066', '肝功十五项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('6f72dee195914b68aa8b60731960d553', 4, 'ZL000069', '血糖');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('6f72dee195914b68aa8b60731960d553', 5, '68063', '胸正位DR（不出片）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('6f72dee195914b68aa8b60731960d553', 6, '11001', '心电图');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('3eef6b4de1d04af890eadc17f7a1e092', 911230000000000043, '202603', '2026消化道疾病筛查套餐（胃镜）', '2026xhdjbsctc（wj）', 'BOTH', 8, 850.9);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('3eef6b4de1d04af890eadc17f7a1e092', 1, 'ZL000066', '肝功十五项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('3eef6b4de1d04af890eadc17f7a1e092', 2, 'ZL000062', '血常规（迪瑞BF-6900）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('3eef6b4de1d04af890eadc17f7a1e092', 3, 'ZL000074', '尿常规（迪瑞FUS-1000）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('3eef6b4de1d04af890eadc17f7a1e092', 4, 'ZL000069', '血糖');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('3eef6b4de1d04af890eadc17f7a1e092', 5, '71447', 'C14呼气试验');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('3eef6b4de1d04af890eadc17f7a1e092', 6, '76018', '无痛胃镜');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('3eef6b4de1d04af890eadc17f7a1e092', 7, 'ZL000071', '血脂六项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('3eef6b4de1d04af890eadc17f7a1e092', 8, 'ZL000068', '肾功三项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('3eef6b4de1d04af890eadc17f7a1e092', 9, '01002', '一般检查');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('08bbc710e68e472f9f33fac3780c1d96', 911230000000000044, '2024032202', '社区体检（女）', 'sqtj（n）', 'FEMALE', 8, 0.0);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('08bbc710e68e472f9f33fac3780c1d96', 1, '66011', '妇科彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('08bbc710e68e472f9f33fac3780c1d96', 2, '09001', '妇科检查');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('9bcf083c406f4dd3aa85e83837e6dfc6', 911230000000000045, '20240322', '社区体检（男）', 'sqtj（n）', 'MALE', 8, 0.0);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('9bcf083c406f4dd3aa85e83837e6dfc6', 1, '10003', '甲状腺彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('9bcf083c406f4dd3aa85e83837e6dfc6', 2, '10009', '前列腺彩超');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('89a4f6bcd5f64ff3afc8556925593e48', 911230000000000046, '202606', '2026消化道疾病筛查套餐（肠镜）', '2026xhdjbsctc（cj）', 'BOTH', 9, 927.2);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('89a4f6bcd5f64ff3afc8556925593e48', 1, 'ZL000062', '血常规（迪瑞BF-6900）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('89a4f6bcd5f64ff3afc8556925593e48', 2, 'ZL000066', '肝功十五项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('89a4f6bcd5f64ff3afc8556925593e48', 3, 'ZL000074', '尿常规（迪瑞FUS-1000）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('89a4f6bcd5f64ff3afc8556925593e48', 4, 'ZL000069', '血糖');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('89a4f6bcd5f64ff3afc8556925593e48', 5, '71447', 'C14呼气试验');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('89a4f6bcd5f64ff3afc8556925593e48', 6, 'ZL000071', '血脂六项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('89a4f6bcd5f64ff3afc8556925593e48', 7, '76019', '无痛肠镜');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('89a4f6bcd5f64ff3afc8556925593e48', 8, 'ZL000068', '肾功三项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('89a4f6bcd5f64ff3afc8556925593e48', 9, '01002', '一般检查');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('1f44275442c54e20bb7090dbbcdbf266', 911230000000000047, '202432601', '体检套餐（一）', 'tjtc（y）', 'BOTH', 9, 148.0);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('1f44275442c54e20bb7090dbbcdbf266', 1, '68054', '双肾彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('1f44275442c54e20bb7090dbbcdbf266', 2, '68055', '动态心电（24h）');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('16e6f07f7e29410b89f81067c7169c44', 911230000000000048, '2023614', '教育系统体检（男）', 'jyxttj（n）', 'MALE', 10, 0.0);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('16e6f07f7e29410b89f81067c7169c44', 1, 'ZL000143', 'IV型胶原蛋白');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('16e6f07f7e29410b89f81067c7169c44', 2, 'ZL000062', '血常规（迪瑞BF-6900）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('16e6f07f7e29410b89f81067c7169c44', 3, 'ZL000100', '血脂六项（贝克曼库尔特DxC700AU）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('16e6f07f7e29410b89f81067c7169c44', 4, 'ZL000103', '血糖(贝克曼库尔特DxC700AU)');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('16e6f07f7e29410b89f81067c7169c44', 5, 'ZL000101', '肝功十五项(贝克曼库尔特DxC700AU)');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('16e6f07f7e29410b89f81067c7169c44', 6, 'ZL000102', '肾功三项（贝克曼库尔特DxC700AU）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('16e6f07f7e29410b89f81067c7169c44', 7, 'ZL000074', '尿常规（迪瑞FUS-1000）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('16e6f07f7e29410b89f81067c7169c44', 8, 'jygp1197', '男性肿瘤标志物五项（贝克曼库尔特DxI 800）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('16e6f07f7e29410b89f81067c7169c44', 9, '64983', '肘关节CT（不出片）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('16e6f07f7e29410b89f81067c7169c44', 10, '66312', '幽门螺旋杆菌分型检测');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('16e6f07f7e29410b89f81067c7169c44', 11, '01002', '一般检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('16e6f07f7e29410b89f81067c7169c44', 12, '05002', '皮肤科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('16e6f07f7e29410b89f81067c7169c44', 13, '06002', '口腔科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('16e6f07f7e29410b89f81067c7169c44', 14, '07001', '中医内科问诊');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('16e6f07f7e29410b89f81067c7169c44', 15, '10003', '甲状腺彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('16e6f07f7e29410b89f81067c7169c44', 16, '10006', '心脏彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('16e6f07f7e29410b89f81067c7169c44', 17, '10009', '前列腺彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('16e6f07f7e29410b89f81067c7169c44', 18, '10032', '双侧颈动脉彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('16e6f07f7e29410b89f81067c7169c44', 19, '11001', '心电图');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('0a04b85cb2d142d386d1eb49e39ea849', 911230000000000049, '202432602', '体检套餐（二）', 'tjtc（e）', 'BOTH', 10, 671.0);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('0a04b85cb2d142d386d1eb49e39ea849', 1, '68022', '阴道彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('0a04b85cb2d142d386d1eb49e39ea849', 2, '12030', '肺部CT、颅脑CT（不出片）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('0a04b85cb2d142d386d1eb49e39ea849', 3, 'jygp1198', '女性肿瘤标志物五项（贝克曼库尔特DxI 800）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('0a04b85cb2d142d386d1eb49e39ea849', 4, '10003', '甲状腺彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('0a04b85cb2d142d386d1eb49e39ea849', 5, '10008', '乳腺彩超');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('6b9129f2145f4d628d70d0a7de7ebaa7', 911230000000000050, '202607', '2026体检套餐', '2026tjtc', 'BOTH', 10, 818.3);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('6b9129f2145f4d628d70d0a7de7ebaa7', 1, 'ZL000220', '离子四项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('6b9129f2145f4d628d70d0a7de7ebaa7', 2, 'ZL000062', '血常规（迪瑞BF-6900）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('6b9129f2145f4d628d70d0a7de7ebaa7', 3, 'ZL000066', '肝功十五项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('6b9129f2145f4d628d70d0a7de7ebaa7', 4, 'ZL000074', '尿常规（迪瑞FUS-1000）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('6b9129f2145f4d628d70d0a7de7ebaa7', 5, 'ZL000069', '血糖');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('6b9129f2145f4d628d70d0a7de7ebaa7', 6, '12030', '肺部CT、颅脑CT（不出片）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('6b9129f2145f4d628d70d0a7de7ebaa7', 7, 'ZL000071', '血脂六项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('6b9129f2145f4d628d70d0a7de7ebaa7', 8, 'ZL000068', '肾功三项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('6b9129f2145f4d628d70d0a7de7ebaa7', 9, '01002', '一般检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('6b9129f2145f4d628d70d0a7de7ebaa7', 10, '10001', '腹部彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('6b9129f2145f4d628d70d0a7de7ebaa7', 11, '11001', '心电图');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('6b9129f2145f4d628d70d0a7de7ebaa7', 12, '12019', '颅脑CT（不出片）');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('888078370f4e40f789967f56b8681e12', 911230000000000051, '2023424', '单位体检（男）', 'dwtj（n）', 'MALE', 11, 1.0);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('888078370f4e40f789967f56b8681e12', 1, 'ZL000065', '乙肝两对半(定量分析)');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('888078370f4e40f789967f56b8681e12', 2, 'ZL000100', '血脂六项（贝克曼库尔特DxC700AU）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('888078370f4e40f789967f56b8681e12', 3, 'ZL000103', '血糖(贝克曼库尔特DxC700AU)');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('888078370f4e40f789967f56b8681e12', 4, 'ZL000102', '肾功三项（贝克曼库尔特DxC700AU）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('888078370f4e40f789967f56b8681e12', 5, 'ZL000101', '肝功十五项(贝克曼库尔特DxC700AU)');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('888078370f4e40f789967f56b8681e12', 6, 'ZL000062', '血常规（迪瑞BF-6900）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('888078370f4e40f789967f56b8681e12', 7, 'ZL000074', '尿常规（迪瑞FUS-1000）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('888078370f4e40f789967f56b8681e12', 8, '12030', '肺部CT、颅脑CT（不出片）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('888078370f4e40f789967f56b8681e12', 9, '64889', '彩超检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('888078370f4e40f789967f56b8681e12', 10, 'jygp1199', '胃幽门螺旋杆菌抗体');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('888078370f4e40f789967f56b8681e12', 11, 'ZL000110', '甲功五项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('888078370f4e40f789967f56b8681e12', 12, '66165', '糖化血红蛋白');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('888078370f4e40f789967f56b8681e12', 13, '10001', '腹部彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('888078370f4e40f789967f56b8681e12', 14, '10003', '甲状腺彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('888078370f4e40f789967f56b8681e12', 15, '10004', '泌尿系统彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('888078370f4e40f789967f56b8681e12', 16, '10006', '心脏彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('888078370f4e40f789967f56b8681e12', 17, '10032', '双侧颈动脉彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('888078370f4e40f789967f56b8681e12', 18, '11001', '心电图');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('aa16ed6cdf2f46499d826fc84ead451e', 911230000000000052, '20234242', '单位体检（女）', 'dwtj（n）', 'FEMALE', 12, 0.0);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('aa16ed6cdf2f46499d826fc84ead451e', 1, 'ZL000102', '肾功三项（贝克曼库尔特DxC700AU）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('aa16ed6cdf2f46499d826fc84ead451e', 2, 'ZL000100', '血脂六项（贝克曼库尔特DxC700AU）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('aa16ed6cdf2f46499d826fc84ead451e', 3, 'ZL000062', '血常规（迪瑞BF-6900）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('aa16ed6cdf2f46499d826fc84ead451e', 4, 'ZL000103', '血糖(贝克曼库尔特DxC700AU)');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('aa16ed6cdf2f46499d826fc84ead451e', 5, 'ZL000065', '乙肝两对半(定量分析)');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('aa16ed6cdf2f46499d826fc84ead451e', 6, 'ZL000101', '肝功十五项(贝克曼库尔特DxC700AU)');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('aa16ed6cdf2f46499d826fc84ead451e', 7, 'ZL000074', '尿常规（迪瑞FUS-1000）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('aa16ed6cdf2f46499d826fc84ead451e', 8, '12030', '肺部CT、颅脑CT（不出片）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('aa16ed6cdf2f46499d826fc84ead451e', 9, '64889', '彩超检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('aa16ed6cdf2f46499d826fc84ead451e', 10, 'jygp1199', '胃幽门螺旋杆菌抗体');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('aa16ed6cdf2f46499d826fc84ead451e', 11, '66011', '妇科彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('aa16ed6cdf2f46499d826fc84ead451e', 12, '66085', '宫颈癌筛查HPV+TCT');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('aa16ed6cdf2f46499d826fc84ead451e', 13, 'ZL000110', '甲功五项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('aa16ed6cdf2f46499d826fc84ead451e', 14, '66165', '糖化血红蛋白');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('aa16ed6cdf2f46499d826fc84ead451e', 15, '10001', '腹部彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('aa16ed6cdf2f46499d826fc84ead451e', 16, '10003', '甲状腺彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('aa16ed6cdf2f46499d826fc84ead451e', 17, '10006', '心脏彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('aa16ed6cdf2f46499d826fc84ead451e', 18, '10008', '乳腺彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('aa16ed6cdf2f46499d826fc84ead451e', 19, '10032', '双侧颈动脉彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('aa16ed6cdf2f46499d826fc84ead451e', 20, '11001', '心电图');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('6c16584e083c41ad99dd874d953ad0c3', 911230000000000053, '20230614', '彩超套餐', 'cctc', 'BOTH', 12, 0.0);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('6c16584e083c41ad99dd874d953ad0c3', 1, '64889', '彩超检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('6c16584e083c41ad99dd874d953ad0c3', 2, '66011', '妇科彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('6c16584e083c41ad99dd874d953ad0c3', 3, '10002', '双侧肾上腺彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('6c16584e083c41ad99dd874d953ad0c3', 4, '10001', '腹部彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('6c16584e083c41ad99dd874d953ad0c3', 5, '10003', '甲状腺彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('6c16584e083c41ad99dd874d953ad0c3', 6, '10004', '泌尿系统彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('6c16584e083c41ad99dd874d953ad0c3', 7, '10006', '心脏彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('6c16584e083c41ad99dd874d953ad0c3', 8, '10008', '乳腺彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('6c16584e083c41ad99dd874d953ad0c3', 9, '10032', '双侧颈动脉彩超');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('cee9006096f14c579cce131b6da72ac6', 911230000000000054, '0001', '体检套餐', 'tjtc', 'BOTH', 13, 0.0);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('cee9006096f14c579cce131b6da72ac6', 1, 'ZL000103', '血糖(贝克曼库尔特DxC700AU)');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('cee9006096f14c579cce131b6da72ac6', 2, 'ZL000102', '肾功三项（贝克曼库尔特DxC700AU）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('cee9006096f14c579cce131b6da72ac6', 3, 'ZL000062', '血常规（迪瑞BF-6900）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('cee9006096f14c579cce131b6da72ac6', 4, 'ZL000101', '肝功十五项(贝克曼库尔特DxC700AU)');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('cee9006096f14c579cce131b6da72ac6', 5, 'ZL000100', '血脂六项（贝克曼库尔特DxC700AU）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('cee9006096f14c579cce131b6da72ac6', 6, 'ZL000074', '尿常规（迪瑞FUS-1000）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('cee9006096f14c579cce131b6da72ac6', 7, '64889', '彩超检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('cee9006096f14c579cce131b6da72ac6', 8, '64983', '肘关节CT（不出片）');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('c157484de5d74139af678a117ccf0aaf', 911230000000000055, '20231218', '个人套餐', 'grtc', 'BOTH', 13, 460.0);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('c157484de5d74139af678a117ccf0aaf', 1, 'ZL000102', '肾功三项（贝克曼库尔特DxC700AU）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('c157484de5d74139af678a117ccf0aaf', 2, 'ZL000062', '血常规（迪瑞BF-6900）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('c157484de5d74139af678a117ccf0aaf', 3, 'ZL000103', '血糖(贝克曼库尔特DxC700AU)');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('c157484de5d74139af678a117ccf0aaf', 4, 'ZL000100', '血脂六项（贝克曼库尔特DxC700AU）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('c157484de5d74139af678a117ccf0aaf', 5, 'ZL000074', '尿常规（迪瑞FUS-1000）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('c157484de5d74139af678a117ccf0aaf', 6, '66280', '眼科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('c157484de5d74139af678a117ccf0aaf', 7, '12030', '肺部CT、颅脑CT（不出片）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('c157484de5d74139af678a117ccf0aaf', 8, '01002', '一般检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('c157484de5d74139af678a117ccf0aaf', 9, '11001', '心电图');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('e84a51686b8c41048d00bd21eb2b08ce', 911230000000000056, '2023821', '社区免费体检', 'sqmftj（lnr）', 'BOTH', 15, 0.0);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('e84a51686b8c41048d00bd21eb2b08ce', 1, 'ZL000062', '血常规（迪瑞BF-6900）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('e84a51686b8c41048d00bd21eb2b08ce', 2, 'jygp0639', '肝功能，肾功能，血脂，血糖');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('e84a51686b8c41048d00bd21eb2b08ce', 3, '64851', '尿常规');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('e84a51686b8c41048d00bd21eb2b08ce', 4, '01002', '一般检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('e84a51686b8c41048d00bd21eb2b08ce', 5, '10011', '消化系统彩超（肝胆胰脾）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('e84a51686b8c41048d00bd21eb2b08ce', 6, '11001', '心电图');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('24dced59850346b8b18f9e2a3cd16a93', 911230000000000057, '202382102', '社区苗费体检（激活）', 'sqmftj（jh）', 'BOTH', 16, 0.0);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('24dced59850346b8b18f9e2a3cd16a93', 1, 'jygp0639', '肝功能，肾功能，血脂，血糖');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('24dced59850346b8b18f9e2a3cd16a93', 2, '64805', '血常规（体检）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('24dced59850346b8b18f9e2a3cd16a93', 3, '64851', '尿常规');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('24dced59850346b8b18f9e2a3cd16a93', 4, '64983', '肘关节CT（不出片）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('24dced59850346b8b18f9e2a3cd16a93', 5, '01002', '一般检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('24dced59850346b8b18f9e2a3cd16a93', 6, '10011', '消化系统彩超（肝胆胰脾）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('24dced59850346b8b18f9e2a3cd16a93', 7, '11001', '心电图');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('d431beed384042039f66bf3a2a0c843f', 911230000000000058, '2024041603', '2024职工体检（女）', '2024zgtj（n）', 'FEMALE', 17, 0.0);
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('9ac82a22139e4f40be13d794edb236ce', 911230000000000059, '20239101', '教育系统体检（女1）', 'jstj（n1）', 'FEMALE', 18, 0.0);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('9ac82a22139e4f40be13d794edb236ce', 1, 'ZL000103', '血糖(贝克曼库尔特DxC700AU)');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('9ac82a22139e4f40be13d794edb236ce', 2, 'ZL000143', 'IV型胶原蛋白');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('9ac82a22139e4f40be13d794edb236ce', 3, 'ZL000062', '血常规（迪瑞BF-6900）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('9ac82a22139e4f40be13d794edb236ce', 4, 'ZL000100', '血脂六项（贝克曼库尔特DxC700AU）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('9ac82a22139e4f40be13d794edb236ce', 5, 'ZL000101', '肝功十五项(贝克曼库尔特DxC700AU)');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('9ac82a22139e4f40be13d794edb236ce', 6, 'ZL000102', '肾功三项（贝克曼库尔特DxC700AU）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('9ac82a22139e4f40be13d794edb236ce', 7, 'ZL000074', '尿常规（迪瑞FUS-1000）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('9ac82a22139e4f40be13d794edb236ce', 8, 'jygp1198', '女性肿瘤标志物五项（贝克曼库尔特DxI 800）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('9ac82a22139e4f40be13d794edb236ce', 9, '64983', '肘关节CT（不出片）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('9ac82a22139e4f40be13d794edb236ce', 10, '66312', '幽门螺旋杆菌分型检测');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('9ac82a22139e4f40be13d794edb236ce', 11, '01002', '一般检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('9ac82a22139e4f40be13d794edb236ce', 12, '05002', '皮肤科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('9ac82a22139e4f40be13d794edb236ce', 13, '06002', '口腔科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('9ac82a22139e4f40be13d794edb236ce', 14, '07001', '中医内科问诊');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('9ac82a22139e4f40be13d794edb236ce', 15, '10003', '甲状腺彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('9ac82a22139e4f40be13d794edb236ce', 16, '10006', '心脏彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('9ac82a22139e4f40be13d794edb236ce', 17, '10008', '乳腺彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('9ac82a22139e4f40be13d794edb236ce', 18, '10032', '双侧颈动脉彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('9ac82a22139e4f40be13d794edb236ce', 19, '11001', '心电图');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('d2aa8659e5f348ec9475839ac390418b', 911230000000000060, '20239102', '教育系统体检（女2）', 'jyxttj（n2）', 'FEMALE', 19, 0.0);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('d2aa8659e5f348ec9475839ac390418b', 1, 'ZL000100', '血脂六项（贝克曼库尔特DxC700AU）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('d2aa8659e5f348ec9475839ac390418b', 2, 'ZL000101', '肝功十五项(贝克曼库尔特DxC700AU)');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('d2aa8659e5f348ec9475839ac390418b', 3, 'ZL000143', 'IV型胶原蛋白');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('d2aa8659e5f348ec9475839ac390418b', 4, 'ZL000062', '血常规（迪瑞BF-6900）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('d2aa8659e5f348ec9475839ac390418b', 5, 'ZL000102', '肾功三项（贝克曼库尔特DxC700AU）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('d2aa8659e5f348ec9475839ac390418b', 6, 'ZL000103', '血糖(贝克曼库尔特DxC700AU)');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('d2aa8659e5f348ec9475839ac390418b', 7, 'ZL000074', '尿常规（迪瑞FUS-1000）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('d2aa8659e5f348ec9475839ac390418b', 8, '64983', '肘关节CT（不出片）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('d2aa8659e5f348ec9475839ac390418b', 9, '66085', '宫颈癌筛查HPV+TCT');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('d2aa8659e5f348ec9475839ac390418b', 10, '66312', '幽门螺旋杆菌分型检测');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('d2aa8659e5f348ec9475839ac390418b', 11, '01002', '一般检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('d2aa8659e5f348ec9475839ac390418b', 12, '05002', '皮肤科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('d2aa8659e5f348ec9475839ac390418b', 13, '06002', '口腔科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('d2aa8659e5f348ec9475839ac390418b', 14, '07001', '中医内科问诊');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('d2aa8659e5f348ec9475839ac390418b', 15, '10003', '甲状腺彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('d2aa8659e5f348ec9475839ac390418b', 16, '10006', '心脏彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('d2aa8659e5f348ec9475839ac390418b', 17, '10008', '乳腺彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('d2aa8659e5f348ec9475839ac390418b', 18, '10032', '双侧颈动脉彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('d2aa8659e5f348ec9475839ac390418b', 19, '11001', '心电图');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('4e8d68ed897b4e52a489840c56f72fbd', 911230000000000061, '202303061', '妇女节  套餐二（宫颈癌）', 'fnj  tce（gja）', 'FEMALE', 22, 0.0);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('4e8d68ed897b4e52a489840c56f72fbd', 1, '66085', '宫颈癌筛查HPV+TCT');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('4e8d68ed897b4e52a489840c56f72fbd', 2, '10001', '腹部彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('4e8d68ed897b4e52a489840c56f72fbd', 3, '10003', '甲状腺彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('4e8d68ed897b4e52a489840c56f72fbd', 4, '10008', '乳腺彩超');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('b30ac74ca5264beaa15240b598d241ee', 911230000000000062, '202303063', '妇女节  套餐二  （性激素）', 'fnj  tce  （xjs）', 'FEMALE', 23, 0.0);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('b30ac74ca5264beaa15240b598d241ee', 1, '10001', '腹部彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('b30ac74ca5264beaa15240b598d241ee', 2, '10003', '甲状腺彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('b30ac74ca5264beaa15240b598d241ee', 3, '10008', '乳腺彩超');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('a132af71f8ce49acbb786cd728b6f92d', 911230000000000063, '202303068', '妇女节   套餐四', 'fnj   tcs', 'BOTH', 24, 0.0);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('a132af71f8ce49acbb786cd728b6f92d', 1, '10001', '腹部彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('a132af71f8ce49acbb786cd728b6f92d', 2, '10003', '甲状腺彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('a132af71f8ce49acbb786cd728b6f92d', 3, '10032', '双侧颈动脉彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('a132af71f8ce49acbb786cd728b6f92d', 4, '12019', '颅脑CT（不出片）');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('bb94606a318749d6b81ae1204d312896', 911230000000000064, '20230306', '妇女节  套餐三', 'fnj  tcs', 'BOTH', 25, 1.0);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('bb94606a318749d6b81ae1204d312896', 1, 'ZL000062', '血常规（迪瑞BF-6900）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('bb94606a318749d6b81ae1204d312896', 2, '12030', '肺部CT、颅脑CT（不出片）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('bb94606a318749d6b81ae1204d312896', 3, '66087', '心肌酶');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('bb94606a318749d6b81ae1204d312896', 4, '11001', '心电图');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('d2b4327a4fec417a8dcea201f8ef9137', 911230000000000065, '202303062', '妇女节 套餐二  （女肿瘤标志物）', 'fnj tce  （nzlbzw）', 'FEMALE', 26, 0.0);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('d2b4327a4fec417a8dcea201f8ef9137', 1, 'jygp1198', '女性肿瘤标志物五项（贝克曼库尔特DxI 800）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('d2b4327a4fec417a8dcea201f8ef9137', 2, '10001', '腹部彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('d2b4327a4fec417a8dcea201f8ef9137', 3, '10003', '甲状腺彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('d2b4327a4fec417a8dcea201f8ef9137', 4, '10008', '乳腺彩超');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('ed681fed24064265abe5dcd5f91af662', 911230000000000066, '202331301', '妇女节套餐四（DR）', 'fnjtcs（DR）', 'BOTH', 27, 0.0);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('ed681fed24064265abe5dcd5f91af662', 1, '10003', '甲状腺彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('ed681fed24064265abe5dcd5f91af662', 2, '12019', '颅脑CT（不出片）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('ed681fed24064265abe5dcd5f91af662', 3, '1100115', '颈椎双斜位DR');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('e10191e2855345ea84dba5124754812e', 911230000000000067, '20240411', '妇保套餐', 'fbtc', 'FEMALE', 31, 0.0);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('e10191e2855345ea84dba5124754812e', 1, '68105', '抗人类免疫缺陷病毒抗体（HIV）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('e10191e2855345ea84dba5124754812e', 2, 'jygp0115', '乙肝两对半（胶体金法）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('e10191e2855345ea84dba5124754812e', 3, 'ZL000072', '血型');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('e10191e2855345ea84dba5124754812e', 4, '66282', '肝功能');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('e10191e2855345ea84dba5124754812e', 5, '64805', '血常规（体检）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('e10191e2855345ea84dba5124754812e', 6, '64851', '尿常规');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('e10191e2855345ea84dba5124754812e', 7, '68106', '梅毒螺旋体抗体');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('e10191e2855345ea84dba5124754812e', 8, 'ZL000068', '肾功三项');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('caad2a0befd9474f894619da4c05ae72', 911230000000000068, '2024041115', '儿保套餐', 'ebtc', 'BOTH', 33, 0.0);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('caad2a0befd9474f894619da4c05ae72', 1, '64805', '血常规（体检）');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('ee4ac31649dc4c3b8a7a4f93d310de0f', 911230000000000069, '2210281', '区政府体检（男）', 'qzftj（n）', 'MALE', 36, 0.0);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('ee4ac31649dc4c3b8a7a4f93d310de0f', 1, 'ZL000101', '肝功十五项(贝克曼库尔特DxC700AU)');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('ee4ac31649dc4c3b8a7a4f93d310de0f', 2, 'ZL000102', '肾功三项（贝克曼库尔特DxC700AU）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('ee4ac31649dc4c3b8a7a4f93d310de0f', 3, 'ZL000103', '血糖(贝克曼库尔特DxC700AU)');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('ee4ac31649dc4c3b8a7a4f93d310de0f', 4, 'ZL000100', '血脂六项（贝克曼库尔特DxC700AU）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('ee4ac31649dc4c3b8a7a4f93d310de0f', 5, 'ZL000062', '血常规（迪瑞BF-6900）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('ee4ac31649dc4c3b8a7a4f93d310de0f', 6, 'ZL000074', '尿常规（迪瑞FUS-1000）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('ee4ac31649dc4c3b8a7a4f93d310de0f', 7, 'jygp1195', '抗链球菌溶血素O测定（贝克曼库尔特DxC700AU）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('ee4ac31649dc4c3b8a7a4f93d310de0f', 8, 'jygp1196', '类风湿因子（贝克曼库尔特DxC700AU）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('ee4ac31649dc4c3b8a7a4f93d310de0f', 9, 'jygp1197', '男性肿瘤标志物五项（贝克曼库尔特DxI 800）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('ee4ac31649dc4c3b8a7a4f93d310de0f', 10, 'jygp1199', '胃幽门螺旋杆菌抗体');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('ee4ac31649dc4c3b8a7a4f93d310de0f', 11, '64983', '肘关节CT（不出片）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('ee4ac31649dc4c3b8a7a4f93d310de0f', 12, '01002', '一般检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('ee4ac31649dc4c3b8a7a4f93d310de0f', 13, '06002', '口腔科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('ee4ac31649dc4c3b8a7a4f93d310de0f', 14, '07001', '中医内科问诊');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('ee4ac31649dc4c3b8a7a4f93d310de0f', 15, '10003', '甲状腺彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('ee4ac31649dc4c3b8a7a4f93d310de0f', 16, '10004', '泌尿系统彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('ee4ac31649dc4c3b8a7a4f93d310de0f', 17, '10006', '心脏彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('ee4ac31649dc4c3b8a7a4f93d310de0f', 18, '10032', '双侧颈动脉彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('ee4ac31649dc4c3b8a7a4f93d310de0f', 19, '11001', '心电图');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('d570f18dfcdb49a49179c2bdee40ae31', 911230000000000070, '2210282', '区政府体检（女）', 'qzftj（n）', 'FEMALE', 37, 0.0);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('d570f18dfcdb49a49179c2bdee40ae31', 1, 'ZL000101', '肝功十五项(贝克曼库尔特DxC700AU)');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('d570f18dfcdb49a49179c2bdee40ae31', 2, 'ZL000100', '血脂六项（贝克曼库尔特DxC700AU）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('d570f18dfcdb49a49179c2bdee40ae31', 3, 'ZL000102', '肾功三项（贝克曼库尔特DxC700AU）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('d570f18dfcdb49a49179c2bdee40ae31', 4, 'ZL000062', '血常规（迪瑞BF-6900）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('d570f18dfcdb49a49179c2bdee40ae31', 5, 'ZL000103', '血糖(贝克曼库尔特DxC700AU)');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('d570f18dfcdb49a49179c2bdee40ae31', 6, 'ZL000074', '尿常规（迪瑞FUS-1000）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('d570f18dfcdb49a49179c2bdee40ae31', 7, 'jygp1195', '抗链球菌溶血素O测定（贝克曼库尔特DxC700AU）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('d570f18dfcdb49a49179c2bdee40ae31', 8, 'jygp1196', '类风湿因子（贝克曼库尔特DxC700AU）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('d570f18dfcdb49a49179c2bdee40ae31', 9, 'jygp1198', '女性肿瘤标志物五项（贝克曼库尔特DxI 800）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('d570f18dfcdb49a49179c2bdee40ae31', 10, 'jygp1199', '胃幽门螺旋杆菌抗体');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('d570f18dfcdb49a49179c2bdee40ae31', 11, '64983', '肘关节CT（不出片）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('d570f18dfcdb49a49179c2bdee40ae31', 12, '01002', '一般检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('d570f18dfcdb49a49179c2bdee40ae31', 13, '06002', '口腔科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('d570f18dfcdb49a49179c2bdee40ae31', 14, '07001', '中医内科问诊');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('d570f18dfcdb49a49179c2bdee40ae31', 15, '10003', '甲状腺彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('d570f18dfcdb49a49179c2bdee40ae31', 16, '10006', '心脏彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('d570f18dfcdb49a49179c2bdee40ae31', 17, '10008', '乳腺彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('d570f18dfcdb49a49179c2bdee40ae31', 18, '10032', '双侧颈动脉彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('d570f18dfcdb49a49179c2bdee40ae31', 19, '11001', '心电图');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('aef47cad28194b7190d69ec435dbf8f5', 911230000000000071, '20240702', '体检套餐2', 'tjtc2', 'BOTH', 44, 556.74);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('aef47cad28194b7190d69ec435dbf8f5', 1, '12030', '肺部CT、颅脑CT（不出片）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('aef47cad28194b7190d69ec435dbf8f5', 2, '10003', '甲状腺彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('aef47cad28194b7190d69ec435dbf8f5', 3, '10008', '乳腺彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('aef47cad28194b7190d69ec435dbf8f5', 4, '10032', '双侧颈动脉彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('aef47cad28194b7190d69ec435dbf8f5', 5, '12019', '颅脑CT（不出片）');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('422b4af9220c4b6985ac4f649ceb5d92', 911230000000000072, '20240710', '戒烟活动套餐', 'jyhdtc', 'BOTH', 46, 130.0);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('422b4af9220c4b6985ac4f649ceb5d92', 1, '68096', '胸正位 DR');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('422b4af9220c4b6985ac4f649ceb5d92', 2, '11004', '肺功能检测');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('be8a913d098c4288b73374645f025cb3', 911230000000000073, '20241228', '健康体检（女）', 'jktj（n）', 'FEMALE', 52, 0.0);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('be8a913d098c4288b73374645f025cb3', 1, 'ZL000062', '血常规（迪瑞BF-6900）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('be8a913d098c4288b73374645f025cb3', 2, 'ZL000066', '肝功十五项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('be8a913d098c4288b73374645f025cb3', 3, 'ZL000074', '尿常规（迪瑞FUS-1000）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('be8a913d098c4288b73374645f025cb3', 4, '68022', '阴道彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('be8a913d098c4288b73374645f025cb3', 5, 'ZL000069', '血糖');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('be8a913d098c4288b73374645f025cb3', 6, '12030', '肺部CT、颅脑CT（不出片）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('be8a913d098c4288b73374645f025cb3', 7, 'jygp1198', '女性肿瘤标志物五项（贝克曼库尔特DxI 800）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('be8a913d098c4288b73374645f025cb3', 8, 'ZL000110', '甲功五项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('be8a913d098c4288b73374645f025cb3', 9, 'ZL000071', '血脂六项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('be8a913d098c4288b73374645f025cb3', 10, 'ZL000068', '肾功三项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('be8a913d098c4288b73374645f025cb3', 11, '10001', '腹部彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('be8a913d098c4288b73374645f025cb3', 12, '10003', '甲状腺彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('be8a913d098c4288b73374645f025cb3', 13, '10006', '心脏彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('be8a913d098c4288b73374645f025cb3', 14, '10008', '乳腺彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('be8a913d098c4288b73374645f025cb3', 15, '10032', '双侧颈动脉彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('be8a913d098c4288b73374645f025cb3', 16, '11001', '心电图');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('be8a913d098c4288b73374645f025cb3', 17, '12019', '颅脑CT（不出片）');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('8b1c35cc87ed44d09470752203e8181d', 911230000000000074, '2024122801', '健康体检（男）', 'jktj（n）', 'BOTH', 53, 0.0);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('8b1c35cc87ed44d09470752203e8181d', 1, 'ZL000066', '肝功十五项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('8b1c35cc87ed44d09470752203e8181d', 2, 'ZL000062', '血常规（迪瑞BF-6900）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('8b1c35cc87ed44d09470752203e8181d', 3, 'ZL000074', '尿常规（迪瑞FUS-1000）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('8b1c35cc87ed44d09470752203e8181d', 4, 'ZL000069', '血糖');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('8b1c35cc87ed44d09470752203e8181d', 5, '12030', '肺部CT、颅脑CT（不出片）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('8b1c35cc87ed44d09470752203e8181d', 6, 'jygp1197', '男性肿瘤标志物五项（贝克曼库尔特DxI 800）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('8b1c35cc87ed44d09470752203e8181d', 7, 'ZL000110', '甲功五项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('8b1c35cc87ed44d09470752203e8181d', 8, 'ZL000071', '血脂六项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('8b1c35cc87ed44d09470752203e8181d', 9, 'ZL000068', '肾功三项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('8b1c35cc87ed44d09470752203e8181d', 10, '10001', '腹部彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('8b1c35cc87ed44d09470752203e8181d', 11, '10003', '甲状腺彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('8b1c35cc87ed44d09470752203e8181d', 12, '10004', '泌尿系统彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('8b1c35cc87ed44d09470752203e8181d', 13, '10006', '心脏彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('8b1c35cc87ed44d09470752203e8181d', 14, '10032', '双侧颈动脉彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('8b1c35cc87ed44d09470752203e8181d', 15, '11001', '心电图');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('8b1c35cc87ed44d09470752203e8181d', 16, '12019', '颅脑CT（不出片）');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('4211757b9cb24d6cb2d9c1e39c5a40d0', 911230000000000075, '25112402', '2025年社工部（女已婚）', '2025nsgb（nyh）', 'FEMALE', 62, 0.0);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('4211757b9cb24d6cb2d9c1e39c5a40d0', 1, 'ZL000066', '肝功十五项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('4211757b9cb24d6cb2d9c1e39c5a40d0', 2, 'jygp0115', '乙肝两对半（胶体金法）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('4211757b9cb24d6cb2d9c1e39c5a40d0', 3, 'ZL000062', '血常规（迪瑞BF-6900）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('4211757b9cb24d6cb2d9c1e39c5a40d0', 4, 'ZL000074', '尿常规（迪瑞FUS-1000）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('4211757b9cb24d6cb2d9c1e39c5a40d0', 5, 'ZL000069', '血糖');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('4211757b9cb24d6cb2d9c1e39c5a40d0', 6, '12030', '肺部CT、颅脑CT（不出片）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('4211757b9cb24d6cb2d9c1e39c5a40d0', 7, '66011', '妇科彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('4211757b9cb24d6cb2d9c1e39c5a40d0', 8, '71447', 'C14呼气试验');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('4211757b9cb24d6cb2d9c1e39c5a40d0', 9, '75059', '液基细胞');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('4211757b9cb24d6cb2d9c1e39c5a40d0', 10, 'ZL000071', '血脂六项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('4211757b9cb24d6cb2d9c1e39c5a40d0', 11, 'ZL000068', '肾功三项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('4211757b9cb24d6cb2d9c1e39c5a40d0', 12, '09001', '妇科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('4211757b9cb24d6cb2d9c1e39c5a40d0', 13, '10001', '腹部彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('4211757b9cb24d6cb2d9c1e39c5a40d0', 14, '10003', '甲状腺彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('4211757b9cb24d6cb2d9c1e39c5a40d0', 15, '10008', '乳腺彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('4211757b9cb24d6cb2d9c1e39c5a40d0', 16, '11001', '心电图');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('4211757b9cb24d6cb2d9c1e39c5a40d0', 17, '11005', '骨密度检查');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('637f58f9eea94d459609115962179369', 911230000000000076, '25112403', '2025年社工部（女未婚）', '2025nsgb（nwh）', 'FEMALE', 63, 0.0);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('637f58f9eea94d459609115962179369', 1, 'ZL000062', '血常规（迪瑞BF-6900）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('637f58f9eea94d459609115962179369', 2, 'ZL000066', '肝功十五项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('637f58f9eea94d459609115962179369', 3, 'jygp0115', '乙肝两对半（胶体金法）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('637f58f9eea94d459609115962179369', 4, 'ZL000074', '尿常规（迪瑞FUS-1000）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('637f58f9eea94d459609115962179369', 5, 'ZL000069', '血糖');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('637f58f9eea94d459609115962179369', 6, '12030', '肺部CT、颅脑CT（不出片）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('637f58f9eea94d459609115962179369', 7, '66011', '妇科彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('637f58f9eea94d459609115962179369', 8, '71447', 'C14呼气试验');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('637f58f9eea94d459609115962179369', 9, 'ZL000071', '血脂六项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('637f58f9eea94d459609115962179369', 10, 'ZL000068', '肾功三项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('637f58f9eea94d459609115962179369', 11, '10001', '腹部彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('637f58f9eea94d459609115962179369', 12, '10003', '甲状腺彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('637f58f9eea94d459609115962179369', 13, '10008', '乳腺彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('637f58f9eea94d459609115962179369', 14, '11001', '心电图');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('637f58f9eea94d459609115962179369', 15, '11005', '骨密度检查');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('2a86a6a97f9b48dbacc11d22561ee279', 911230000000000077, '2025121502', '健康体检套餐（女）', 'jktjtc（n）', 'FEMALE', 65, 0.0);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('2a86a6a97f9b48dbacc11d22561ee279', 1, 'ZL000062', '血常规（迪瑞BF-6900）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('2a86a6a97f9b48dbacc11d22561ee279', 2, 'ZL000066', '肝功十五项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('2a86a6a97f9b48dbacc11d22561ee279', 3, 'ZL000074', '尿常规（迪瑞FUS-1000）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('2a86a6a97f9b48dbacc11d22561ee279', 4, '66278', '外科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('2a86a6a97f9b48dbacc11d22561ee279', 5, 'ZL000069', '血糖');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('2a86a6a97f9b48dbacc11d22561ee279', 6, '12030', '肺部CT、颅脑CT（不出片）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('2a86a6a97f9b48dbacc11d22561ee279', 7, '66011', '妇科彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('2a86a6a97f9b48dbacc11d22561ee279', 8, '66165', '糖化血红蛋白');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('2a86a6a97f9b48dbacc11d22561ee279', 9, '71447', 'C14呼气试验');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('2a86a6a97f9b48dbacc11d22561ee279', 10, '72016', '糖类抗原CA15-3');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('2a86a6a97f9b48dbacc11d22561ee279', 11, '72555', '耳穴压丸');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('2a86a6a97f9b48dbacc11d22561ee279', 12, 'ZL000071', '血脂六项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('2a86a6a97f9b48dbacc11d22561ee279', 13, 'ZL000068', '肾功三项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('2a86a6a97f9b48dbacc11d22561ee279', 14, '01002', '一般检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('2a86a6a97f9b48dbacc11d22561ee279', 15, '05002', '皮肤科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('2a86a6a97f9b48dbacc11d22561ee279', 16, '06002', '口腔科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('2a86a6a97f9b48dbacc11d22561ee279', 17, '07001', '中医内科问诊');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('2a86a6a97f9b48dbacc11d22561ee279', 18, '09001', '妇科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('2a86a6a97f9b48dbacc11d22561ee279', 19, '10001', '腹部彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('2a86a6a97f9b48dbacc11d22561ee279', 20, '10003', '甲状腺彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('2a86a6a97f9b48dbacc11d22561ee279', 21, '10008', '乳腺彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('2a86a6a97f9b48dbacc11d22561ee279', 22, '11001', '心电图');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('2a86a6a97f9b48dbacc11d22561ee279', 23, '11005', '骨密度检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('2a86a6a97f9b48dbacc11d22561ee279', 24, '12019', '颅脑CT（不出片）');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('dad59f3e22ac434b9ffd3739e23f15c6', 911230000000000078, '202601', '2026肿瘤风险筛查套餐（女）', 'zlfxsctc', 'BOTH', 68, 1082.7);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('dad59f3e22ac434b9ffd3739e23f15c6', 1, 'ZL000062', '血常规（迪瑞BF-6900）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('dad59f3e22ac434b9ffd3739e23f15c6', 2, 'ZL000066', '肝功十五项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('dad59f3e22ac434b9ffd3739e23f15c6', 3, 'ZL000074', '尿常规（迪瑞FUS-1000）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('dad59f3e22ac434b9ffd3739e23f15c6', 4, 'ZL000069', '血糖');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('dad59f3e22ac434b9ffd3739e23f15c6', 5, '12030', '肺部CT、颅脑CT（不出片）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('dad59f3e22ac434b9ffd3739e23f15c6', 6, 'jygp1198', '女性肿瘤标志物五项（贝克曼库尔特DxI 800）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('dad59f3e22ac434b9ffd3739e23f15c6', 7, '71447', 'C14呼气试验');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('dad59f3e22ac434b9ffd3739e23f15c6', 8, 'ZL000071', '血脂六项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('dad59f3e22ac434b9ffd3739e23f15c6', 9, 'ZL000068', '肾功三项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('dad59f3e22ac434b9ffd3739e23f15c6', 10, '01002', '一般检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('dad59f3e22ac434b9ffd3739e23f15c6', 11, '10001', '腹部彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('dad59f3e22ac434b9ffd3739e23f15c6', 12, '10003', '甲状腺彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('dad59f3e22ac434b9ffd3739e23f15c6', 13, '10004', '泌尿系统彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('dad59f3e22ac434b9ffd3739e23f15c6', 14, '11001', '心电图');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('e0ff2a9883ff4eb5b7adeccb340d1a4a', 911230000000000079, '202602', '2026心脑血管筛查套餐', '2026xnxgsctc', 'BOTH', 69, 1201.4);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('e0ff2a9883ff4eb5b7adeccb340d1a4a', 1, 'ZL000062', '血常规（迪瑞BF-6900）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('e0ff2a9883ff4eb5b7adeccb340d1a4a', 2, 'ZL000066', '肝功十五项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('e0ff2a9883ff4eb5b7adeccb340d1a4a', 3, 'ZL000074', '尿常规（迪瑞FUS-1000）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('e0ff2a9883ff4eb5b7adeccb340d1a4a', 4, 'ZL000069', '血糖');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('e0ff2a9883ff4eb5b7adeccb340d1a4a', 5, '12030', '肺部CT、颅脑CT（不出片）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('e0ff2a9883ff4eb5b7adeccb340d1a4a', 6, 'ZL000071', '血脂六项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('e0ff2a9883ff4eb5b7adeccb340d1a4a', 7, 'ZL000068', '肾功三项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('e0ff2a9883ff4eb5b7adeccb340d1a4a', 8, '01002', '一般检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('e0ff2a9883ff4eb5b7adeccb340d1a4a', 9, '10001', '腹部彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('e0ff2a9883ff4eb5b7adeccb340d1a4a', 10, '10003', '甲状腺彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('e0ff2a9883ff4eb5b7adeccb340d1a4a', 11, '10006', '心脏彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('e0ff2a9883ff4eb5b7adeccb340d1a4a', 12, '10032', '双侧颈动脉彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('e0ff2a9883ff4eb5b7adeccb340d1a4a', 13, '11001', '心电图');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('e0ff2a9883ff4eb5b7adeccb340d1a4a', 14, '12019', '颅脑CT（不出片）');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('8c7d8f64d74b44a8b662eda2439fe47f', 911230000000000080, '20260417_BOTH', '20260417（男）', '20260417（n）', 'BOTH', 74, 1543.4);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('8c7d8f64d74b44a8b662eda2439fe47f', 1, 'ZL000062', '血常规（迪瑞BF-6900）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('8c7d8f64d74b44a8b662eda2439fe47f', 2, 'ZL000066', '肝功十五项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('8c7d8f64d74b44a8b662eda2439fe47f', 3, 'ZL000074', '尿常规（迪瑞FUS-1000）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('8c7d8f64d74b44a8b662eda2439fe47f', 4, 'ZL000069', '血糖');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('8c7d8f64d74b44a8b662eda2439fe47f', 5, '12030', '肺部CT、颅脑CT（不出片）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('8c7d8f64d74b44a8b662eda2439fe47f', 6, 'jygp1197', '男性肿瘤标志物五项（贝克曼库尔特DxI 800）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('8c7d8f64d74b44a8b662eda2439fe47f', 7, 'ZL000071', '血脂六项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('8c7d8f64d74b44a8b662eda2439fe47f', 8, 'ZL000068', '肾功三项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('8c7d8f64d74b44a8b662eda2439fe47f', 9, '01002', '一般检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('8c7d8f64d74b44a8b662eda2439fe47f', 10, '10001', '腹部彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('8c7d8f64d74b44a8b662eda2439fe47f', 11, '10003', '甲状腺彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('8c7d8f64d74b44a8b662eda2439fe47f', 12, '10006', '心脏彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('8c7d8f64d74b44a8b662eda2439fe47f', 13, '10032', '双侧颈动脉彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('8c7d8f64d74b44a8b662eda2439fe47f', 14, '11001', '心电图');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('8c7d8f64d74b44a8b662eda2439fe47f', 15, '12019', '颅脑CT（不出片）');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('8bea284569c246c99f3a8cb1e980a809', 911230000000000081, '20260417_BOTH_8bea2845', '20260417（女）', '20260417（n）', 'BOTH', 75, 1637.1);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('8bea284569c246c99f3a8cb1e980a809', 1, 'ZL000062', '血常规（迪瑞BF-6900）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('8bea284569c246c99f3a8cb1e980a809', 2, 'ZL000066', '肝功十五项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('8bea284569c246c99f3a8cb1e980a809', 3, 'ZL000074', '尿常规（迪瑞FUS-1000）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('8bea284569c246c99f3a8cb1e980a809', 4, 'ZL000069', '血糖');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('8bea284569c246c99f3a8cb1e980a809', 5, '12030', '肺部CT、颅脑CT（不出片）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('8bea284569c246c99f3a8cb1e980a809', 6, 'jygp1198', '女性肿瘤标志物五项（贝克曼库尔特DxI 800）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('8bea284569c246c99f3a8cb1e980a809', 7, '66011', '妇科彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('8bea284569c246c99f3a8cb1e980a809', 8, 'ZL000071', '血脂六项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('8bea284569c246c99f3a8cb1e980a809', 9, 'ZL000068', '肾功三项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('8bea284569c246c99f3a8cb1e980a809', 10, '01002', '一般检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('8bea284569c246c99f3a8cb1e980a809', 11, '10001', '腹部彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('8bea284569c246c99f3a8cb1e980a809', 12, '10003', '甲状腺彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('8bea284569c246c99f3a8cb1e980a809', 13, '10006', '心脏彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('8bea284569c246c99f3a8cb1e980a809', 14, '10032', '双侧颈动脉彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('8bea284569c246c99f3a8cb1e980a809', 15, '11001', '心电图');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('8bea284569c246c99f3a8cb1e980a809', 16, '12019', '颅脑CT（不出片）');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('aec001f6db2f40d3a4157c6abbc10fa0', 911230000000000082, '20260512', '2026医疗废物专职管理人员体检套餐', '2026ylfwzzglrytjtc', 'BOTH', 76, 0.0);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('aec001f6db2f40d3a4157c6abbc10fa0', 1, '68096', '胸正位 DR');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('aec001f6db2f40d3a4157c6abbc10fa0', 2, 'ZL000121', '肝功十三项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('aec001f6db2f40d3a4157c6abbc10fa0', 3, 'ZL000062', '血常规（迪瑞BF-6900）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('aec001f6db2f40d3a4157c6abbc10fa0', 4, '76067', 'HIV抗体');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('aec001f6db2f40d3a4157c6abbc10fa0', 5, 'jygp0115', '乙肝两对半（胶体金法）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('aec001f6db2f40d3a4157c6abbc10fa0', 6, 'ZL000074', '尿常规（迪瑞FUS-1000）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('aec001f6db2f40d3a4157c6abbc10fa0', 7, '66553', '甲功三项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('aec001f6db2f40d3a4157c6abbc10fa0', 8, 'ZL000068', '肾功三项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('aec001f6db2f40d3a4157c6abbc10fa0', 9, '11001', '心电图');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('50758414041e423cb7207ef17e66d380', 911230000000000083, '20260519', '医疗废物专职管理人员体检套餐', 'ylfwzzglrytjtc', 'BOTH', 77, 0.0);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('50758414041e423cb7207ef17e66d380', 1, '68096', '胸正位 DR');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('50758414041e423cb7207ef17e66d380', 2, 'ZL000070', '血脂四项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('50758414041e423cb7207ef17e66d380', 3, '76067', 'HIV抗体');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('50758414041e423cb7207ef17e66d380', 4, 'ZL000121', '肝功十三项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('50758414041e423cb7207ef17e66d380', 5, 'ZL000062', '血常规（迪瑞BF-6900）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('50758414041e423cb7207ef17e66d380', 6, '76066', '梅毒抗体');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('50758414041e423cb7207ef17e66d380', 7, 'jygp0115', '乙肝两对半（胶体金法）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('50758414041e423cb7207ef17e66d380', 8, 'ZL000074', '尿常规（迪瑞FUS-1000）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('50758414041e423cb7207ef17e66d380', 9, 'ZL000069', '血糖');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('50758414041e423cb7207ef17e66d380', 10, '66553', '甲功三项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('50758414041e423cb7207ef17e66d380', 11, '76065', '丙肝抗体');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('50758414041e423cb7207ef17e66d380', 12, 'ZL000068', '肾功三项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('50758414041e423cb7207ef17e66d380', 13, '11001', '心电图');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('373adb80fac546a18e64d31bdf3ded5b', 911230000000000084, '260626', '2026常规体检套餐', '2026cgtjtc', 'BOTH', 79, 102.7);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('373adb80fac546a18e64d31bdf3ded5b', 1, 'ZL000062', '血常规（迪瑞BF-6900）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('373adb80fac546a18e64d31bdf3ded5b', 2, 'ZL000066', '肝功十五项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('373adb80fac546a18e64d31bdf3ded5b', 3, 'ZL000074', '尿常规（迪瑞FUS-1000）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('373adb80fac546a18e64d31bdf3ded5b', 4, 'ZL000069', '血糖');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('373adb80fac546a18e64d31bdf3ded5b', 5, '12030', '肺部CT、颅脑CT（不出片）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('373adb80fac546a18e64d31bdf3ded5b', 6, 'ZL000071', '血脂六项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('373adb80fac546a18e64d31bdf3ded5b', 7, 'ZL000068', '肾功三项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('373adb80fac546a18e64d31bdf3ded5b', 8, '10001', '腹部彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('373adb80fac546a18e64d31bdf3ded5b', 9, '11001', '心电图');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('373adb80fac546a18e64d31bdf3ded5b', 10, '12019', '颅脑CT（不出片）');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('4729d7155441481c9977e9b09bec4ad0', 911230000000000085, '2026070801', '2026政府体检（男）', '2026zftj（n）', 'BOTH', 82, 0.0);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('4729d7155441481c9977e9b09bec4ad0', 1, 'ZL000062', '血常规（迪瑞BF-6900）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('4729d7155441481c9977e9b09bec4ad0', 2, 'ZL000066', '肝功十五项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('4729d7155441481c9977e9b09bec4ad0', 3, 'ZL000074', '尿常规（迪瑞FUS-1000）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('4729d7155441481c9977e9b09bec4ad0', 4, '66278', '外科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('4729d7155441481c9977e9b09bec4ad0', 5, 'ZL000069', '血糖');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('4729d7155441481c9977e9b09bec4ad0', 6, '12030', '肺部CT、颅脑CT（不出片）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('4729d7155441481c9977e9b09bec4ad0', 7, '66165', '糖化血红蛋白');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('4729d7155441481c9977e9b09bec4ad0', 8, '72555', '耳穴压丸');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('4729d7155441481c9977e9b09bec4ad0', 9, 'ZL000071', '血脂六项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('4729d7155441481c9977e9b09bec4ad0', 10, 'ZL000068', '肾功三项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('4729d7155441481c9977e9b09bec4ad0', 11, '01002', '一般检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('4729d7155441481c9977e9b09bec4ad0', 12, '05002', '皮肤科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('4729d7155441481c9977e9b09bec4ad0', 13, '06002', '口腔科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('4729d7155441481c9977e9b09bec4ad0', 14, '07001', '中医内科问诊');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('4729d7155441481c9977e9b09bec4ad0', 15, '10003', '甲状腺彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('4729d7155441481c9977e9b09bec4ad0', 16, '10004', '泌尿系统彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('4729d7155441481c9977e9b09bec4ad0', 17, '10011', '消化系统彩超（肝胆胰脾）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('4729d7155441481c9977e9b09bec4ad0', 18, '10032', '双侧颈动脉彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('4729d7155441481c9977e9b09bec4ad0', 19, '11001', '心电图');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('4729d7155441481c9977e9b09bec4ad0', 20, '11005', '骨密度检查');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('a8a0d66fc39b4d0e82a76356f642615b', 911230000000000086, '2026070802', '2026政府体检（女）', '2026zftj（n）', 'BOTH', 83, 0.0);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('a8a0d66fc39b4d0e82a76356f642615b', 1, 'ZL000062', '血常规（迪瑞BF-6900）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('a8a0d66fc39b4d0e82a76356f642615b', 2, 'ZL000066', '肝功十五项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('a8a0d66fc39b4d0e82a76356f642615b', 3, 'ZL000074', '尿常规（迪瑞FUS-1000）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('a8a0d66fc39b4d0e82a76356f642615b', 4, '66278', '外科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('a8a0d66fc39b4d0e82a76356f642615b', 5, 'ZL000069', '血糖');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('a8a0d66fc39b4d0e82a76356f642615b', 6, '12030', '肺部CT、颅脑CT（不出片）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('a8a0d66fc39b4d0e82a76356f642615b', 7, '66011', '妇科彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('a8a0d66fc39b4d0e82a76356f642615b', 8, '72555', '耳穴压丸');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('a8a0d66fc39b4d0e82a76356f642615b', 9, 'ZL000071', '血脂六项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('a8a0d66fc39b4d0e82a76356f642615b', 10, 'ZL000068', '肾功三项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('a8a0d66fc39b4d0e82a76356f642615b', 11, '01002', '一般检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('a8a0d66fc39b4d0e82a76356f642615b', 12, '05002', '皮肤科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('a8a0d66fc39b4d0e82a76356f642615b', 13, '06002', '口腔科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('a8a0d66fc39b4d0e82a76356f642615b', 14, '07001', '中医内科问诊');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('a8a0d66fc39b4d0e82a76356f642615b', 15, '09001', '妇科检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('a8a0d66fc39b4d0e82a76356f642615b', 16, '10001', '腹部彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('a8a0d66fc39b4d0e82a76356f642615b', 17, '10003', '甲状腺彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('a8a0d66fc39b4d0e82a76356f642615b', 18, '10008', '乳腺彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('a8a0d66fc39b4d0e82a76356f642615b', 19, '11001', '心电图');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('a8a0d66fc39b4d0e82a76356f642615b', 20, '11005', '骨密度检查');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('edf34fa97e4848569a58170990718564', 911230000000000087, '2026070803', '2026政府体检无痛胃肠镜', '2026zftjwcj', 'BOTH', 85, 0.0);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('edf34fa97e4848569a58170990718564', 1, '71256', '胃肠镜检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('edf34fa97e4848569a58170990718564', 2, '11001', '心电图');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('f3f4218697384d8a93671538a5532d19', 911230000000000088, '20260812', '2026学生运动员体检', '2026xsydytj', 'BOTH', 86, 20.0);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('f3f4218697384d8a93671538a5532d19', 1, '71897', '血压');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('f3f4218697384d8a93671538a5532d19', 2, '11001', '心电图');
INSERT INTO tmp_peis_legacy_package (legacy_id, intended_activity_id, bus_no, display, py_str, permitted_gender_enum, sort_order, sale_price) VALUES ('7459c395a94a4c03b09744542044f79e', 911230000000000089, '2026070804', '2026镇政府体检心血管', '2026zzftjxxg', 'BOTH', 88, 0.0);
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('7459c395a94a4c03b09744542044f79e', 1, 'ZL000066', '肝功十五项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('7459c395a94a4c03b09744542044f79e', 2, 'ZL000062', '血常规（迪瑞BF-6900）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('7459c395a94a4c03b09744542044f79e', 3, 'ZL000074', '尿常规（迪瑞FUS-1000）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('7459c395a94a4c03b09744542044f79e', 4, 'ZL000069', '血糖');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('7459c395a94a4c03b09744542044f79e', 5, '12030', '肺部CT、颅脑CT（不出片）');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('7459c395a94a4c03b09744542044f79e', 6, 'ZL000071', '血脂六项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('7459c395a94a4c03b09744542044f79e', 7, 'ZL000068', '肾功三项');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('7459c395a94a4c03b09744542044f79e', 8, '01002', '一般检查');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('7459c395a94a4c03b09744542044f79e', 9, '10001', '腹部彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('7459c395a94a4c03b09744542044f79e', 10, '10003', '甲状腺彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('7459c395a94a4c03b09744542044f79e', 11, '10006', '心脏彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('7459c395a94a4c03b09744542044f79e', 12, '10032', '双侧颈动脉彩超');
INSERT INTO tmp_peis_legacy_package_item (package_legacy_id, sort_num, child_bus_no, child_name) VALUES ('7459c395a94a4c03b09744542044f79e', 13, '11001', '心电图');

-- ========== 套餐 AD：按 bus_no 幂等 ==========
INSERT INTO wkf_activity_definition (
    id, tenant_id, created_at, updated_at, created_by, updated_by,
    is_deleted, version, category_code, bus_no, name, py_str,
    type_enum, status_enum, org_id, children_flag, pricing_flag,
    permitted_gender_enum, sort_order
)
SELECT
    t.intended_activity_id, 1, NOW(), NOW(), 1, 1,
    FALSE, 0, 'PEIS', t.bus_no, t.display, t.py_str,
    '5', 'ACTIVE', NULL, TRUE,
    CASE WHEN COALESCE(t.sale_price, 0) > 0 THEN TRUE ELSE FALSE END,
    t.permitted_gender_enum, t.sort_order
FROM tmp_peis_legacy_package t
WHERE NOT EXISTS (
    SELECT 1 FROM wkf_activity_definition a
    WHERE a.tenant_id = 1 AND a.bus_no = t.bus_no AND a.is_deleted = FALSE
)
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    py_str = EXCLUDED.py_str,
    type_enum = EXCLUDED.type_enum,
    status_enum = 'ACTIVE',
    children_flag = TRUE,
    pricing_flag = EXCLUDED.pricing_flag,
    permitted_gender_enum = EXCLUDED.permitted_gender_enum,
    sort_order = EXCLUDED.sort_order,
    category_code = 'PEIS',
    is_deleted = FALSE,
    updated_at = NOW();

-- 已存在同 bus_no 时更新为套餐口径（不改动已占用的单项 bus_no，套餐码与项目码无交叉）
UPDATE wkf_activity_definition a
SET name = t.display,
    py_str = t.py_str,
    type_enum = '5',
    status_enum = 'ACTIVE',
    children_flag = TRUE,
    pricing_flag = CASE WHEN COALESCE(t.sale_price, 0) > 0 THEN TRUE ELSE FALSE END,
    permitted_gender_enum = t.permitted_gender_enum,
    sort_order = t.sort_order,
    category_code = 'PEIS',
    is_deleted = FALSE,
    updated_at = NOW(),
    updated_by = 1
FROM tmp_peis_legacy_package t
WHERE a.tenant_id = 1
  AND a.bus_no = t.bus_no
  AND a.is_deleted = FALSE;

-- 回写实际套餐 activity_id
UPDATE tmp_peis_legacy_package t
SET activity_id = a.id
FROM wkf_activity_definition a
WHERE a.tenant_id = 1
  AND a.bus_no = t.bus_no
  AND a.is_deleted = FALSE;

-- ========== SALE 原价（je，不用 yj）==========
INSERT INTO adm_charge_item_definition (
    id, tenant_id, created_at, updated_at, created_by, updated_by,
    is_deleted, version, instance_table, instance_id, charge_name, status_enum,
    org_id, type_code, price_type_code, price_value, priority
)
SELECT
    COALESCE(
      (SELECT MAX(id) FROM adm_charge_item_definition
       WHERE id >= 911231000000000000 AND id < 911231000000000000 + 1000000000),
      911231000000000000
    ) + ROW_NUMBER() OVER (ORDER BY t.legacy_id),
    1, NOW(), NOW(), 1, 1,
    FALSE, 0, 'wkf_activity_definition', t.activity_id, t.display, 'ACTIVE',
    NULL, '14', 'SALE', t.sale_price, 0
FROM tmp_peis_legacy_package t
WHERE t.activity_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM adm_charge_item_definition c
      WHERE c.instance_table = 'wkf_activity_definition'
        AND c.instance_id = t.activity_id
        AND c.price_type_code = 'SALE'
        AND c.is_deleted = FALSE
  );

UPDATE adm_charge_item_definition c
SET charge_name = t.display,
    price_value = t.sale_price,
    type_code = '14',
    status_enum = 'ACTIVE',
    is_deleted = FALSE,
    updated_at = NOW(),
    updated_by = 1
FROM tmp_peis_legacy_package t
WHERE t.activity_id IS NOT NULL
  AND c.instance_table = 'wkf_activity_definition'
  AND c.instance_id = t.activity_id
  AND c.price_type_code = 'SALE'
  AND c.is_deleted = FALSE;

-- ========== 子项：xm.code → PEIS 单项 bus_no ==========
INSERT INTO wkf_activity_definition_children (
    id, tenant_id, created_at, updated_at, created_by, updated_by,
    is_deleted, version, activity_id, children_id, children_num, children_name
)
SELECT
    COALESCE(
      (SELECT MAX(id) FROM wkf_activity_definition_children
       WHERE id >= 911232000000000000 AND id < 911232000000000000 + 1000000000),
      911232000000000000
    ) + ROW_NUMBER() OVER (ORDER BY t.legacy_id, i.sort_num),
    1, NOW(), NOW(), 1, 1,
    FALSE, 0, t.activity_id, child.id, 1,
    COALESCE(i.child_name, child.name)
FROM tmp_peis_legacy_package_item i
JOIN tmp_peis_legacy_package t ON t.legacy_id = i.package_legacy_id
JOIN wkf_activity_definition child
  ON child.tenant_id = 1
 AND child.bus_no = i.child_bus_no
 AND child.category_code = 'PEIS'
 AND COALESCE(child.children_flag, FALSE) = FALSE
 AND child.is_deleted = FALSE
WHERE t.activity_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM wkf_activity_definition_children c
      WHERE c.activity_id = t.activity_id
        AND c.children_id = child.id
        AND c.is_deleted = FALSE
  );

-- 校验
SELECT 'PEIS_PKG' AS k, COUNT(*) AS n FROM wkf_activity_definition
 WHERE category_code='PEIS' AND children_flag=TRUE AND is_deleted=FALSE;
SELECT 'PKG_STAGED' AS k, COUNT(*) AS n FROM tmp_peis_legacy_package;
SELECT 'PKG_MAPPED' AS k, COUNT(*) AS n FROM tmp_peis_legacy_package WHERE activity_id IS NOT NULL;
SELECT 'PKG_CHILDREN' AS k, COUNT(*) AS n
  FROM wkf_activity_definition_children c
  JOIN tmp_peis_legacy_package t ON t.activity_id = c.activity_id
 WHERE c.is_deleted=FALSE;
SELECT 'PKG_CHILD_MISS' AS k, COUNT(*) AS n
  FROM tmp_peis_legacy_package_item i
  JOIN tmp_peis_legacy_package t ON t.legacy_id = i.package_legacy_id
  WHERE t.activity_id IS NOT NULL
    AND NOT EXISTS (
      SELECT 1 FROM wkf_activity_definition child
      WHERE child.tenant_id = 1
        AND child.bus_no = i.child_bus_no
        AND child.category_code = 'PEIS'
        AND COALESCE(child.children_flag, FALSE) = FALSE
        AND child.is_deleted = FALSE
    );
SELECT permitted_gender_enum, COUNT(*) FROM wkf_activity_definition
 WHERE category_code='PEIS' AND children_flag=TRUE AND is_deleted=FALSE GROUP BY 1 ORDER BY 1;

COMMIT;
