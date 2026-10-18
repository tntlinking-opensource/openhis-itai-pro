# -*- coding: utf-8 -*-
import psycopg2

conn = psycopg2.connect(host="192.168.1.1", port=5433, user="postgres", password="root", dbname="whale-training")
cur = conn.cursor()
cur.execute("SELECT COUNT(*) FROM wkf_activity_definition WHERE is_deleted = false")
print("ad", cur.fetchone()[0])
cur.execute("""
SELECT
  SUM(CASE WHEN mr_settlement_type_code IS NULL OR btrim(mr_settlement_type_code) = '' THEN 1 ELSE 0 END),
  SUM(CASE WHEN mr_settlement_type_code IS NOT NULL AND btrim(mr_settlement_type_code) <> '' THEN 1 ELSE 0 END)
FROM wkf_activity_definition WHERE is_deleted = false
""")
print("mr empty/filled", cur.fetchone())
cur.execute("""
SELECT is_nullable FROM information_schema.columns
WHERE table_name = 'adm_charge_item_definition' AND column_name = 'type_code'
""")
print("type_code nullable", cur.fetchone())
cur.execute("""
SELECT d.price_type_code,
       SUM(CASE WHEN d.type_code IS NULL OR btrim(d.type_code) = '' THEN 1 ELSE 0 END),
       COUNT(*)
FROM adm_charge_item_definition d
JOIN wkf_activity_definition a ON a.id = d.instance_id AND a.is_deleted = false
WHERE d.is_deleted = false AND d.instance_table = 'wkf_activity_definition'
GROUP BY d.price_type_code
ORDER BY 1
""")
print("by price type empty/total", cur.fetchall())
cur.execute("""
SELECT COUNT(DISTINCT a.id)
FROM wkf_activity_definition a
JOIN adm_charge_item_definition d ON d.instance_id = a.id AND d.instance_table = 'wkf_activity_definition' AND d.is_deleted = false
WHERE a.is_deleted = false AND (d.type_code IS NULL OR btrim(d.type_code) = '')
""")
print("ad with some empty type_code", cur.fetchone()[0])
cur.execute("""
SELECT COUNT(*) FROM wkf_activity_definition a
WHERE a.is_deleted = false
  AND NOT EXISTS (
    SELECT 1 FROM adm_charge_item_definition d
    WHERE d.instance_table = 'wkf_activity_definition' AND d.instance_id = a.id AND d.is_deleted = false
      AND d.type_code IS NOT NULL AND btrim(d.type_code) <> ''
  )
""")
print("ad with no non-empty fin type", cur.fetchone()[0])
cur.execute("""
SELECT cs.name, c.code, c.display
FROM ter_concept c
JOIN ter_code_system cs ON cs.id = c.code_system_id
WHERE cs.name IN ('fin_type_code', 'mr_settlement_type_code')
  AND c.is_deleted = false AND cs.is_deleted = false
ORDER BY cs.name, c.sort_order NULLS LAST, c.code
""")
print("--- terms ---")
for row in cur.fetchall():
    print(row)
cur.execute("""
SELECT type_code, COUNT(*)
FROM adm_charge_item_definition d
JOIN wkf_activity_definition a ON a.id = d.instance_id AND a.is_deleted = false
WHERE d.is_deleted = false AND d.instance_table = 'wkf_activity_definition'
  AND d.type_code IS NOT NULL AND btrim(d.type_code) <> ''
GROUP BY type_code
ORDER BY COUNT(*) DESC
LIMIT 30
""")
print("--- existing type_code ---")
for row in cur.fetchall():
    print(row)
cur.execute("""
SELECT mr_settlement_type_code, COUNT(*)
FROM wkf_activity_definition
WHERE is_deleted = false AND mr_settlement_type_code IS NOT NULL AND btrim(mr_settlement_type_code) <> ''
GROUP BY mr_settlement_type_code
ORDER BY COUNT(*) DESC
LIMIT 30
""")
print("--- existing mr ---")
for row in cur.fetchall():
    print(row)
conn.close()
