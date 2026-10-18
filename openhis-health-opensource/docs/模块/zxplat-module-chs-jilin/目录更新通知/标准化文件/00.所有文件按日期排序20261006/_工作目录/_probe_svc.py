# -*- coding: utf-8 -*-
import json
import pymysql
import psycopg2

mysql = pymysql.connect(
    host="192.168.1.106", port=3306, user="root", password="mycqyl_20191",
    database="areahis_jlcc", charset="utf8mb4",
)
cur = mysql.cursor()
cur.execute(
    """
    SELECT COUNT(*)
    FROM b_item bi
    INNER JOIN b_charge_item ci ON ci.CHARGE_ITEM_ID = bi.CHARGE_ITEM_ID
    WHERE IFNULL(bi.IS_VOID, 0) = 0
      AND ci.ITEM_TYPE IN (3, 4, 6, 7)
    """
)
print("old_count", cur.fetchone()[0])
cur.execute(
    """
    SELECT ci.ITEM_TYPE, COUNT(*)
    FROM b_item bi
    INNER JOIN b_charge_item ci ON ci.CHARGE_ITEM_ID = bi.CHARGE_ITEM_ID
    WHERE IFNULL(bi.IS_VOID, 0) = 0
      AND ci.ITEM_TYPE IN (3, 4, 6, 7)
    GROUP BY ci.ITEM_TYPE
    """
)
print("by_type", cur.fetchall())
cur.execute(
    """
    SELECT COLUMN_NAME FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA='areahis_jlcc' AND TABLE_NAME='b_item'
      AND (COLUMN_NAME LIKE '%UNIT%' OR COLUMN_NAME LIKE '%SPEC%'
           OR COLUMN_NAME LIKE '%TYPE%' OR COLUMN_NAME IN
           ('YP_CODE','YP_NAME','PRICE','YB_CODE','dirclass','ITEM_ID','IS_VOID','CHARGE_ITEM_ID'))
    ORDER BY ORDINAL_POSITION
    """
)
print("cols", [r[0] for r in cur.fetchall()])
cur.execute(
    """
    SELECT ci.ITEM_TYPE, ci.ITEM_NAME, COUNT(*)
    FROM b_item bi
    INNER JOIN b_charge_item ci ON ci.CHARGE_ITEM_ID = bi.CHARGE_ITEM_ID
    WHERE IFNULL(bi.IS_VOID, 0) = 0
      AND ci.ITEM_TYPE IN (3, 4, 6, 7)
    GROUP BY ci.ITEM_TYPE, ci.ITEM_NAME
    ORDER BY ci.ITEM_TYPE, COUNT(*) DESC
    LIMIT 40
    """
)
print("charge_items")
for row in cur.fetchall():
    print(row)
cur.execute(
    """
    SELECT bi.YP_CODE, bi.YP_NAME, bi.UNIT, bi.dirclass, bi.YB_CODE, ci.ITEM_TYPE, ci.ITEM_NAME
    FROM b_item bi
    INNER JOIN b_charge_item ci ON ci.CHARGE_ITEM_ID = bi.CHARGE_ITEM_ID
    WHERE IFNULL(bi.IS_VOID, 0) = 0
      AND ci.ITEM_TYPE IN (3, 4, 6, 7)
    LIMIT 8
    """
)
print("sample")
for row in cur.fetchall():
    print(row)
mysql.close()

pg = psycopg2.connect(host="192.168.1.1", port=5433, user="postgres", password="root", dbname="whale-training")
pc = pg.cursor()
pc.execute(
    """
    SELECT cs.name, c.code, c.display
    FROM ter_concept c
    JOIN ter_code_system cs ON cs.id = c.code_system_id
    WHERE cs.name IN ('activity_type_code','activity_category_code','fin_type_code','chrgitm_lv','med_chrgitm_type','mr_settlement_type_code')
      AND c.is_deleted = false AND cs.is_deleted = false AND c.is_active = true
    ORDER BY cs.name, c.sort_order, c.code
    """
)
print("--- terms ---")
for row in pc.fetchall():
    print(row)
pc.execute(
    """
    SELECT COUNT(*) FROM wkf_activity_definition
    WHERE is_deleted = false
      AND children_json IS NOT NULL
      AND btrim(children_json) NOT IN ('', '[]', 'null')
    """
)
print("children_json_rows", pc.fetchone()[0])
pc.execute(
    """
    SELECT COUNT(*) FROM wkf_activity_definition_children WHERE is_deleted = false
    """
)
print("child_table_rows", pc.fetchone()[0])
pc.execute(
    """
    SELECT id, bus_no, yb_no, name, children_flag, left(children_json, 400)
    FROM wkf_activity_definition
    WHERE is_deleted = false
      AND children_json IS NOT NULL
      AND btrim(children_json) NOT IN ('', '[]', 'null')
    LIMIT 3
    """
)
print("--- json sample ---")
for row in pc.fetchall():
    print(row)
pc.execute("SELECT COUNT(*) FROM wkf_activity_definition WHERE is_deleted = false")
print("training_ad", pc.fetchone()[0])
pg.close()

bak = psycopg2.connect(host="192.168.1.1", port=5433, user="postgres", password="root", dbname="zxplat-bak")
bc = bak.cursor()
bc.execute("SELECT COUNT(*) FROM wkf_activity_definition WHERE is_deleted = false")
print("bak_ad", bc.fetchone()[0])
bc.execute(
    """
    SELECT code, display FROM ter_concept c
    JOIN ter_code_system cs ON cs.id = c.code_system_id
    WHERE cs.name = 'unit_code' AND c.is_deleted = false AND c.is_active = true
    ORDER BY c.sort_order NULLS LAST, c.code
    LIMIT 80
    """
)
print("--- units ---")
for row in bc.fetchall():
    print(row)
bak.close()
