# -*- coding: utf-8 -*-
"""给「当前可用服务项目.xlsx」补「类型」列。类型取自各通知行的医疗收费项目类别。"""
import csv
import json
from copy import copy
from pathlib import Path

from openpyxl import load_workbook
from openpyxl.utils import get_column_letter

BASE = Path(
    r"D:\0.product\03.ZXPlat\zxplat\docs\模块\zxplat-module-chs-jilin"
    r"\目录更新通知\标准化文件\00.所有文件按日期排序20261006"
)
XLSX = Path(
    r"D:\0.product\03.ZXPlat\zxplat\docs\projects\project-H22010202493"
    r"\数据迁移相关\服务目录\当前可用服务项目.xlsx"
)

# 国家医疗收费项目类别。展示去掉「费」，与清单里「手术」这类说法一致。
FEE_TYPE = {
    "01": "床位",
    "1": "床位",
    "02": "诊察",
    "2": "诊察",
    "03": "检查",
    "3": "检查",
    "04": "化验",
    "4": "化验",
    "05": "治疗",
    "5": "治疗",
    "06": "手术",
    "6": "手术",
    "07": "护理",
    "7": "护理",
    "08": "卫生材料",
    "8": "卫生材料",
    "12": "一般诊疗",
    "13": "挂号",
    "14": "其他",
}
TEXT_TYPE = {
    "手术费": "手术",
    "治疗费": "治疗",
    "检查费": "检查",
    "诊察费": "诊察",
    "护理费": "护理",
    "床位费": "床位",
    "其他费": "其他",
    "化验费": "化验",
    "挂号费": "挂号",
    "卫生材料费": "卫生材料",
    "一般诊疗费": "一般诊疗",
}


def pack_no(folder_name):
    head = folder_name.split(".", 1)[0]
    if head.isdigit():
        return int(head)
    return -1


def row_type(ext):
    text = TEXT_TYPE.get(str(ext.get("归集口径") or "").strip())
    if text:
        return text
    text = TEXT_TYPE.get(str(ext.get("费用类型口径") or "").strip())
    if text:
        return text
    code = str(ext.get("医疗收费项目类别") or "").strip()
    if code:
        return FEE_TYPE.get(code, "类别" + code)
    return ""


def load_types():
    # key -> (pack, type)。同一编码以较晚通知为准。
    best = {}
    unknown = {}
    for folder in BASE.iterdir():
        lines = folder / "notice.lines.csv"
        if not lines.exists():
            continue
        pack = pack_no(folder.name)
        with lines.open(encoding="utf-8-sig", newline="") as handle:
            for row in csv.DictReader(handle):
                try:
                    ext = json.loads(row.get("ext_json") or "{}")
                except json.JSONDecodeError:
                    continue
                kind = row_type(ext)
                if not kind:
                    continue
                if kind.startswith("类别"):
                    unknown[kind] = unknown.get(kind, 0) + 1
                for raw in (row.get("project_code"), row.get("hilist_code")):
                    key = (raw or "").strip()
                    if not key:
                        continue
                    prev = best.get(key)
                    if prev is None or pack >= prev[0]:
                        best[key] = (pack, kind)
    return best, unknown


def main():
    best, unknown = load_types()
    wb = load_workbook(XLSX)
    ws = wb["可用项目"]
    headers = [cell.value for cell in ws[1]]
    if "类型" in headers:
        raise SystemExit("类型列已存在，停止以免重复插入")
    name_col = headers.index("项目名称") + 1
    insert_at = name_col + 1
    ws.insert_cols(insert_at)
    src = ws.cell(1, name_col)
    dst = ws.cell(1, insert_at, "类型")
    if src.has_style:
        dst.font = copy(src.font)
        dst.fill = copy(src.fill)
        dst.alignment = copy(src.alignment)
        dst.border = copy(src.border)
    code_col = headers.index("项目代码") + 1
    hilist_col = headers.index("医保目录编码") + 1
    counts = {}
    missing = []
    for row_idx in range(2, ws.max_row + 1):
        code = ws.cell(row_idx, code_col).value
        hilist = ws.cell(row_idx, hilist_col).value
        found = None
        for raw in (code, hilist):
            key = str(raw or "").strip()
            if key and key in best:
                found = best[key][1]
                break
        ws.cell(row_idx, insert_at, found or None)
        if found:
            counts[found] = counts.get(found, 0) + 1
        else:
            name = ws.cell(row_idx, name_col).value
            missing.append((code, hilist, name))
    ws.column_dimensions[get_column_letter(insert_at)].width = 12
    last_col = get_column_letter(ws.max_column)
    ws.auto_filter.ref = "A1:%s%d" % (last_col, ws.max_row)
    note = wb["说明"]
    note.append(["类型", "取自各通知的医疗收费项目类别（归集口径/费用类型口径优先），同一编码以较晚通知为准。取值：手术、治疗、检查、化验、诊察、护理、床位、其他、一般诊疗。"])
    wb.save(XLSX)
    print("filled", sum(counts.values()), "missing", len(missing))
    print("counts", sorted(counts.items(), key=lambda item: -item[1]))
    print("unknown_codes", unknown)
    for item in missing[:20]:
        print("MISS", item)


if __name__ == "__main__":
    main()
