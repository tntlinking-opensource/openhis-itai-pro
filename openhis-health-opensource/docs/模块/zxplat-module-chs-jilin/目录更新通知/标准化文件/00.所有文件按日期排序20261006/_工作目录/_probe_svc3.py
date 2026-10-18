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
    SELECT TABLE_NAME FROM information_schema.TABLES
    WHERE TABLE_SCHEMA='areahis_jlcc'
      AND (TABLE_NAME LIKE '%unit%' OR TABLE_NAME LIKE '%dict%' OR TABLE_NAME LIKE '%UNIT%' OR TABLE_NAME LIKE '%DICT%')
    """
)
print("tables", [r[0] for r in cur.fetchall()])
cur.execute(
    """
    SELECT bi.YP_CODE, bi.YP_NAME, bi.YB_CODE, bi.PRICE, bi.YP_UNIT_ID, bi.defaultunit, bi.prcunt_type, ci.ITEM_NAME
    FROM b_item bi
    INNER JOIN b_charge_item ci ON ci.CHARGE_ITEM_ID = bi.CHARGE_ITEM_ID
    WHERE IFNULL(bi.IS_VOID,0)=0 AND ci.ITEM_NAME='国临手术代码30'
    LIMIT 5
    """
)
print("--- 国临 sample ---")
for row in cur.fetchall():
    print(row)
cur.execute(
    """
    SELECT bi.YP_UNIT_ID, bi.defaultunit, bi.prcunt_type, COUNT(*)
    FROM b_item bi
    INNER JOIN b_charge_item ci ON ci.CHARGE_ITEM_ID = bi.CHARGE_ITEM_ID
    WHERE IFNULL(bi.IS_VOID,0)=0 AND ci.ITEM_TYPE IN (3,4,6,7) AND ci.ITEM_NAME <> '国临手术代码30'
    GROUP BY bi.YP_UNIT_ID, bi.defaultunit, bi.prcunt_type
    ORDER BY COUNT(*) DESC
    LIMIT 25
    """
)
print("--- unit ids ---")
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
    WHERE cs.name IN ('activity_type_code','activity_category_code','fin_type_code','chrgitm_lv')
      AND c.is_deleted = false AND cs.is_deleted = false
    ORDER BY cs.name, c.sort_order NULLS LAST, c.code
    """
)
print("--- terms ---")
cur_name = None
for name, code, display in pc.fetchall():
    if name != cur_name:
        print("##", name)
        cur_name = name
    print(code, display)
pc.execute(
    """
    SELECT
      SUM(CASE WHEN children_json IS NOT NULL AND btrim(children_json) NOT IN ('','[]','null') THEN 1 ELSE 0 END),
      SUM(CASE WHEN children_flag IS TRUE THEN 1 ELSE 0 END)
    FROM wkf_activity_definition WHERE is_deleted = false
    """
)
print("json_flag", pc.fetchone())
pc.execute("SELECT COUNT(*) FROM wkf_activity_definition_children WHERE is_deleted = false")
print("child_rows", pc.fetchone()[0])
pc.execute(
    """
    SELECT p.bus_no, p.yb_no, p.name, c.children_num, c.children_name, ch.bus_no, ch.yb_no, left(coalesce(p.children_json,''), 180)
    FROM wkf_activity_definition_children c
    JOIN wkf_activity_definition p ON p.id = c.activity_id AND p.is_deleted = false
    LEFT JOIN wkf_activity_definition ch ON ch.id = c.children_id
    WHERE c.is_deleted = false
    LIMIT 6
    """
)
print("--- children ---")
for row in pc.fetchall():
    print(row)
pg.close()
bak = psycopg2.connect(host="192.168.1.1", port=5433, user="postgres", password="root", dbname="zxplat-bak")
bc = bak.cursor()
bc.execute("SELECT COUNT(*) FROM wkf_activity_definition WHERE is_deleted = false")
print("bak_ad", bc.fetchone()[0])
bc.execute("SELECT COUNT(*) FROM wkf_activity_definition_children WHERE is_deleted = false")
print("bak_children", bc.fetchone()[0])
bak.close()
