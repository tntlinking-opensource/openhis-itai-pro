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

-- seed: adm_pr_assign_service
INSERT INTO "adm_pr_assign_service" ("id", "tenant_id", "created_at", "updated_at", "created_by", "updated_by", "is_deleted", "deleted_at", "deleted_by", "version", "assignment_id", "healthcare_service_id")
VALUES
(3700000000000000001, 1, '2026-05-03 06:07:04.590+00', '2026-05-03 06:07:04.590+00', 1, 1, false, NULL, NULL, 0, 3600000000000000001, 3300000000000000001),
(3700000000000000002, 1, '2026-05-03 06:07:04.590+00', '2026-05-03 06:07:04.590+00', 1, 1, false, NULL, NULL, 0, 3600000000000000002, 3300000000000000002),
(3700000000000000003, 1, '2026-05-03 06:07:04.590+00', '2026-05-03 06:07:04.590+00', 1, 1, false, NULL, NULL, 0, 3600000000000000003, 3300000000000000002),
(3700000000000000004, 1, '2026-05-03 06:07:04.590+00', '2026-05-03 06:07:04.590+00', 1, 1, false, NULL, NULL, 0, 3600000000000000004, 3300000000000000003)
ON CONFLICT ("id") DO NOTHING;
