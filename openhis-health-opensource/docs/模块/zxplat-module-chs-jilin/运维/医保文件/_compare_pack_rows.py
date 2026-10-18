# -*- coding: utf-8 -*-
"""Compare canonical pack CSV row counts vs DB imported lines."""
from __future__ import annotations

import csv
import hashlib
import json
from pathlib import Path

ROOT = Path(r"D:/0.product/02.Whale/whale-doc/whale-module-chs-jilin/目录更新通知/标准化文件")
OUT = Path(r"D:/0.product/02.Whale/whale-doc/whale-module-chs-jilin/运维/医保文件/_pack_row_counts.json")


def content_hash(meta_bytes: bytes, lines_bytes: bytes) -> str:
    return hashlib.sha256(meta_bytes + b"\0" + lines_bytes).hexdigest()


def main() -> None:
    packs = []
    for d in sorted(ROOT.iterdir(), key=lambda p: p.name.lower()):
        if not d.is_dir():
            continue
        meta_p = d / "notice.meta.json"
        csv_p = d / "notice.lines.csv"
        if not meta_p.exists() or not csv_p.exists():
            packs.append({"stem": d.name, "error": "missing files"})
            continue
        meta_bytes = meta_p.read_bytes()
        lines_bytes = csv_p.read_bytes()
        meta = json.loads(meta_bytes.decode("utf-8-sig"))
        n = 0
        actions: dict[str, int] = {}
        skipped_blank_action = 0
        with csv_p.open(encoding="utf-8-sig", newline="") as f:
            for row in csv.DictReader(f):
                action = (row.get("action") or "").strip()
                if not action:
                    skipped_blank_action += 1
                    continue
                n += 1
                a = action.upper()
                actions[a] = actions.get(a, 0) + 1
        packs.append(
            {
                "stem": d.name,
                "title": meta.get("title") or d.name,
                "doc_kind": meta.get("doc_kind"),
                "category": meta.get("category"),
                "source_filename": meta.get("source_filename"),
                "csv_rows": n,
                "blank_action_skipped": skipped_blank_action,
                "actions": actions,
                "csv_bytes": csv_p.stat().st_size,
                "source_file_hash": content_hash(meta_bytes, lines_bytes),
            }
        )
    OUT.write_text(json.dumps(packs, ensure_ascii=False, indent=2), encoding="utf-8")
    print("pack_count", len(packs))
    print("total_csv_rows", sum(p.get("csv_rows", 0) for p in packs))
    print("wrote", OUT)
    for p in sorted(packs, key=lambda x: -x.get("csv_rows", 0))[:20]:
        print(f"{p['csv_rows']:6d}  {p.get('category',''):10s}  {p['stem'][:70]}")


if __name__ == "__main__":
    main()
