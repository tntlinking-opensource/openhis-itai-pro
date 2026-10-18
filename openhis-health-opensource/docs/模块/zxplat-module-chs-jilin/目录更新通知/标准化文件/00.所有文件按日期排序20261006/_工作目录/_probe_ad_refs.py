# -*- coding: utf-8 -*-
import psycopg2
conn = psycopg2.connect(host="192.168.1.1", port=5433, user="postgres", password="root", dbname="zxplat-bak")
cur = conn.cursor()
cur.execute("""
    SELECT table_name, column_name
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND column_name IN ('activity_definition_id', 'activity_id')
    ORDER BY table_name
""")
refs = cur.fetchall()
print("ref cols", len(refs))
for table, column in refs:
    cur.execute(
        "SELECT COUNT(*) FROM %s WHERE %s IS NOT NULL" % (table, column)
    )
    n = cur.fetchone()[0]
    if n:
        print(table, column, n)
conn.close()
