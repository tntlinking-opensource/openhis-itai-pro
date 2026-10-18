# -*- coding: utf-8 -*-
"""提示词 1A：清洗 H2024.7.1 耗材 / 2024.7.8 诊疗 两个另存 .xls。"""
from __future__ import annotations

import csv
import json
import re
import shutil
from collections import Counter
from datetime import datetime, date
from pathlib import Path

import xlrd

BASE = Path(r"D:\0.product\02.Whale\whale-doc\whale-module-chs-jilin\目录更新通知")
STD_ROOT = BASE / "标准化文件"
RAW_ROOT = BASE / "原始文件"
FAIL_ROOT = RAW_ROOT / "_无法转换"

TARGETS = [
    "耗材数据更新（H2024.7.1）.xls",
    "诊疗目录新增(2024.7.8).xls",
]

CSV_FIELDS = [
    "action",
    "hilist_code",
    "project_code",
    "hilist_name",
    "begin_date",
    "end_date",
    "change_fields",
    "chrgitm_lv",
    "list_type",
    "source_sheet",
    "source_row",
    "ext_json",
]

CORE_SKIP = {
    "国家目录编码",
    "医疗目录编码",
    "目录编码",
    "医保目录名称",
    "耗材名称",
    "项目名称",
    "开始日期",
    "终止日期",
    "收费项目等级",
    "目录类别",
    "本期变更",
    "变更标记",
    "本次变更",
}
PRICE_KEYS = {"医保目录定价上限金额", "医保目录超限处理方式"}


def cell_str(v) -> str:
    if v is None or v == "":
        return ""
    if isinstance(v, float):
        if v == int(v):
            return str(int(v))
        return str(v).rstrip("0").rstrip(".") if "." in str(v) else str(v)
    s = str(v).strip()
    if s.endswith(".0") and s[:-2].lstrip("-").isdigit():
        s = s[:-2]
    return s


def to_date(v, datemode: int = 0) -> str:
    if v is None or v == "":
        return ""
    if isinstance(v, datetime):
        return v.strftime("%Y-%m-%d")
    if isinstance(v, date):
        return v.strftime("%Y-%m-%d")
    if isinstance(v, (int, float)):
        try:
            dt = xlrd.xldate_as_datetime(float(v), datemode)
            return dt.strftime("%Y-%m-%d")
        except Exception:
            return ""
    s = str(v).strip()
    if not s or s in ("--", "None", "null"):
        return ""
    m = re.match(r"^(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})", s)
    if m:
        return f"{int(m.group(1)):04d}-{int(m.group(2)):02d}-{int(m.group(3)):02d}"
    return ""


def map_chrgitm_lv(v) -> str:
    s = cell_str(v)
    if not s:
        return ""
    if s in ("1", "01", "甲"):
        return "1"
    if s in ("2", "02", "乙"):
        return "2"
    if s in ("3", "03", "丙", "自费") or "自费" in s or s.startswith("丙"):
        return "3"
    if re.fullmatch(r"0*[123]", s):
        return str(int(s))
    return ""


def sheet_action(sheet_name: str) -> str | None:
    n = sheet_name.strip()
    if n.startswith("省变更") or "省变更" in n[:6]:
        return "PROVINCE_MODIFY"
    if n.startswith("新增"):
        return "ADD"
    if n.startswith("停用"):
        return "STOP"
    if n.startswith("变更"):
        return "MODIFY"
    if "变更" in n:
        return "MODIFY"
    return None


def parse_notice_date(filename: str) -> str | None:
    dates = re.findall(r"(?:H)?(\d{4})\.(\d{1,2})\.(\d{1,2})", filename)
    if not dates:
        return None
    y, m, d = dates[-1]
    return f"{int(y):04d}-{int(m):02d}-{int(d):02d}"


def infer_category(filename: str) -> str:
    if "耗材" in filename:
        return "DEVICE"
    if "诊疗" in filename or "服务" in filename:
        return "SERVICE"
    return "OTHER"


def default_list_type(category: str) -> str:
    return {"DEVICE": "301", "SERVICE": "201", "SERVICE_F": "201"}.get(category, "")


