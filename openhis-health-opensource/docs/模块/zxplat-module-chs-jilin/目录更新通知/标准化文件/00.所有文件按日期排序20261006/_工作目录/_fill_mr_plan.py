# -*- coding: utf-8 -*-
"""对照诊疗目录表，只统计 whale-training 病案结算类型空值能否补上。不写库。"""
from collections import defaultdict
from openpyxl import load_workbook
import psycopg2

XLSX = r"D:\0.product\03.ZXPlat\zxplat\docs\projects\project-H22010202493\数据迁移相关\服务目录\国基医院新系统诊疗目录表.xlsx"
MR = {
    "一般医疗服务费": "1",
    "一般治疗操作费": "2",
    "护理费": "3",
    "综合医疗服务类其他费用": "4",
    "病理诊断费": "5",
    "实验室诊断费": "6",
    "影像学诊断费": "7",
    "临床诊断项目费": "8",
    "非手术治疗项目费": "9",
    "临床物理治疗费": "10",
    "手术治疗费": "11",
    "麻醉费": "12",
    "手术费": "13",
    "康复费": "14",
    "中医治疗费": "15",
    "中医骨伤": "15",
    "中医特殊治疗": "15",
    "中医推拿治疗": "15",
    "针刺与灸法": "15",
    "中医外治": "15",
    "西药费": "16",
    "抗菌药物费用": "17",
    "中成药费": "18",
    "中药饮片费": "19",
    "血费": "20",
    "白蛋白类制品费": "21",
    "球蛋白类制品费": "22",
    "凝血因子类制品费": "23",
    "细胞因子类制品费": "24",
    "检查用一次性医用材料费": "25",
    "治疗用一次性医用材料费": "26",
    "手术用一次性医用材料费": "27",
    "其他费": "28",
    "其他费用": "28",
}


def norm(value):
    return str(value or "").strip()


def add_hit(index, key, code, source):
    if not key:
        return
    index[key].append((code, source))


def load_file():
    wb = load_workbook(XLSX, read_only=True, data_only=True)
    ws = wb["②诊疗目录"]
    rows = ws.iter_rows(values_only=True)
    next(rows)
    header = list(next(rows))
    name_i = header.index("*诊疗名称")
    yb_i = header.index("医保编号")
    legacy_i = header.index("院内旧编码")
    mr_i = header.index("病案结算类型")
    fin_i = header.index("*财务类型")
    by_yb = defaultdict(list)
    by_legacy = defaultdict(list)
    by_name = defaultdict(list)
    unknown = defaultdict(int)
    fin_by_yb = defaultdict(set)
    for row in rows:
        name = norm(row[name_i])
        if not name or name.startswith("[示例]"):
            continue
        mr_text = norm(row[mr_i])
        fin_text = norm(row[fin_i])
        yb = norm(row[yb_i]).replace(" ", "")
        legacy = norm(row[legacy_i])
        if fin_text and yb:
            fin_by_yb[yb].add(fin_text)
        if not mr_text:
            continue
        code = MR.get(mr_text)
        if not code:
            unknown[mr_text] += 1
            continue
        add_hit(by_yb, yb, code, "目录表")
        add_hit(by_legacy, legacy, code, "目录表")
        add_hit(by_name, name, code, "目录表")
    wb.close()
    return by_yb, by_legacy, by_name, unknown, fin_by_yb


def unique_code(hits):
    codes = {code for code, _source in hits}
    if len(codes) == 1:
        return next(iter(codes))
    return None


def main():
    by_yb, by_legacy, by_name, unknown, fin_by_yb = load_file()
    print("unknown mr labels", dict(unknown))
    conn = psycopg2.connect(host="192.168.1.1", port=5433, user="postgres", password="root", dbname="whale-training")
    cur = conn.cursor()
    cur.execute("""
        SELECT a.id, a.bus_no, a.name, coalesce(a.yb_no, ''), coalesce(a.description_text, '')
        FROM wkf_activity_definition a
        WHERE a.is_deleted = false
          AND (a.mr_settlement_type_code IS NULL OR btrim(a.mr_settlement_type_code) = '')
    """)
    empty = cur.fetchall()
    print("empty mr", len(empty))
    matched = defaultdict(int)
    unmatched = []
    conflict = []
    for ad_id, bus, name, yb, desc in empty:
        yb = yb.strip().replace(" ", "")
        legacy = ""
        for line in desc.splitlines():
            if line.startswith("院内旧编码="):
                legacy = line.split("=", 1)[1].strip()
        chosen = None
        how = ""
        if yb and yb in by_yb:
            chosen = unique_code(by_yb[yb])
            how = "医保编号" if chosen else "医保编号冲突"
        if chosen is None and legacy and legacy in by_legacy:
            chosen = unique_code(by_legacy[legacy])
            how = "院内旧编码" if chosen else "院内旧编码冲突"
        if chosen is None and name in by_name:
            chosen = unique_code(by_name[name])
            how = "名称" if chosen else "名称冲突"
        if chosen:
            matched[how] += 1
        elif how.endswith("冲突"):
            conflict.append((bus, name, yb, how))
        else:
            unmatched.append((bus, name, yb))
    print("matched", dict(matched), "sum", sum(matched.values()))
    print("conflict", len(conflict))
    for row in conflict[:15]:
        print("CONFLICT", row)
    print("unmatched", len(unmatched))
    for row in unmatched[:25]:
        print("MISS", row)
    cur.execute("""
        SELECT a.yb_no, d.type_code
        FROM wkf_activity_definition a
        JOIN adm_charge_item_definition d
          ON d.instance_id = a.id AND d.instance_table = 'wkf_activity_definition'
         AND d.is_deleted = false AND d.price_type_code = 'SALE'
        WHERE a.is_deleted = false AND a.yb_no IS NOT NULL AND btrim(a.yb_no) <> ''
    """)
    fin_code = {
        "手术费": "1005", "治疗费": "1008", "检查费": "2009", "化验": "1004", "麻醉": "2001",
        "床位": "1010", "诊察费": "1012", "DR": "25", "CT": "2011", "输血费": "2003",
        "护理": "1009", "护理费": "1009", "其他费": "1013",
    }
    differ = 0
    compared = 0
    for yb, type_code in cur.fetchall():
        labels = fin_by_yb.get(yb.strip())
        if not labels or len(labels) != 1:
            continue
        compared += 1
        expect = fin_code.get(next(iter(labels)))
        if expect and expect != type_code:
            differ += 1
    print("fin compared", compared, "differ", differ)
    conn.close()


if __name__ == "__main__":
    main()
