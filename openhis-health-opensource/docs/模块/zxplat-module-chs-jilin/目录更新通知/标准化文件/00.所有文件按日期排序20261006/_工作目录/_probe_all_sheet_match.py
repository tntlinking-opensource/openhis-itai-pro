# -*- coding: utf-8 -*-
from collections import defaultdict
from openpyxl import load_workbook
import psycopg2

XLSX = r"D:\0.product\03.ZXPlat\zxplat\docs\projects\project-H22010202493\数据迁移相关\服务目录\国基医院新系统诊疗目录表.xlsx"
MR = {
    "一般医疗服务费": "1", "一般治疗操作费": "2", "护理费": "3", "综合医疗服务类其他费用": "4",
    "病理诊断费": "5", "实验室诊断费": "6", "影像学诊断费": "7", "临床诊断项目费": "8",
    "非手术治疗项目费": "9", "临床物理治疗费": "10", "手术治疗费": "11", "麻醉费": "12",
    "手术费": "13", "康复费": "14", "中医治疗费": "15", "中医骨伤": "15", "中医特殊治疗": "15",
    "中医推拿治疗": "15", "针刺与灸法": "15", "中医外治": "15", "西药费": "16", "抗菌药物费用": "17",
    "中成药费": "18", "中药饮片费": "19", "血费": "20", "其他费": "28", "其他费用": "28",
    "非手术治疗项目费 (目录节点)": "9",
}


def norm(value):
    return str(value or "").replace(" ", "").replace("\t", "").strip()


def norm_name(value):
    return str(value or "").strip()


wb = load_workbook(XLSX, read_only=True, data_only=True)
by_name = defaultdict(set)
by_code = defaultdict(set)
unknown = defaultdict(int)
for sheet in wb.sheetnames:
    ws = wb[sheet]
    rows = list(ws.iter_rows(values_only=True))
    if len(rows) < 2:
        continue
    header = [str(c or "").strip() for c in rows[0]]
    # find columns
    name_cols = [i for i, h in enumerate(header) if h in ("*诊疗名称", "项目", "项目名称")]
    code_cols = [i for i, h in enumerate(header) if h in ("医保编号", "医保编码", "项目代码", "编码")]
    mr_cols = [i for i, h in enumerate(header) if h in ("病案结算类型", "病案结算项")]
    if not mr_cols:
        # headerless sheets: 旧系统CT row0 is data. detect by known mr words in the row
        continue
    start = 1
    if header[0].startswith("*") or header[0] in ("编码", "项目代码", "项目名称"):
        start = 1
    for row in rows[start:]:
        texts = []
        for i in mr_cols:
            if i < len(row) and row[i]:
                texts.append(str(row[i]).strip())
        codes = set()
        for text in texts:
            code = MR.get(text)
            if code:
                codes.add(code)
            elif text:
                unknown[text] += 1
        if not codes:
            continue
        for i in name_cols:
            if i < len(row):
                name = norm_name(row[i])
                if name and not name.startswith("[示例]"):
                    by_name[name].update(codes)
        for i in code_cols:
            if i < len(row) and row[i]:
                raw = norm(row[i])
                if not raw or raw.startswith("[示例]"):
                    continue
                by_code[raw].update(codes)
                head = raw.split("-")[0]
                if head != raw:
                    by_code[head].update(codes)
wb.close()
print("names", len(by_name), "codes", len(by_code), "unknown", dict(unknown))

conn = psycopg2.connect(host="192.168.1.1", port=5433, user="postgres", password="root", dbname="whale-training")
cur = conn.cursor()
cur.execute("""
SELECT bus_no, name, coalesce(yb_no,'')
FROM wkf_activity_definition
WHERE is_deleted = false
  AND (mr_settlement_type_code IS NULL OR btrim(mr_settlement_type_code) = '')
""")
matched = conflict = miss = 0
miss_rows = []
for bus, name, yb in cur.fetchall():
    yb = norm(yb)
    chosen = None
    if name in by_name and len(by_name[name]) == 1:
        chosen = next(iter(by_name[name]))
    elif yb and yb in by_code and len(by_code[yb]) == 1:
        chosen = next(iter(by_code[yb]))
    elif yb:
        head = yb.split("-")[0]
        if head in by_code and len(by_code[head]) == 1:
            chosen = next(iter(by_code[head]))
    if chosen:
        matched += 1
    else:
        name_hit = name in by_name
        code_hit = yb in by_code if yb else False
        if name_hit or code_hit:
            conflict += 1
        else:
            miss += 1
            if len(miss_rows) < 15:
                miss_rows.append((bus, name, yb))
print("matched", matched, "conflict", conflict, "miss", miss)
for row in miss_rows:
    print("MISS", row)
conn.close()
