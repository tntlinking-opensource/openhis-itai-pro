-- Campus dev seed: 小程序 bindCampusStudent / bindCampusStaff 联调档案
-- 【DEV ONLY】依赖 health-dev 患者与 V2 校区；生产/清库基线非必须，可跳过或删 history 后不跑本脚本。
-- 依赖 health-dev V23 患者：325580322788872192 李先生、325866857001652224 小王
-- 依赖 V2 校区：910000000000000001 自由校区

INSERT INTO "public"."cmp_campus_college" (
    "id", "tenant_id", "created_at", "is_deleted", "version",
    "college_code", "college_name", "campus_site_id", "active_flag", "sort_order"
) VALUES (
    920000000000000501, 1, now(), false, 0,
    'DEV-COLLEGE', '生命科学学院（联调）', 910000000000000001, true, 0
)
ON CONFLICT ("id") DO NOTHING;

INSERT INTO "public"."cmp_campus_department" (
    "id", "tenant_id", "created_at", "is_deleted", "version",
    "department_code", "department_name", "campus_site_id", "active_flag", "sort_order"
) VALUES (
    920000000000000601, 1, now(), false, 0,
    'DEV-DEPT', '校医院（联调）', 910000000000000001, true, 0
)
ON CONFLICT ("id") DO NOTHING;

INSERT INTO "public"."cmp_patient_student" (
    "id", "tenant_id", "created_at", "is_deleted", "version",
    "patient_id", "name", "student_no", "college_id", "college_name",
    "campus_code", "school_status_enum", "active_flag"
) VALUES (
    920000000000000701, 1, now(), false, 0,
    325580322788872192, '李先生', 'DEV2026001',
    920000000000000501, '生命科学学院（联调）',
    'BENBU', 'IN_SCHOOL', true
)
ON CONFLICT ("id") DO NOTHING;

INSERT INTO "public"."cmp_patient_staff" (
    "id", "tenant_id", "created_at", "is_deleted", "version",
    "patient_id", "name", "employee_no",
    "department_id", "campus_site_id", "job_status_enum", "active_flag"
) VALUES (
    920000000000000801, 1, now(), false, 0,
    325866857001652224, '小王', 'DEV-E001',
    920000000000000601, 910000000000000001, 'ACTIVE', true
)
ON CONFLICT ("id") DO NOTHING;
