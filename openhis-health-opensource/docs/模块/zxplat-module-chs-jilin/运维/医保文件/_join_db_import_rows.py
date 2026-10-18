# -*- coding: utf-8 -*-
"""Join pack CSV counts with DB doc/line counts and write comparison report."""
from __future__ import annotations

import json
from pathlib import Path

import psycopg2
import psycopg2.extras

PACKS = Path(r"D:/0.product/02.Whale/whale-doc/whale-module-chs-jilin/运维/医保文件/_pack_row_counts.json")
OUT = Path(r"D:/0.product/02.Whale/whale-doc/whale-module-chs-jilin/运维/医保文件/_import_row_compare.json")

DSN = dict(
    host="172.16.101.2",
    port=5432,
    dbname="whale_pro",
    user="postgres",
    password="root",
)


def main() -> None:
    packs = json.loads(PACKS.read_text(encoding="utf-8"))
    by_hash = {p["source_file_hash"]: p for p in packs if p.get("source_file_hash")}
    by_title = {}
    for p in packs:
        title = (p.get("title") or "").strip()
        if title:
            by_title.setdefault(title, []).append(p)
    by_stem = {p["stem"]: p for p in packs if p.get("stem")}

    conn = psycopg2.connect(**DSN)
    try:
        with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
            cur.execute(
                """
                SELECT
                  d.id,
                  d.tenant_id,
                  d.title,
                  d.source_filename,
                  d.source_file_hash,
                  d.doc_kind,
                  d.category,
                  d.line_count AS doc_line_count,
                  d.parse_status,
                  d.apply_status,
                  d.imported_at,
                  (SELECT COUNT(*) FROM yb_catalog_notice_line l
                    WHERE l.doc_id = d.id AND COALESCE(l.is_deleted,false) = false) AS actual_line_count
                FROM yb_catalog_notice_doc d
                WHERE COALESCE(d.is_deleted,false) = false
                ORDER BY d.imported_at NULLS LAST, d.id
                """
            )
            docs = [dict(r) for r in cur.fetchall()]
            cur.execute("SELECT COUNT(*) AS c FROM yb_catalog_notice_doc WHERE COALESCE(is_deleted,false)=false")
            doc_cnt = cur.fetchone()["c"]
            cur.execute("SELECT COUNT(*) AS c FROM yb_catalog_notice_line WHERE COALESCE(is_deleted,false)=false")
            line_cnt = cur.fetchone()["c"]
    finally:
        conn.close()

    # stringify dates
    for d in docs:
        if d.get("imported_at") is not None:
            d["imported_at"] = str(d["imported_at"])

    matched = []
    unmatched_docs = []
    used_hashes = set()

    for doc in docs:
        pack = by_hash.get(doc["source_file_hash"])
        match_via = "hash"
        if pack is None:
            # fallback title / filename
            cands = by_title.get((doc.get("title") or "").strip(), [])
            if len(cands) == 1:
                pack = cands[0]
                match_via = "title"
            else:
                stem_guess = None
                src = doc.get("source_filename") or ""
                if src.lower().endswith(".xlsx"):
                    stem_guess = src[:-5]
                elif src.lower().endswith(".xls"):
                    stem_guess = src[:-4]
                if stem_guess and stem_guess in by_stem:
                    pack = by_stem[stem_guess]
                    match_via = "source_filename"
        if pack is None:
            unmatched_docs.append(doc)
            continue
        used_hashes.add(pack["source_file_hash"])
        csv_rows = pack["csv_rows"]
        doc_lc = int(doc["doc_line_count"] or 0)
        actual = int(doc["actual_line_count"] or 0)
        matched.append(
            {
                "doc_id": doc["id"],
                "tenant_id": doc["tenant_id"],
                "title": doc["title"],
                "stem": pack["stem"],
                "category": doc.get("category") or pack.get("category"),
                "doc_kind": doc.get("doc_kind") or pack.get("doc_kind"),
                "match_via": match_via,
                "source_file_hash": doc["source_file_hash"],
                "csv_rows": csv_rows,
                "doc_line_count": doc_lc,
                "actual_line_count": actual,
                "blank_action_skipped": pack.get("blank_action_skipped", 0),
                "delta_actual_vs_csv": actual - csv_rows,
                "delta_doc_vs_csv": doc_lc - csv_rows,
                "delta_doc_vs_actual": doc_lc - actual,
                "status": (
                    "OK"
                    if actual == csv_rows and doc_lc == csv_rows
                    else "MISMATCH"
                ),
                "imported_at": doc.get("imported_at"),
            }
        )

    missing_packs = [
        p
        for p in packs
        if p.get("source_file_hash") and p["source_file_hash"] not in used_hashes
    ]

    mismatches = [m for m in matched if m["status"] != "OK"]
    ok = [m for m in matched if m["status"] == "OK"]

    report = {
        "summary": {
            "pack_dirs": len(packs),
            "pack_csv_total_rows": sum(p.get("csv_rows", 0) for p in packs),
            "db_docs": doc_cnt,
            "db_lines_actual": line_cnt,
            "db_docs_sum_line_count": sum(int(d.get("doc_line_count") or 0) for d in docs),
            "matched": len(matched),
            "ok": len(ok),
            "mismatch": len(mismatches),
            "unmatched_docs": len(unmatched_docs),
            "packs_not_in_db": len(missing_packs),
            "missing_pack_csv_rows": sum(p.get("csv_rows", 0) for p in missing_packs),
        },
        "mismatches": mismatches,
        "missing_packs": [
            {
                "stem": p["stem"],
                "title": p.get("title"),
                "category": p.get("category"),
                "csv_rows": p.get("csv_rows"),
                "source_file_hash": p.get("source_file_hash"),
            }
            for p in missing_packs
        ],
        "unmatched_docs": unmatched_docs,
        "matched_all": matched,
    }
    OUT.write_text(json.dumps(report, ensure_ascii=False, indent=2, default=str), encoding="utf-8")
    s = report["summary"]
    print("==== SUMMARY ====")
    for k, v in s.items():
        print(f"  {k}: {v}")
    print("==== MISMATCHES ====")
    for m in mismatches[:50]:
        print(
            f"doc={m['doc_id']} csv={m['csv_rows']} doc_lc={m['doc_line_count']} actual={m['actual_line_count']} "
            f"Δ={m['delta_actual_vs_csv']}  {m['stem'][:60]}"
        )
    print("==== PACKS NOT IN DB (top by rows) ====")
    for p in sorted(missing_packs, key=lambda x: -x.get("csv_rows", 0))[:30]:
        print(f"  {p['csv_rows']:6d}  {p.get('category',''):10s}  {p['stem'][:70]}")
    print("wrote", OUT)


if __name__ == "__main__":
    main()
