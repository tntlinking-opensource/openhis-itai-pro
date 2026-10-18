# -*- coding: utf-8 -*-
import pymysql
import psycopg2

mysql = pymysql.connect(
    host="192.168.1.106", port=3306, user="root", password="mycqyl_20191",
    database="areahis_jlcc", charset="utf8mb4",
)
cur = mysql.cursor()
cur.execute(
    """
    SELECT ci.ITEM_NAME,
           COUNT(*) cnt,
           SUM(CASE WHEN IFNULL(bi.YB_CODE,'') <> '' THEN 1 ELSE 0 END) has_yb,
           SUM(CASE WHEN IFNULL(bi.PRICE,0) > 0 THEN 1 ELSE 0 END) has_price
    FROM b_item bi
    INNER JOIN b_charge_item ci ON ci.CHARGE_ITEM_ID = bi.CHARGE_ITEM_ID
    WHERE IFNULL(bi.IS_VOID, 0) = 0
      AND ci.ITEM_TYPE IN (3, 4, 6, 7)
    GROUP BY ci.ITEM_NAME
    ORDER BY cnt DESC
    """
)
print("--- by charge name ---")
for row in cur.fetchall():
    print(row)
cur.execute(
    """
    SELECT TABLE_NAME FROM information_schema.TABLES
    WHERE TABLE_SCHEMA='areahis_jlcc'
      AND (TABLE_NAME LIKE '%unit%' OR TABLE_NAME LIKE '%UNIT%')
    """
)
print("unit_tables", [r[0] for r in cur.fetchall()])
cur.execute(
    """
    SELECT u.UNIT_NAME, COUNT(*)
    FROM b_item bi
    INNER JOIN b_charge_item ci ON ci.CHARGE_ITEM_ID = bi.CHARGE_ITEM_ID
    LEFT JOIN b_unit u ON u.UNIT_ID = bi.YP_UNIT_ID
    WHERE IFNULL(bi.IS_VOID, 0) = 0
      AND ci.ITEM_TYPE IN (3, 4, 6, 7)
      AND ci.ITEM_NAME <> '国临手术代码30'
    GROUP BY u.UNIT_NAME
    ORDER BY COUNT(*) DESC
    LIMIT 30
    """
)
print("--- units excl 国临 ---")
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
    WHERE cs.name IN ('activity_type_code','activity_category_code','fin_type_code','chrgitm_lv','med_chrgitm_type')
      AND c.is_deleted = false AND cs.is_deleted = false AND c.is_active = true
    ORDER BY cs.name, c.sort_order NULLS LAST, c.code
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
pc.execute("SELECT COUNT(*) FROM wkf_activity_definition_children WHERE is_deleted = false")
print("child_table_rows", pc.fetchone()[0])
pc.execute(
    """
    SELECT id, bus_no, yb_no, name, children_flag, left(coalesce(children_json,''), 500)
    FROM wkf_activity_definition
    WHERE is_deleted = false
      AND (
        (children_json IS NOT NULL AND btrim(children_json) NOT IN ('', '[]', 'null'))
        OR children_flag = true
      )
    LIMIT 5
    """
)
print("--- parent sample ---")
for row in pc.fetchall():
    print(row)
pc.execute(
    """
    SELECT c.activity_id, c.children_id, c.children_num, c.children_name,
           p.yb_no, p.bus_no, p.name, ch.yb_no, ch.bus_no, ch.name
    FROM wkf_activity_definition_children c
    JOIN wkf_activity_definition p ON p.id = c.activity_id
    JOIN wkf_activity_definition ch ON ch.id = c.children_id
    WHERE c.is_deleted = false
    LIMIT 8
    """
)
print("--- child join ---")
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
    WHERE cs.name = 'unit_code' AND c.is_deleted = false AND coalesce(c.is_active, true) = true
    ORDER BY c.sort_order NULLS LAST, c.code
    """
)
print("--- units ---")
for row in bc.fetchall():
    print(row[0], row[1])
bak.close()
