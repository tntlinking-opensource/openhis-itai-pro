# -*- coding: utf-8 -*-
"""Extract JSON samples from converted 联调方案 markdown."""
from __future__ import annotations

import json
import re
from pathlib import Path

BASE = Path(__file__).resolve().parent
SRC = BASE / "附件：全省事前联调方案1.5.md"
OUT_DIR = BASE / "samples-31xx-joint"


def clean_cell_json(raw: str) -> str:
    s = raw.replace("<br>", "\n").replace("\xa0", " ").replace("&nbsp;", " ")
    # remove inline comments like ,(3101中jrd_id)
    s = re.sub(r",\s*\([^)]*\)", "", s)
    # keys with trailing spaces: "lv1_hosp_item_pric "
    s = re.sub(r'"([^"]+?)\s+":', lambda m: '"' + m.group(1).rstrip() + '":', s)
    return s.strip()


def try_load(blob: str):
    fixed = re.sub(r",\s*}", "}", blob)
    fixed = re.sub(r",\s*]", "]", fixed)
    return json.loads(fixed)


def main() -> None:
    src = SRC.read_text(encoding="utf-8")
    OUT_DIR.mkdir(exist_ok=True)

    blobs: list[str] = []
    for m in re.finditer(r"\| \{<br>(.*?)(?:\n\| --- \|\n|\n\| \n)", src, re.S):
        raw = "{" + m.group(1)
        raw = raw.split("\n| ---")[0]
        raw = re.sub(r"\|\s*$", "", raw.strip())
        blobs.append(clean_cell_json(raw))

    # blob index 5 may contain 3103 output + pharmacy samples concatenated
    if len(blobs) >= 6 and "4.明细审核" in blobs[5]:
        mixed = blobs[5]
        head, rest = mixed.split("4.明细审核", 1)
        blobs[5] = head.strip()
        m_in = re.search(r"4\.1.*?示例.*?\n(\{.*?\})\n4\.2", rest, re.S)
        m_out = re.search(r"4\.2.*?示例.*?\n(\{.*\})\s*$", rest, re.S)
        if m_in:
            blobs.append(clean_cell_json(m_in.group(1)))
        if m_out:
            blobs.append(clean_cell_json(m_out.group(1)))

    names = [
        "3101-input-example-1-门诊收费登记.json",
        "3101-output-example-1.json",
        "3101-input-example-2-住院执行医嘱.json",
        "3101-output-example-2.json",
        "3103-input-example.json",
        "3103-output-example.json",
        "3101-input-example-pharmacy-购药划卡.json",
        "3101-output-example-pharmacy.json",
    ]

    for i, blob in enumerate(blobs):
        name = names[i] if i < len(names) else f"sample-{i}.json"
        path = OUT_DIR / name
        try:
            obj = try_load(blob)
            path.write_text(json.dumps(obj, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
            print(f"OK  {name}")
        except Exception as exc:
            raw_path = OUT_DIR / name.replace(".json", ".raw.txt")
            raw_path.write_text(blob, encoding="utf-8")
            print(f"RAW {raw_path.name}: {exc}")


if __name__ == "__main__":
    main()
