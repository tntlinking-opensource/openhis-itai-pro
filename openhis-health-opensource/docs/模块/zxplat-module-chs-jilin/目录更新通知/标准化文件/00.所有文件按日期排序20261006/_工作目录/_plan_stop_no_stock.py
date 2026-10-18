# -*- coding: utf-8 -*-
"""统计 zxplat-bak 中老系统无库存的药品/耗材。不写库。"""
import re
import pymysql
import psycopg2

CODE_RE = re.compile(r"(?:老系统药品编码|院内旧编码)=([^\s\r\n]+)")

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
    if int(item_type) in (1, 2, 8):
        drug_stock.add(code)
    elif int(item_type) == 5:
        dev_stock.add(code)
my.close()
print("yp", len(yp_set), "drug_stock", len(drug_stock), "dev_stock", len(dev_stock))

def resolve(text):
    found = CODE_RE.findall(text or "")
    legacy = None
    old = None
    # findall doesn't tell which prefix. parse lines.
    for line in str(text or "").splitlines():
        line = line.strip()
        if line.startswith("老系统药品编码="):
            old = line.split("=", 1)[1].strip()
        elif line.startswith("院内旧编码="):
            legacy = line.split("=", 1)[1].strip()
    raw = old or legacy or ""
    if not raw:
        return None, "无编码"
    if raw in yp_set:
        return raw, "YP_CODE"
    if raw in item_to_yp:
        return item_to_yp[raw], "ITEM_ID"
    return raw, "老库无此码"

pg = psycopg2.connect(host="192.168.1.1", port=5433, user="postgres", password="root", dbname="zxplat-bak")
cur = pg.cursor()

def classify(sql, stock):
    cur.execute(sql)
    buckets = {}
    stop = keep = 0
    for row_id, text in cur.fetchall():
        code, how = resolve(text)
        key = how
        if how in ("YP_CODE", "ITEM_ID"):
            key = "有库存" if code in stock else "无库存"
        buckets[key] = buckets.get(key, 0) + 1
        if key == "无库存":
            stop += 1
        elif key == "有库存":
            keep += 1
    return buckets, stop, keep

med_stat, med_stop, med_keep = classify(
    "SELECT id, definition FROM med_medication_definition WHERE is_deleted = false AND status_enum = 'ACTIVE'",
    drug_stock,
)
dev_stat, dev_stop, dev_keep = classify(
    "SELECT id, description FROM adm_device_definition WHERE is_deleted = false AND status_enum = 'ACTIVE'",
    dev_stock,
)
print("med", med_stat, "stop", med_stop, "keep", med_keep)
print("dev", dev_stat, "stop", dev_stop, "keep", dev_keep)
pg.close()
