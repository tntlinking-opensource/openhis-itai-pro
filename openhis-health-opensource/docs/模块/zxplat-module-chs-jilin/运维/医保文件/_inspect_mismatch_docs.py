# -*- coding: utf-8 -*-
"""Inspect DB lines for mismatched docs: look for suspicious extra rows."""
from __future__ import annotations

import psycopg2
import psycopg2.extras

DOC_IDS = [335582622496329728, 335582887035277312, 335583304825704448]

conn = psycopg2.connect(
    host="172.16.101.2",
    port=5432,
    dbname="whale_pro",
    user="postgres",
    password="root",
)
try:
    with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
        for doc_id in DOC_IDS:
            print("==== doc", doc_id)
            cur.execute(
                """
                SELECT action, COUNT(*) c,
                       COUNT(*) FILTER (WHERE hilist_code IS NULL OR hilist_code='') AS empty_hilist,
                       COUNT(*) FILTER (WHERE project_code IS NULL OR project_code='') AS empty_project,
                       COUNT(*) FILTER (WHERE hilist_name IS NULL OR hilist_name='') AS empty_name,
                       COUNT(*) FILTER (WHERE warning_message IS NOT NULL AND warning_message<>'') AS with_warn
                FROM yb_catalog_notice_line
                WHERE doc_id=%s AND COALESCE(is_deleted,false)=false
                GROUP BY action ORDER BY c DESC
                """,
                (doc_id,),
            )
            for r in cur.fetchall():
                print(dict(r))
            cur.execute(
                """
                SELECT id, action, hilist_code, project_code, LEFT(hilist_name,40) AS name,
                       sheet_name, row_no, LEFT(COALESCE(warning_message,''),80) AS warn,
                       LEFT(COALESCE(payload_json,''),120) AS payload
                FROM yb_catalog_notice_line
                WHERE doc_id=%s AND COALESCE(is_deleted,false)=false
                  AND (
                    action IS NULL OR action='' OR action='UNKNOWN'
                    OR (COALESCE(hilist_code,'')='' AND COALESCE(project_code,'')='')
                    OR sheet_name IS NULL
                  )
                ORDER BY row_no NULLS LAST, id
                LIMIT 20
                """,
                (doc_id,),
            )
            rows = cur.fetchall()
            print("suspicious", len(rows))
            for r in rows[:10]:
                print(dict(r))
            # row_no duplicates / max
            cur.execute(
                """
                SELECT COUNT(*) AS n,
                       COUNT(DISTINCT row_no) AS distinct_row_no,
                       MIN(row_no) AS min_rn, MAX(row_no) AS max_rn
                FROM yb_catalog_notice_line
                WHERE doc_id=%s AND COALESCE(is_deleted,false)=false
                """,
                (doc_id,),
            )
            print("row_no_stats", dict(cur.fetchone()))
            cur.execute(
                """
                SELECT row_no, COUNT(*) c
                FROM yb_catalog_notice_line
                WHERE doc_id=%s AND COALESCE(is_deleted,false)=false
                GROUP BY row_no HAVING COUNT(*)>1
                ORDER BY c DESC LIMIT 10
                """,
                (doc_id,),
            )
            dups = cur.fetchall()
            print("dup_row_no", [dict(x) for x in dups])
finally:
    conn.close()
