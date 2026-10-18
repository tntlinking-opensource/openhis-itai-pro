# -*- coding: utf-8 -*-
import psycopg2

def connect(db):
    return psycopg2.connect(host="192.168.1.1", port=5433, user="postgres", password="root", dbname=db)

for db in ("whale-training", "zxplat-bak"):
    conn = connect(db)
    cur = conn.cursor()
    print("====", db)
    cur.execute("""
        SELECT 'ad' , COUNT(*) FROM wkf_activity_definition
        UNION ALL SELECT 'ad_live', COUNT(*) FROM wkf_activity_definition WHERE is_deleted = false
        UNION ALL SELECT 'child', COUNT(*) FROM wkf_activity_definition_children
        UNION ALL SELECT 'child_live', COUNT(*) FROM wkf_activity_definition_children WHERE is_deleted = false
        UNION ALL SELECT 'price', COUNT(*) FROM adm_charge_item_definition
          WHERE instance_table = 'wkf_activity_definition'
        UNION ALL SELECT 'price_other', COUNT(*) FROM adm_charge_item_definition
          WHERE instance_table IS DISTINCT FROM 'wkf_activity_definition' AND is_deleted = false
    """)
    for row in cur.fetchall():
        print(row)
    conn.close()

src = connect("whale-training")
dst = connect("zxplat-bak")
sc, dc = src.cursor(), dst.cursor()
for table in ("wkf_activity_definition", "wkf_activity_definition_children", "adm_charge_item_definition"):
    sc.execute("""
        SELECT column_name, data_type, udt_name, is_nullable
        FROM information_schema.columns
        WHERE table_schema='public' AND table_name=%s
        ORDER BY ordinal_position
    """, (table,))
    src_cols = sc.fetchall()
    dc.execute("""
        SELECT column_name, data_type, udt_name, is_nullable
        FROM information_schema.columns
        WHERE table_schema='public' AND table_name=%s
        ORDER BY ordinal_position
    """, (table,))
    dst_cols = dc.fetchall()
    print(table, "src", len(src_cols), "dst", len(dst_cols), "same", src_cols == dst_cols)
    if src_cols != dst_cols:
        src_names = [c[0] for c in src_cols]
        dst_names = [c[0] for c in dst_cols]
        print(" only src", set(src_names)-set(dst_names))
        print(" only dst", set(dst_names)-set(src_names))

dc.execute("""
    SELECT conrelid::regclass, conname, pg_get_constraintdef(oid)
    FROM pg_constraint
    WHERE contype = 'f'
      AND pg_get_constraintdef(oid) LIKE '%wkf_activity_definition%'
""")
print("--- fks to activity ---")
for row in dc.fetchall():
    print(row)
src.close()
dst.close()
