# -*- coding: utf-8 -*-
"""Export national YB catalog (药品/诊疗/耗材) to Excel from whale-pro."""
from datetime import date, datetime
from pathlib import Path

import psycopg2
import xlsxwriter

OUT_DIR = Path(__file__).resolve().parent
HOST, PORT, DB, USER, PWD = "172.16.101.2", 5432, "whale-pro", "postgres", "root"

COLS = [
    ("list_type", "目录类别"),
    ("hilist_code", "医保目录编码"),
    ("hilist_name", "医保目录名称"),
    ("chrgitm_lv", "医保等级"),
    ("lmt_used_flag", "限制使用标志"),
    ("begndate", "开始日期"),
    ("enddate", "结束日期"),
    ("vali_flag", "有效标志"),
    ("spec", "规格"),
    ("dosform", "剂型"),
    ("aprvno", "批准文号"),
    ("pinyin", "拼音"),
    ("prodentp_name", "生产企业"),
    ("source_infno", "来源接口"),
    ("updt_time", "更新时间"),
]
DB_COLS = [c[0] for c in COLS]
HEADERS = [c[1] for c in COLS]
LIST_TYPE_LABEL = {
    "101": "西药中成药",
    "102": "中药饮片",
    "201": "医疗服务",
    "301": "医用耗材",
}
# Excel sheet max ~1048576 incl header
SHEET_MAX_DATA = 1_000_000


def cell(v):
    if v is None:
        return ""
    if isinstance(v, datetime):
        return v.strftime("%Y-%m-%d %H:%M:%S")
    if isinstance(v, date):
        return v.strftime("%Y-%m-%d")
    return v


def export_file(conn, filename, list_types):
    path = OUT_DIR / filename
    print(f">>> writing {path.name} types={list_types}", flush=True)
    wb = xlsxwriter.Workbook(
        str(path), {"constant_memory": True, "strings_to_urls": False}
    )
    header_fmt = wb.add_format({"bold": True})

    for lt in list_types:
        label = LIST_TYPE_LABEL.get(lt, lt)
        print(f"  fetching list_type={lt} ({label}) ...", flush=True)
        cur = conn.cursor(name=f"exp_{lt}")
        cur.itersize = 5000
        cur.execute(
            f"""
            SELECT {", ".join(DB_COLS)}
            FROM yb_national_catalog_item
            WHERE COALESCE(is_deleted, false) = false
              AND list_type = %s
            ORDER BY hilist_code
            """,
            (lt,),
        )
        sheet_idx = 1
        row_in_sheet = 0
        total = 0
        ws = None

        def new_sheet(idx):
            name = f"{label}" if idx == 1 else f"{label}-{idx}"
            name = name[:31]
            s = wb.add_worksheet(name)
            for col, h in enumerate(HEADERS):
                s.write(0, col, h, header_fmt)
            return s

        for row in cur:
            if ws is None or row_in_sheet >= SHEET_MAX_DATA:
                if ws is not None:
                    sheet_idx += 1
                ws = new_sheet(sheet_idx)
                row_in_sheet = 0
            row_in_sheet += 1
            total += 1
            for col, val in enumerate(row):
                ws.write(row_in_sheet, col, cell(val))
            if total % 100000 == 0:
                print(f"    {lt}: {total} rows...", flush=True)
        cur.close()
        if total == 0:
            ws = new_sheet(1)
        print(f"  done {lt}: {total} rows, sheets={sheet_idx}", flush=True)

    wb.close()
    size_mb = path.stat().st_size / (1024 * 1024)
    print(f"<<< {path.name} OK ({size_mb:.1f} MB)", flush=True)
    return path


def main():
    conn = psycopg2.connect(
        host=HOST, port=PORT, dbname=DB, user=USER, password=PWD
    )
    # named (server-side) cursor 需要事务；只读即可
    conn.set_session(readonly=True, autocommit=False)
    try:
        files = [
            ("医保药品目录.xlsx", ["101", "102"]),
            ("医保诊疗目录.xlsx", ["201"]),
            ("医保耗材目录.xlsx", ["301"]),
        ]
        for name, types in files:
            export_file(conn, name, types)
    finally:
        conn.close()
    print("ALL DONE", flush=True)


if __name__ == "__main__":
    main()
