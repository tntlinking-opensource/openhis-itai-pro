# -*- coding: utf-8 -*-
"""把 whale-training 诊疗目录（主表、子项、定价）同步到 zxplat-bak。不碰药品和耗材定价。"""
import io
import psycopg2

def connect(db):
    return psycopg2.connect(host="192.168.1.1", port=5433, user="postgres", password="root", dbname=db)

src = connect("whale-training")
dst = connect("zxplat-bak")
src.autocommit = True
dst.autocommit = False
sc = src.cursor()
dc = dst.cursor()
dc.execute("SELECT current_database()")
if dc.fetchone()[0] != "zxplat-bak":
    raise SystemExit("拒绝：目标不是 zxplat-bak")
dc.execute("SET LOCAL session_replication_role = replica")
dc.execute("""
    DELETE FROM adm_charge_item_definition
    WHERE instance_table = 'wkf_activity_definition'
""")
dc.execute("DELETE FROM wkf_activity_definition_children")
dc.execute("DELETE FROM wkf_activity_definition")

copies = [
    (
        "wkf_activity_definition",
        "COPY wkf_activity_definition TO STDOUT WITH (FORMAT binary)",
        "COPY wkf_activity_definition FROM STDIN WITH (FORMAT binary)",
    ),
    (
        "wkf_activity_definition_children",
        "COPY wkf_activity_definition_children TO STDOUT WITH (FORMAT binary)",
        "COPY wkf_activity_definition_children FROM STDIN WITH (FORMAT binary)",
    ),
    (
        "adm_charge_item_definition",
        """COPY (
            SELECT * FROM adm_charge_item_definition
            WHERE instance_table = 'wkf_activity_definition'
        ) TO STDOUT WITH (FORMAT binary)""",
        "COPY adm_charge_item_definition FROM STDIN WITH (FORMAT binary)",
    ),
]
for name, out_sql, in_sql in copies:
    buf = io.BytesIO()
    sc.copy_expert(out_sql, buf)
    buf.seek(0)
    dc.copy_expert(in_sql, buf)
    print("copied", name, "bytes", buf.tell())

dst.commit()

def counts(conn):
    cur = conn.cursor()
    cur.execute("""
        SELECT
          (SELECT COUNT(*) FROM wkf_activity_definition),
          (SELECT COUNT(*) FROM wkf_activity_definition WHERE is_deleted = false),
          (SELECT COUNT(*) FROM wkf_activity_definition WHERE is_deleted = false
             AND (mr_settlement_type_code IS NULL OR btrim(mr_settlement_type_code) = '')),
          (SELECT COUNT(*) FROM wkf_activity_definition_children),
          (SELECT COUNT(*) FROM wkf_activity_definition WHERE is_deleted = false AND children_flag IS TRUE),
          (SELECT COUNT(*) FROM adm_charge_item_definition WHERE instance_table = 'wkf_activity_definition'),
          (SELECT COUNT(*) FROM adm_charge_item_definition
            WHERE instance_table IS DISTINCT FROM 'wkf_activity_definition' AND is_deleted = false)
    """)
    return cur.fetchone()

print("training", counts(src))
print("bak", counts(dst))
dc.execute("""
    SELECT a.bus_no, a.name, a.mr_settlement_type_code,
           (SELECT COUNT(*) FROM wkf_activity_definition_children c
            WHERE c.activity_id = a.id AND c.is_deleted = false)
    FROM wkf_activity_definition a
    WHERE a.bus_no = 'AD00003508'
""")
print("肝功七项", dc.fetchone())
src.close()
dst.close()
