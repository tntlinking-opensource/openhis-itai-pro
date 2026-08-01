-- Campus seed: 东北师范大学校区（tenant_id=1）
INSERT INTO "public"."cmp_campus_site" (
    "id",
    "tenant_id",
    "created_at",
    "is_deleted",
    "version",
    "campus_code",
    "campus_name",
    "short_name",
    "is_primary",
    "active_flag",
    "sort_order"
) VALUES
(
    910000000000000001,
    1,
    now(),
    false,
    0,
    'BENBU',
    '自由校区',
    '自由校区',
    true,
    true,
    0
),
(
    910000000000000002,
    1,
    now(),
    false,
    0,
    'JINGYUE',
    '净月校区',
    '净月校区',
    false,
    true,
    1
)
ON CONFLICT ("id") DO NOTHING;
