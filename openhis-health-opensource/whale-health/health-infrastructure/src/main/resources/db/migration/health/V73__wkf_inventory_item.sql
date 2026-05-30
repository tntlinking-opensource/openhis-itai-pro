-- wkf_inventory_item（建表 + 种子数据）
-- Flyway V73

CREATE TABLE IF NOT EXISTS "public"."wkf_inventory_item" (
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
    "category_code" varchar(255),
    "item_table" varchar(255) NOT NULL,
    "item_id" int8 NOT NULL,
    "name" varchar(255) NOT NULL,
    "py_str" varchar(255),
    "wb_str" varchar(255),
    "inventory_status_enum" varchar(255) NOT NULL,
    "supplier_id" int8 NOT NULL,
    "description_text" varchar(255),
    "unit_code" varchar(255) NOT NULL,
    "quantity" numeric(18,2) NOT NULL,
    "characteristic_json" varchar(255),
    "lot_number" varchar(255) NOT NULL,
    "production_date" timestamptz(6),
    "expiration_date" timestamptz(6),
    "validity_mon" int4,
    "location_store_id" int8,
    "location_id" int8 NOT NULL,
    "trace_no" varchar(50000),
    "packaging_levels" int4,
    CONSTRAINT "wkf_inventory_item_pkey" PRIMARY KEY ("id")
);

