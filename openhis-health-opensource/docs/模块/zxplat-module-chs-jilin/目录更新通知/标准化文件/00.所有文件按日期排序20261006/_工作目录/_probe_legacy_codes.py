# -*- coding: utf-8 -*-
import psycopg2
pg = psycopg2.connect(host="192.168.1.1", port=5433, user="postgres", password="root", dbname="zxplat-bak")
cur = pg.cursor()
cur.execute("""
SELECT definition FROM med_medication_definition
WHERE is_deleted = false AND definition LIKE '%老系统药品编码=%'
LIMIT 2
""")
print("--- med 老系统 ---")
for row in cur.fetchall():
    print(row[0])
    print("---")
cur.execute("""
SELECT definition FROM med_medication_definition
WHERE is_deleted = false AND definition LIKE '%院内旧编码=%' AND definition NOT LIKE '%老系统药品编码=%'
LIMIT 2
""")
print("--- med only 院内 ---")
for row in cur.fetchall():
    print(row[0])
    print("---")
cur.execute("""
SELECT description FROM adm_device_definition
WHERE is_deleted = false
LIMIT 3
""")
print("--- dev ---")
for row in cur.fetchall():
    print(row[0])
    print("---")
cur.execute("""
SELECT
  SUM(CASE WHEN description LIKE '%院内旧编码=%' THEN 1 ELSE 0 END),
  SUM(CASE WHEN description LIKE '%老系统药品编码=%' THEN 1 ELSE 0 END),
  SUM(CASE WHEN description IS NULL OR btrim(description) = '' THEN 1 ELSE 0 END)
FROM adm_device_definition WHERE is_deleted = false
""")
print("dev markers", cur.fetchone())
pg.close()
