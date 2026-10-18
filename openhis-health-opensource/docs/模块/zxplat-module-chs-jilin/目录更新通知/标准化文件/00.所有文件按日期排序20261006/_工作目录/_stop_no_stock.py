# -*- coding: utf-8 -*-
"""zxplat-bak：老系统无正库存的药品、耗材目录改为停用。有库存的不动。对不上老编码的不动。"""
import pymysql
import psycopg2

my = pymysql.connect(host="192.168.1.106", port=3306, user="root", password="mycqyl_20191",
                     database="areahis_jlcc", charset="utf8mb4")
mc = my.cursor()
mc.execute("SELECT YP_CODE, ITEM_ID FROM b_item WHERE IFNULL(IS_VOID,0)=0")
yp_set = set()
item_to_yp = {}
for yp, item_id in mc.fetchall():
    code = str(yp or "").strip()
    if code:
        yp_set.add(code)
        item_to_yp[str(item_id)] = code
mc.execute("""
    SELECT bi.YP_CODE, si.item_type
    FROM stock_item si
    JOIN b_item bi ON bi.ITEM_ID = si.item_id
    WHERE si.org_id = 239 AND si.quantity > 0 AND IFNULL(bi.IS_VOID,0)=0
""")
drug_stock = set()
dev_stock = set()
for yp, item_type in mc.fetchall():
    code = str(yp or "").strip()
    if not code:
        continue
    kind = int(item_type)
    if kind in (1, 2, 8):
        drug_stock.add(code)
    elif kind == 5:
        dev_stock.add(code)
my.close()

def resolve(text):
    old = None
    legacy = None
    for line in str(text or "").splitlines():
        line = line.strip()
        if line.startswith("老系统药品编码="):
            old = line.split("=", 1)[1].strip()
        elif line.startswith("院内旧编码="):
            legacy = line.split("=", 1)[1].strip()
    raw = old or legacy or ""
    if not raw:
        return None
    if raw in yp_set:
        return raw
    if raw in item_to_yp:
        return item_to_yp[raw]
    return None

pg = psycopg2.connect(host="192.168.1.1", port=5433, user="postgres", password="root", dbname="zxplat-bak")
pg.autocommit = False
cur = pg.cursor()
cur.execute("SELECT current_database()")
if cur.fetchone()[0] != "zxplat-bak":
    raise SystemExit("拒绝：不是 zxplat-bak")

def collect(sql, stock):
    cur.execute(sql)
    ids = []
    for row_id, text in cur.fetchall():
        code = resolve(text)
        if code is not None and code not in stock:
            ids.append(row_id)
    return ids

med_ids = collect(
    "SELECT id, definition FROM med_medication_definition WHERE is_deleted = false AND status_enum = 'ACTIVE'",
    drug_stock,
)
dev_ids = collect(
    "SELECT id, description FROM adm_device_definition WHERE is_deleted = false AND status_enum = 'ACTIVE'",
    dev_stock,
)
print("to_stop med", len(med_ids), "dev", len(dev_ids))

cur.execute("""
    UPDATE med_medication_definition
    SET status_enum = 'INACTIVE', active_flag = false, updated_at = CURRENT_TIMESTAMP, updated_by = 1
    WHERE is_deleted = false AND status_enum = 'ACTIVE' AND id = ANY(%s)
""", (med_ids,))
print("med updated", cur.rowcount)
cur.execute("""
    UPDATE adm_device_definition
    SET status_enum = 'INACTIVE', active_flag = false, updated_at = CURRENT_TIMESTAMP, updated_by = 1
    WHERE is_deleted = false AND status_enum = 'ACTIVE' AND id = ANY(%s)
""", (dev_ids,))
print("dev updated", cur.rowcount)
pg.commit()

cur.execute("""
    SELECT '药品', status_enum, COUNT(*) FROM med_medication_definition
    WHERE is_deleted = false GROUP BY status_enum
    UNION ALL
    SELECT '耗材', status_enum, COUNT(*) FROM adm_device_definition
    WHERE is_deleted = false GROUP BY status_enum
    ORDER BY 1, 2
""")
for row in cur.fetchall():
    print(row)
pg.close()