-- seed: wkf_inventory_item
INSERT INTO "wkf_inventory_item" ("id", "tenant_id", "created_at", "updated_at", "created_by", "updated_by", "is_deleted", "deleted_at", "deleted_by", "version", "category_code", "item_table", "item_id", "name", "py_str", "wb_str", "inventory_status_enum", "supplier_id", "description_text", "unit_code", "quantity", "characteristic_json", "lot_number", "production_date", "expiration_date", "validity_mon", "location_store_id", "location_id", "trace_no", "packaging_levels")
VALUES
(318622630570102784, 1, '2026-05-29 05:32:34.850+00', '2026-05-29 05:51:52.839+00', 1, 1, false, NULL, NULL, 1, 11, 'adm_device_definition', 318617136047591424, '一次性使用无菌注射器', 'YCXSYWJZSQ', 'GUNWEFAITK', 'ACTIVE', 4100000000000000001, NULL, 9, 50.00, NULL, 052902, '2026-05-28 16:00:00.000+00', '2027-07-02 16:00:00.000+00', NULL, NULL, 4200000000000000001, NULL, NULL),
(318622630591074304, 1, '2026-05-29 05:32:34.855+00', '2026-05-29 05:32:34.855+00', 1, 1, false, NULL, NULL, 0, 11, 'adm_device_definition', 318619026458677248, '(蓝）一次性真空采血管', '(L）YCXZKCXG', '(A）GUNFPETT', 'ACTIVE', 4100000000000000001, NULL, 9, 100.00, NULL, 052901, '2026-04-27 16:00:00.000+00', '2027-07-21 16:00:00.000+00', NULL, NULL, 4200000000000000001, NULL, NULL),
(318627232605540352, 1, '2026-05-29 05:50:52.060+00', '2026-05-29 05:51:52.839+00', 1, 1, false, NULL, NULL, 1, 11, 'adm_device_definition', 318619026458677248, '(蓝）一次性真空采血管', '(L）YCXZKCXG', '(A）GUNFPETT', 'ACTIVE', 4100000000000000001, NULL, 9, 50.00, NULL, 052903, '2026-05-28 16:00:00.000+00', '2027-06-25 16:00:00.000+00', NULL, NULL, 4200000000000000001, NULL, NULL),
(318627487577280512, 1, '2026-05-29 05:51:52.850+00', '2026-05-29 05:51:52.850+00', 1, 1, false, NULL, NULL, 0, 11, 'adm_device_definition', 318617136047591424, '一次性使用无菌注射器', 'YCXSYWJZSQ', 'GUNWEFAITK', 'ACTIVE', 4100000000000000001, NULL, 9, 50.00, NULL, 052902, '2026-05-28 16:00:00.000+00', '2027-07-02 16:00:00.000+00', NULL, NULL, 4200000000000000002, NULL, NULL),
(318627487594057728, 1, '2026-05-29 05:51:52.854+00', '2026-05-29 05:51:52.854+00', 1, 1, false, NULL, NULL, 0, 11, 'adm_device_definition', 318619026458677248, '(蓝）一次性真空采血管', '(L）YCXZKCXG', '(A）GUNFPETT', 'ACTIVE', 4100000000000000001, NULL, 9, 50.00, NULL, 052903, '2026-05-28 16:00:00.000+00', '2027-06-25 16:00:00.000+00', NULL, NULL, 4200000000000000002, NULL, NULL),
(318655687341641728, 1, '2026-05-29 07:43:56.198+00', '2026-05-29 07:46:47.208+00', 1, 1, false, NULL, NULL, 1, 2, 'med_medication_definition', 318616326953766912, '左氧氟沙星片', 'ZYFSXP', 'DRRIJT', 'ACTIVE', 4100000000000000001, NULL, 1, 0.00, NULL, '001-002', '2026-04-30 16:00:00.000+00', '2026-06-29 16:00:00.000+00', NULL, NULL, 4200000000000000001, NULL, NULL),
(318655687362613248, 1, '2026-05-29 07:43:56.203+00', '2026-05-29 07:46:47.208+00', 1, 1, false, NULL, NULL, 1, 2, 'med_medication_definition', 318616975946813440, '氯化钠注射液', 'LHNZSY', 'RWQITI', 'ACTIVE', 4100000000000000001, NULL, 8, 100.00, NULL, '001-003', '2026-04-30 16:00:00.000+00', '2026-06-29 16:00:00.000+00', NULL, NULL, 4200000000000000001, NULL, NULL),
(318655687371001856, 1, '2026-05-29 07:43:56.205+00', '2026-05-29 07:46:47.208+00', 1, 1, false, NULL, NULL, 1, 2, 'med_medication_definition', 318617582145376256, '注射用头孢呋辛钠', 'ZSYTBFXN', 'ITEUBKUQ', 'ACTIVE', 4100000000000000001, NULL, 8, 0.00, NULL, '001-004', '2026-04-30 16:00:00.000+00', '2026-06-29 16:00:00.000+00', NULL, NULL, 4200000000000000001, NULL, NULL),
(318655687379390464, 1, '2026-05-29 07:43:56.207+00', '2026-05-29 07:46:47.208+00', 1, 1, false, NULL, NULL, 1, 2, 'med_medication_definition', 318619891278024704, '注射用阿奇霉素', 'ZSYAQMS', 'ITEBDFG', 'ACTIVE', 4100000000000000001, NULL, 8, 100.00, NULL, '001-005', '2026-04-30 16:00:00.000+00', '2026-06-29 16:00:00.000+00', NULL, NULL, 4200000000000000001, NULL, NULL),
(318655687387779072, 1, '2026-05-29 07:43:56.209+00', '2026-05-29 07:46:47.208+00', 1, 1, false, NULL, NULL, 1, 2, 'med_medication_definition', 318624888930177024, '银杏叶片', 'YXYP', 'QSKT', 'ACTIVE', 4100000000000000001, NULL, 1, 0.00, NULL, '001-006', '2026-04-30 16:00:00.000+00', '2026-06-29 16:00:00.000+00', NULL, NULL, 4200000000000000001, NULL, NULL),
(318656404647317504, 1, '2026-05-29 07:46:47.217+00', '2026-05-29 08:41:26.700+00', 1, 1, false, NULL, NULL, 1, 2, 'med_medication_definition', 318624888930177024, '银杏叶片', 'YXYP', 'QSKT', 'ACTIVE', 4100000000000000001, NULL, 1, 684.00, NULL, '001-006', '2026-04-30 16:00:00.000+00', '2026-06-29 16:00:00.000+00', NULL, NULL, 4200000000000000002, NULL, NULL),
(318656404659900416, 1, '2026-05-29 07:46:47.220+00', '2026-05-29 08:04:20.214+00', 1, 1, false, NULL, NULL, 1, 2, 'med_medication_definition', 318619891278024704, '注射用阿奇霉素', 'ZSYAQMS', 'ITEBDFG', 'ACTIVE', 4100000000000000001, NULL, 8, 197.00, NULL, '001-005', '2026-04-30 16:00:00.000+00', '2026-06-29 16:00:00.000+00', NULL, NULL, 4200000000000000002, NULL, NULL),
(318656404668289024, 1, '2026-05-29 07:46:47.222+00', '2026-05-29 07:46:47.222+00', 1, 1, false, NULL, NULL, 0, 2, 'med_medication_definition', 318617582145376256, '注射用头孢呋辛钠', 'ZSYTBFXN', 'ITEUBKUQ', 'ACTIVE', 4100000000000000001, NULL, 8, 50.00, NULL, '001-004', '2026-04-30 16:00:00.000+00', '2026-06-29 16:00:00.000+00', NULL, NULL, 4200000000000000002, NULL, NULL),
(318656404672483328, 1, '2026-05-29 07:46:47.223+00', '2026-05-29 07:46:47.223+00', 1, 1, false, NULL, NULL, 0, 2, 'med_medication_definition', 318616975946813440, '氯化钠注射液', 'LHNZSY', 'RWQITI', 'ACTIVE', 4100000000000000001, NULL, 8, 100.00, NULL, '001-003', '2026-04-30 16:00:00.000+00', '2026-06-29 16:00:00.000+00', NULL, NULL, 4200000000000000002, NULL, NULL),
(318656404680871936, 1, '2026-05-29 07:46:47.225+00', '2026-05-29 07:46:47.225+00', 1, 1, false, NULL, NULL, 0, 2, 'med_medication_definition', 318616326953766912, '左氧氟沙星片', 'ZYFSXP', 'DRRIJT', 'ACTIVE', 4100000000000000001, NULL, 1, 280.00, NULL, '001-002', '2026-04-30 16:00:00.000+00', '2026-06-29 16:00:00.000+00', NULL, NULL, 4200000000000000002, NULL, NULL)
ON CONFLICT ("id") DO NOTHING;
