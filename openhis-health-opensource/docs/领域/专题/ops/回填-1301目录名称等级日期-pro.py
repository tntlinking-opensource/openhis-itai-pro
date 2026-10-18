# -*- coding: utf-8 -*-
"""Backfill 1301 catalog display fields from raw_json (whale-pro, 3 passes)."""
from __future__ import annotations

import psycopg2

HOST, PORT, DB, USER, PWD = "172.16.101.2", 5432, "whale-pro", "postgres", "root"
BATCH = 5000

SETUP = r"""
CREATE OR REPLACE FUNCTION pg_temp.yb_txt(v text) RETURNS text AS $f$
  SELECT CASE
    WHEN v IS NULL THEN NULL
    WHEN btrim(v) = '' THEN NULL
    WHEN lower(btrim(v)) = 'null' THEN NULL
    WHEN btrim(v) IN ('--', '-') THEN NULL
    ELSE btrim(v)
  END;
$f$ LANGUAGE sql IMMUTABLE;
"""


def run_pass(cur, sql, last_id, batch, params_extra=()):
    cur.execute(SETUP)
    cur.execute(sql, (last_id, batch) + params_extra)
    ids = cur.fetchall()
    return [row[0] for row in ids]


NAME_SQL = r"""
WITH chunk AS (
    SELECT id
    FROM yb_national_catalog_item
    WHERE COALESCE(is_deleted, false) = false
      AND source_infno = '1301'
      AND raw_json IS NOT NULL
      AND left(btrim(raw_json), 1) = '['
      AND (
            hilist_name IS NULL
            OR btrim(hilist_name) IN ('', 'null', '--', '-')
            OR chrgitm_lv IN ('--', '-', 'null', 'NULL')
          )
      AND id > %s
    ORDER BY id
    LIMIT %s
)
UPDATE yb_national_catalog_item t
SET
    hilist_name = COALESCE(
        CASE
            WHEN pg_temp.yb_txt(t.hilist_name) IS NULL THEN COALESCE(
                pg_temp.yb_txt(t.raw_json::json->>3),
                pg_temp.yb_txt(t.raw_json::json->>7),
                CASE
                    WHEN pg_temp.yb_txt(t.raw_json::json->>1) = '无' THEN NULL
                    ELSE pg_temp.yb_txt(t.raw_json::json->>1)
                END
            )
        END,
        t.hilist_name
    ),
    chrgitm_lv = CASE
        WHEN pg_temp.yb_txt(t.chrgitm_lv) IS NULL THEN NULL
        ELSE t.chrgitm_lv
    END,
    updated_at = CURRENT_TIMESTAMP
FROM chunk
WHERE t.id = chunk.id
RETURNING t.id
"""

DATE_SQL = r"""
WITH chunk AS (
    SELECT id
    FROM yb_national_catalog_item
    WHERE COALESCE(is_deleted, false) = false
      AND source_infno = '1301'
      AND raw_json IS NOT NULL
      AND left(btrim(raw_json), 1) = '['
      AND begndate IS NULL
      AND id > %s
    ORDER BY id
    LIMIT %s
)
UPDATE yb_national_catalog_item t
SET
    begndate = COALESCE(
        t.begndate,
        CASE
            WHEN pg_temp.yb_txt(t.raw_json::json->>36) ~ '^\d{4}-\d{2}-\d{2}'
                 AND left(pg_temp.yb_txt(t.raw_json::json->>36), 4) <> '0001'
            THEN left(pg_temp.yb_txt(t.raw_json::json->>36), 10)::date
        END,
        CASE
            WHEN pg_temp.yb_txt(t.raw_json::json->>95) ~ '^\d{4}-\d{2}-\d{2}'
                 AND left(pg_temp.yb_txt(t.raw_json::json->>95), 4) <> '0001'
            THEN left(pg_temp.yb_txt(t.raw_json::json->>95), 10)::date
        END
    ),
    enddate = COALESCE(
        t.enddate,
        CASE
            WHEN pg_temp.yb_txt(t.raw_json::json->>37) ~ '^\d{4}-\d{2}-\d{2}'
                 AND left(pg_temp.yb_txt(t.raw_json::json->>37), 4) <> '0001'
            THEN left(pg_temp.yb_txt(t.raw_json::json->>37), 10)::date
        END,
        CASE
            WHEN pg_temp.yb_txt(t.raw_json::json->>96) ~ '^\d{4}-\d{2}-\d{2}'
                 AND left(pg_temp.yb_txt(t.raw_json::json->>96), 4) <> '0001'
            THEN left(pg_temp.yb_txt(t.raw_json::json->>96), 10)::date
        END
    ),
    updated_at = CURRENT_TIMESTAMP
FROM chunk
WHERE t.id = chunk.id
RETURNING t.id
"""

PINYIN_SQL = r"""
WITH chunk AS (
    SELECT id
    FROM yb_national_catalog_item
    WHERE COALESCE(is_deleted, false) = false
      AND source_infno = '1301'
      AND pinyin IS NOT NULL
      AND lower(btrim(pinyin)) IN ('null', '--', '-')
      AND id > %s
    ORDER BY id
    LIMIT %s
)
UPDATE yb_national_catalog_item t
SET pinyin = NULL, updated_at = CURRENT_TIMESTAMP
FROM chunk
WHERE t.id = chunk.id
RETURNING t.id
"""


def loop_pass(pg, name, sql):
    cur = pg.cursor()
    last_id = 0
    updated = 0
    while True:
        ids = run_pass(cur, sql, last_id, BATCH)
        if not ids:
            pg.commit()
            break
        last_id = ids[-1]
        updated += len(ids)
        pg.commit()
        print(f"{name} updated={updated} last_id={last_id}", flush=True)
    cur.close()
    print(f"{name} DONE updated={updated}", flush=True)
    return updated


def main():
    pg = psycopg2.connect(host=HOST, port=PORT, dbname=DB, user=USER, password=PWD)
    pg.autocommit = False
    n1 = loop_pass(pg, "name/lv", NAME_SQL)
    n2 = loop_pass(pg, "dates", DATE_SQL)
    n3 = loop_pass(pg, "pinyin", PINYIN_SQL)
    pg.close()
    print(f"ALL DONE name/lv={n1} dates={n2} pinyin={n3}", flush=True)


if __name__ == "__main__":
    main()
