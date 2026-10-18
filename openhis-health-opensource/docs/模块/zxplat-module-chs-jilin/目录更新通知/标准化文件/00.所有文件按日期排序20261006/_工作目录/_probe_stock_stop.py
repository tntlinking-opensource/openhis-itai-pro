# -*- coding: utf-8 -*-
import psycopg2
import pymysql

pg = psycopg2.connect(host="192.168.1.1", port=5433, user="postgres", password="root", dbname="zxplat-bak")
cur = pg.cursor()
cur.execute("""
SELECT status_enum, COUNT(*) FROM med_medication_definition
WHERE is_deleted = false GROUP BY 1 ORDER BY 2 DESC
""")
print("med status", cur.fetchall())
cur.execute("""
SELECT status_enum, COUNT(*) FROM adm_device_definition
WHERE is_deleted = false GROUP BY 1 ORDER BY 2 DESC
""")
print("dev status", cur.fetchall())
cur.execute("""
SELECT
  SUM(CASE WHEN definition LIKE '%院内旧编码=%' THEN 1 ELSE 0 END),
  SUM(CASE WHEN definition LIKE '%老系统药品编码=%' THEN 1 ELSE 0 END),
  COUNT(*)
FROM med_medication_definition WHERE is_deleted = false
""")
print("med legacy markers", cur.fetchone())
cur.execute("""
SELECT
  SUM(CASE WHEN description_text LIKE '%院内旧编码=%' THEN 1 ELSE 0 END),
  SUM(CASE WHEN description_text LIKE '%老系统药品编码=%' THEN 1 ELSE 0 END),
  COUNT(*)
FROM adm_device_definition WHERE is_deleted = false
""")
print("dev legacy markers", cur.fetchone())
cur.execute("""
SELECT bus_no, name, left(coalesce(definition,''), 180), status_enum
FROM med_medication_definition WHERE is_deleted = false LIMIT 3
""")
print("--- med sample ---")
for row in cur.fetchall():
    print(row)
cur.execute("""
SELECT bus_no, name, left(coalesce(description_text,''), 180), status_enum
FROM adm_device_definition WHERE is_deleted = false LIMIT 3
""")
print("--- dev sample ---")
for row in cur.fetchall():
    print(row)
pg.close()

my = pymysql.connect(host="192.168.1.106", port=3306, user="root", password="mycqyl_20191",
                     database="areahis_jlcc", charset="utf8mb4")
mc = my.cursor()
mc.execute("""
SELECT si.item_type, COUNT(DISTINCT bi.YP_CODE)
FROM stock_item si
JOIN b_item bi ON bi.ITEM_ID = si.item_id
WHERE si.org_id = 239 AND si.quantity > 0 AND IFNULL(bi.IS_VOID,0)=0
GROUP BY si.item_type
""")
print("stock yp by type", mc.fetchall())
my.close()
