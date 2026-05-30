-- cus_frequency（建表）
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
    "cycle_type" int4 NOT NULL,
    "day_count" int4,
    "day_interval" int4,
    "day_times" varchar(255),
    "week_cycle_flag" bool,
    "total_execution_count" int4,
    "execution_period" int4,
    "execution_period_unit" varchar(255),
    "applicable_scenes" varchar(255),
    "allow_cross_day" int4,
    "active_flag" bool NOT NULL,
    "sort_no" int4,
    CONSTRAINT "cus_frequency_pkey" PRIMARY KEY ("id")
);

-- seed: adm_location（V15 未包含的病区/床位，增量数据）
INSERT INTO "public"."adm_location" ("id", "tenant_id", "created_at", "updated_at", "created_by", "updated_by", "is_deleted", "deleted_at", "deleted_by", "version", "name", "bus_no", "status_enum", "operational_enum", "form_enum", "mode_enum", "type_json", "organization_id", "display_order", "py_str", "wb_str", "parent_id") VALUES
(319025239965044736, 1, '2026-05-30 16:12:24.41058+08', '2026-05-30 16:12:24.41058+08', 1, 1, false, NULL, NULL, 0, '门诊病区', 'LO0018', 'ACTIVE', 'NOT_IN_USE', 'WARD', 'INSTANCE', '{}', 6, NULL, NULL, NULL, NULL),
(319025301734559744, 1, '2026-05-30 16:12:39.137219+08', '2026-05-30 16:12:39.137219+08', 1, 1, false, NULL, NULL, 0, '病房301', 'LO0018.LO0019', 'ACTIVE', 'NOT_IN_USE', 'HOUSE', 'INSTANCE', '{}', 6, NULL, NULL, NULL, 319025239965044736),
(319025336354344960, 1, '2026-05-30 16:12:47.391939+08', '2026-05-30 16:12:47.391939+08', 1, 1, false, NULL, NULL, 0, '病房302', 'LO0018.LO0020', 'ACTIVE', 'NOT_IN_USE', 'HOUSE', 'INSTANCE', '{}', 6, NULL, NULL, NULL, 319025239965044736),
(319025369048944640, 1, '2026-05-30 16:12:55.186314+08', '2026-05-30 16:12:55.186314+08', 1, 1, false, NULL, NULL, 0, '病房303', 'LO0018.LO0021', 'ACTIVE', 'NOT_IN_USE', 'HOUSE', 'INSTANCE', '{}', 6, NULL, NULL, NULL, 319025239965044736),
(319025523273502720, 1, '2026-05-30 16:13:31.956441+08', '2026-05-30 16:13:31.956441+08', 1, 1, false, NULL, NULL, 0, '二号床', 'LO0018.LO0019.LO0023', 'ACTIVE', 'NOT_IN_USE', 'BED', 'INSTANCE', '{}', 6, NULL, NULL, NULL, 319025301734559744),
(319025561907236864, 1, '2026-05-30 16:13:41.167288+08', '2026-05-30 16:13:41.167288+08', 1, 1, false, NULL, NULL, 0, '一号床', 'LO0018.LO0020.LO0024', 'ACTIVE', 'NOT_IN_USE', 'BED', 'INSTANCE', '{}', 6, NULL, NULL, NULL, 319025336354344960),
(319025589228933120, 1, '2026-05-30 16:13:47.681674+08', '2026-05-30 16:13:47.681674+08', 1, 1, false, NULL, NULL, 0, '二号床', 'LO0018.LO0020.LO0025', 'ACTIVE', 'NOT_IN_USE', 'BED', 'INSTANCE', '{}', 6, NULL, NULL, NULL, 319025336354344960),
(319025615778877440, 1, '2026-05-30 16:13:54.011817+08', '2026-05-30 16:13:54.011817+08', 1, 1, false, NULL, NULL, 0, '三号床', 'LO0018.LO0020.LO0026', 'ACTIVE', 'NOT_IN_USE', 'BED', 'INSTANCE', '{}', 6, NULL, NULL, NULL, 319025336354344960),
(319025644295950336, 1, '2026-05-30 16:14:00.810521+08', '2026-05-30 16:14:00.810521+08', 1, 1, false, NULL, NULL, 0, '一号床', 'LO0018.LO0021.LO0027', 'ACTIVE', 'NOT_IN_USE', 'BED', 'INSTANCE', '{}', 6, NULL, NULL, NULL, 319025369048944640),
(319025668488695808, 1, '2026-05-30 16:14:06.578354+08', '2026-05-30 16:14:06.578354+08', 1, 1, false, NULL, NULL, 0, '二号床', 'LO0018.LO0021.LO0028', 'ACTIVE', 'NOT_IN_USE', 'BED', 'INSTANCE', '{}', 6, NULL, NULL, NULL, 319025369048944640),
(319025691674808320, 1, '2026-05-30 16:14:12.106011+08', '2026-05-30 16:14:12.106011+08', 1, 1, false, NULL, NULL, 0, '三号床', 'LO0018.LO0021.LO0029', 'ACTIVE', 'NOT_IN_USE', 'BED', 'INSTANCE', '{}', 6, NULL, NULL, NULL, 319025369048944640),
(319025436304609280, 1, '2026-05-30 16:13:11.221419+08', '2026-05-30 16:18:18.517447+08', 1, 1, false, NULL, NULL, 0, '一号床', 'LO0018.LO0019.LO0022', 'ACTIVE', 'IN_USE', 'BED', 'INSTANCE', NULL, 6, NULL, NULL, NULL, 319025301734559744)
ON CONFLICT ("id") DO NOTHING;