def unique_dest(dest_dir: Path, filename: str) -> Path:
    dest = dest_dir / filename
    if not dest.exists():
        return dest
    stem = Path(filename).stem
    suffix = Path(filename).suffix
    ts = datetime.now().strftime("%Y%m%d%H%M%S")
    return dest_dir / f"{stem}_{ts}{suffix}"


def safe_stem(src: Path) -> str:
    return src.stem.strip().rstrip(". ")


def json_safe(v):
    if v is None or v == "":
        return None
    if isinstance(v, (datetime, date)):
        return to_date(v) or cell_str(v)
    if isinstance(v, bool):
        return v
    if isinstance(v, float):
        if v == int(v):
            return int(v)
        return v
    if isinstance(v, int):
        return v
    s = cell_str(v)
    return s if s != "" else None


def read_sheet_rows(sh, datemode: int):
    headers = [cell_str(sh.cell_value(0, c)) for c in range(sh.ncols)]
    headers = [h if h else f"__empty_{i}" for i, h in enumerate(headers)]
    rows = []
    for r in range(1, sh.nrows):
        raw = [sh.cell_value(r, c) for c in range(sh.ncols)]
        if all(v is None or str(v).strip() == "" for v in raw):
            continue
        row_map = {}
        for h, v in zip(headers, raw):
            if h.startswith("__empty_"):
                continue
            if h in ("开始日期", "终止日期"):
                row_map[h] = to_date(v, datemode)
            else:
                row_map[h] = cell_str(v)
        rows.append((r + 1, row_map))  # 1-based Excel row
    return headers, rows


def build_ext(row_map: dict) -> dict:
    ext = {}
    for h, v in row_map.items():
        if h in CORE_SKIP and h not in PRICE_KEYS:
            continue
        if v == "" or v is None:
            continue
        if h in CORE_SKIP and h in PRICE_KEYS:
            ext[h] = json_safe(v)
            continue
        if h in CORE_SKIP:
            continue
        ext[h] = json_safe(v)
    for pk in PRICE_KEYS:
        if pk in row_map and row_map[pk] != "" and pk not in ext:
            ext[pk] = json_safe(row_map[pk])
    return ext


