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
    "role_code" int4 NOT NULL,
    "active_flag" bool NOT NULL DEFAULT true,
    "practitioner_id" int8 NOT NULL,
    "org_id" int8,
    "location_id" int8,
    "specialty_code" varchar(64),
    "type_code" int4,

    CONSTRAINT "adm_pr_assign_pkey" PRIMARY KEY ("id"),
    CONSTRAINT "ck_adm_pr_assign_org_or_location_not_null" CHECK ("org_id" IS NOT NULL OR "location_id" IS NOT NULL)
);

-- seed: adm_pr_assign
INSERT INTO "adm_pr_assign" ("id", "tenant_id", "created_at", "updated_at", "created_by", "updated_by", "is_deleted", "deleted_at", "deleted_by", "version", "name", "role_code", "active_flag", "practitioner_id", "org_id", "location_id", "specialty_code", "type_code")
VALUES
(309211073810534400, 1, '2026-05-03 06:14:24.860+00', '2026-05-03 06:14:37.264+00', 1, 1, false, NULL, NULL, 0, NULL, 1, true, 2, 6, NULL, NULL, NULL),
(309213686425718784, 1, '2026-05-03 06:24:47.756+00', '2026-05-03 06:24:47.756+00', 1, 1, false, NULL, NULL, 0, NULL, 1, true, 308791351298756608, 6, NULL, NULL, NULL),
(310028120844931072, 1, '2026-05-05 12:21:04.053+00', '2026-05-05 12:21:04.053+00', 1, 1, false, NULL, NULL, 0, NULL, 1, true, 308791351298756608, 4, NULL, NULL, NULL),
(310028196904439808, 1, '2026-05-05 12:21:22.187+00', '2026-05-05 12:21:22.187+00', 1, 1, false, NULL, NULL, 0, NULL, 2, true, 3, 5, NULL, NULL, NULL),
(311483127961227264, 1, '2026-05-09 12:42:44.782+00', '2026-05-09 12:42:44.782+00', 1, 1, false, NULL, NULL, 0, NULL, 3, true, 308791351298756608, 6, NULL, NULL, NULL),
(311483164153876480, 1, '2026-05-09 12:42:53.411+00', '2026-05-09 12:42:53.411+00', 1, 1, false, NULL, NULL, 0, NULL, 3, true, 2, 6, NULL, NULL, NULL),
(3600000000000000001, 1, '2026-05-03 06:07:04.587+00', '2026-05-03 06:07:04.587+00', 1, 1, false, NULL, NULL, 0, '张三-内科', 1, true, 2, 4, NULL, NULL, NULL),
(3600000000000000002, 1, '2026-05-03 06:07:04.587+00', '2026-05-03 06:07:04.587+00', 1, 1, false, NULL, NULL, 0, '李四-外科', 1, true, 3, 5, NULL, NULL, NULL),
(3600000000000000003, 1, '2026-05-03 06:07:04.587+00', '2026-05-03 06:07:04.587+00', 1, 1, false, NULL, NULL, 0, '王五-内科', 1, true, 4, 4, NULL, NULL, NULL),
(3600000000000000004, 1, '2026-05-03 06:07:04.587+00', '2026-05-03 06:07:04.587+00', 1, 1, false, NULL, NULL, 0, '王五-外科', 1, true, 4, 5, NULL, NULL, NULL),
(319026612022546432, 1, '2026-05-30 16:17:51.534592+08', '2026-05-30 16:17:51.534592+08', 1, 1, false, NULL, NULL, 0, NULL, 2, true, 308791351298756608, 6, 319025239965044740, NULL, NULL)
ON CONFLICT ("id") DO NOTHING;