def wash_one(src: Path) -> dict:
    stem = safe_stem(src)
    out_dir = STD_ROOT / stem
    category = infer_category(src.name)
    notice_date = parse_notice_date(src.name)
    wash_notes = []

    try:
        wb = xlrd.open_workbook(str(src))
    except Exception as e:
        return fail_move(src, f"xlrd 无法打开：{type(e).__name__}: {e}")

    lines: list[dict] = []
    tried = []
    for sn in wb.sheet_names():
        action = sheet_action(sn)
        tried.append(f"{sn}->{action or '?'}")
        if not action:
            wash_notes.append(f"跳过无法识别动作的 Sheet：{sn}")
            continue
        sh = wb.sheet_by_name(sn)
        _, rows = read_sheet_rows(sh, wb.datemode)
        for excel_row, row_map in rows:
            code = (
                row_map.get("国家目录编码")
                or row_map.get("医疗目录编码")
                or row_map.get("目录编码")
                or ""
            )
            if not code:
                continue
            name = (
                row_map.get("医保目录名称")
                or row_map.get("耗材名称")
                or row_map.get("项目名称")
                or ""
            )
            begin_date = row_map.get("开始日期", "")
            end_date = row_map.get("终止日期", "")
            change_fields = (
                row_map.get("本期变更")
                or row_map.get("变更标记")
                or row_map.get("本次变更")
                or ""
            )
            chrgitm_lv = map_chrgitm_lv(row_map.get("收费项目等级", ""))
            list_type = cell_str(row_map.get("目录类别", "")) or default_list_type(category)
            ext = build_ext(row_map)

            hilist_code = ""
            project_code = ""
            if category == "SERVICE":
                if action == "ADD":
                    # 01* 只进 project_code
                    project_code = code
                    hilist_code = ""
                else:
                    hilist_code = code
                    project_code = ""
            else:
                # 耗材等：hilist_code=目录编码
                hilist_code = code
                project_code = ""

            lines.append(
                {
                    "action": action,
                    "hilist_code": hilist_code,
                    "project_code": project_code,
                    "hilist_name": name,
                    "begin_date": begin_date,
                    "end_date": end_date,
                    "change_fields": change_fields,
                    "chrgitm_lv": chrgitm_lv,
                    "list_type": list_type,
                    "source_sheet": sn,
                    "source_row": str(excel_row),
                    "ext_json": json.dumps(ext, ensure_ascii=False, separators=(",", ":")),
                }
            )

    if not lines:
        return fail_move(src, f"无可转换明细行。Sheets={tried}; notes={wash_notes}")

    out_dir.mkdir(parents=True, exist_ok=True)
    for fn in ("notice.meta.json", "notice.lines.csv"):
        p = out_dir / fn
        if p.exists():
            p.unlink()

    action_counts = Counter(x["action"] for x in lines)
    note_parts = [
        "原件为用户另存可解析 .xls（此前伪xlsx/.et失败）",
        f"xlrd读取；CATALOG_DELTA/{category}",
        f"行数={len(lines)}",
        "actions=" + ",".join(f"{k}:{v}" for k, v in sorted(action_counts.items())),
    ]
    if category == "DEVICE":
        note_parts.append("hilist_code←国家目录编码；甲乙丙/01/02/03→1/2/3；限价进ext_json")
    elif category == "SERVICE":
        note_parts.append("ADD：01*仅进project_code，hilist_code留空；限价进ext_json")
    if wash_notes:
        note_parts.append("；".join(wash_notes))

    meta = {
        "doc_kind": "CATALOG_DELTA",
        "category": category,
        "title": stem,
        "source_filename": src.name,
        "notice_date": notice_date,
        "effective_date": None,
        "wash_note": "；".join(note_parts),
        "wash_tool": "cursor-ai",
    }
    (out_dir / "notice.meta.json").write_text(
        json.dumps(meta, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    with open(out_dir / "notice.lines.csv", "w", encoding="utf-8-sig", newline="") as f:
        w = csv.DictWriter(f, fieldnames=CSV_FIELDS, extrasaction="ignore")
        w.writeheader()
        for row in lines:
            w.writerow(row)

    RAW_ROOT.mkdir(parents=True, exist_ok=True)
    dest = unique_dest(RAW_ROOT, src.name)
    shutil.move(str(src), str(dest))

    return {
        "result": "OK",
        "source": src.name,
        "std_dir": str(out_dir.relative_to(BASE)),
        "moved_to": str(dest.relative_to(BASE)),
        "rows": len(lines),
        "actions": dict(action_counts),
        "category": category,
        "doc_kind": "CATALOG_DELTA",
        "notice_date": notice_date,
        "remark": meta["wash_note"],
    }


def fail_move(src: Path, reason: str) -> dict:
    FAIL_ROOT.mkdir(parents=True, exist_ok=True)
    stem = safe_stem(src)
    sub = FAIL_ROOT / stem
    if sub.exists() and any(sub.iterdir()):
        sub = FAIL_ROOT / f"{stem}_{datetime.now().strftime('%Y%m%d%H%M%S')}"
    sub.mkdir(parents=True, exist_ok=True)
    dest = sub / src.name
    if dest.exists():
        dest = unique_dest(sub, src.name)
    shutil.move(str(src), str(dest))
    (sub / "reason.txt").write_text(reason + "\n", encoding="utf-8")
    return {
        "result": "FAIL",
        "source": src.name,
        "moved_to": str(dest.relative_to(BASE)),
        "reason": reason,
    }


def main():
    results = []
    for name in TARGETS:
        src = BASE / name
        if not src.exists():
            # 再找 _无法转换
            cand = None
            for p in (BASE / "原始文件" / "_无法转换").rglob("*"):
                if p.is_file() and (p.name == name or p.stem == Path(name).stem):
                    cand = p
                    break
            if cand is None:
                results.append({"result": "FAIL", "source": name, "reason": "文件未找到"})
                continue
            src = cand
        print(f"washing: {src.name}")
        r = wash_one(src)
        results.append(r)
        print(json.dumps(r, ensure_ascii=False, indent=2))

    out = BASE / "_wash_2xls_1a_summary.json"
    out.write_text(json.dumps(results, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print("summary ->", out)


if __name__ == "__main__":
    main()
