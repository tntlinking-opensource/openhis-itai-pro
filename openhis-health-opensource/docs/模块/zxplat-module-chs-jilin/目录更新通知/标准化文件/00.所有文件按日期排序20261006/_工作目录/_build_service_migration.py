# -*- coding: utf-8 -*-
"""导出老系统服务项目，对照现行可用目录做违规校验，并生成诊疗目录导入文件与套餐同步 SQL。"""
import hashlib
from collections import defaultdict
from decimal import Decimal, ROUND_HALF_UP
from pathlib import Path

import pymysql
import psycopg2
from openpyxl import Workbook, load_workbook
from openpyxl.styles import Alignment, Font, PatternFill
from openpyxl.utils import get_column_letter

OUT_DIR = Path(
    r"D:\0.product\03.ZXPlat\zxplat\docs\projects\project-H22010202493\数据迁移相关\服务目录"
)
CATALOG_XLSX = OUT_DIR / "当前可用服务项目.xlsx"

COLUMN_HEADERS = [
    "诊疗名称", "目录分类", "业务类型", "销售/计价单位", "财务类型",
    "成本价", "零售价", "最高零售价",
    "医保标志", "医保编号", "医保类别", "医保对码标记", "医保等级",
    "病案结算类型", "院内旧编码", "描述",
]
REQUIRED = {"诊疗名称", "目录分类", "业务类型", "销售/计价单位", "财务类型", "零售价"}
FINGERPRINT = hashlib.sha256("|".join(COLUMN_HEADERS).encode("utf-8")).hexdigest()
CHUNK = 2800
MONEY = Decimal("0.01")

CATEGORY = {
    "化验": "检验", "治疗": "治疗", "手术": "手术", "护理": "护理项目",
    "麻醉": "麻醉", "输血费": "输血", "膳食": "膳食", "床位": "床位服务",
    "诊断": "诊查费", "检查": "检查", "DR": "检查", "CT": "检查", "自备医嘱": "嘱托",
}
ACTIVITY_BY_CHARGE = {
    "化验": "检验", "治疗": "其他", "手术": "手术", "护理": "护理",
    "麻醉": "其他", "输血费": "其他", "膳食": "其他", "床位": "其他",
    "诊断": "诊查", "检查": "检查", "DR": "检查", "CT": "检查", "自备医嘱": "其他",
}
FIN_BY_CHARGE = {
    "化验": "化验", "治疗": "治疗", "手术": "手术", "护理": "护理",
    "麻醉": "麻醉", "输血费": "输血费", "膳食": "膳食", "床位": "床位",
    "诊断": "诊察费", "检查": "检查", "DR": "DR", "CT": "CT", "自备医嘱": "自备医嘱",
}
ACTIVITY_BY_OFFICIAL = {
    "化验": "检验", "检查": "检查", "手术": "手术", "治疗": "其他",
    "诊察": "诊查", "护理": "护理", "床位": "其他", "其他": "其他", "一般诊疗": "诊查",
}
FIN_BY_CATEGORY = {
    "检验": "化验", "治疗": "治疗", "手术": "手术", "护理项目": "护理", "护理级别": "护理",
    "麻醉": "麻醉", "输血": "输血费", "膳食": "膳食", "床位服务": "床位",
    "诊查费": "诊察费", "诊断费": "诊察费", "检查": "检查", "嘱托": "自备医嘱",
}
LV_OFFICIAL = {"甲": "甲类", "乙": "乙类", "丙": "丙类"}
LV_OLD = {"甲": "甲类", "乙": "乙类", "自费": "自费"}
HEADER_FILL = PatternFill("solid", fgColor="1F4E79")
HEADER_FONT = Font(color="FFFFFF", bold=True)
FAIL_FILL = PatternFill("solid", fgColor="F4CCCC")
WARN_FILL = PatternFill("solid", fgColor="FFF2CC")


def money(value):
    if value is None or value == "":
        return None
    number = Decimal(str(value))
    return number.quantize(MONEY, rounding=ROUND_HALF_UP)


def norm_code(value):
    return str(value or "").strip().replace(" ", "")


def norm_name(value):
    text = str(value or "").strip()
    for ch in " \t\r\n（）()":
        text = text.replace(ch, "")
    return text


def sql_text(value):
    return "'" + str(value or "").replace("'", "''") + "'"


def load_catalog():
    wb = load_workbook(CATALOG_XLSX, read_only=True, data_only=True)
    ws = wb["可用项目"]
    rows = ws.iter_rows(values_only=True)
    header = [str(cell) if cell is not None else "" for cell in next(rows)]
    idx = {name: header.index(name) for name in header}
    index = defaultdict(list)
    for raw in rows:
        item = {
            "口径": raw[idx["口径"]],
            "项目代码": norm_code(raw[idx["项目代码"]]),
            "医保目录编码": norm_code(raw[idx["医保目录编码"]]),
            "项目名称": str(raw[idx["项目名称"]] or "").strip(),
            "类型": str(raw[idx["类型"]] or "").strip(),
            "收费等级": str(raw[idx["收费等级"]] or "").strip(),
            "定价上限": money(raw[idx["定价上限"]]),
            "地方编码": norm_code(raw[idx["地方编码"]]),
        }
        if item["定价上限"] is not None and item["定价上限"] <= 0:
            item["定价上限"] = None
        seen = set()
        for key in (item["项目代码"], item["医保目录编码"], item["地方编码"]):
            if key and key not in seen:
                index[key].append(item)
                seen.add(key)
    wb.close()
    return index


def pick_catalog(index, code):
    hits = index.get(code) or []
    if not hits:
        return None

    def score(item):
        has_cap = 1 if item["定价上限"] is not None else 0
        prefer_f = 1 if item["口径"] == "F服务" else 0
        return (has_cap, prefer_f)

    return max(hits, key=score)


def catalog_yb(item):
    if item["项目代码"]:
        return item["项目代码"]
    return item["医保目录编码"]


def load_old_items():
    conn = pymysql.connect(
        host="192.168.1.106", port=3306, user="root", password="mycqyl_20191",
        database="areahis_jlcc", charset="utf8mb4",
    )
    cur = conn.cursor()
    cur.execute(
        """
        SELECT
            bi.YP_CODE,
            bi.YP_NAME,
            IFNULL((
                SELECT p.PRICE
                FROM b_item_price p
                WHERE p.ITEM_ID = bi.ITEM_ID
                ORDER BY p.MODIFY_TIME DESC, p.ITEM_PRICE_ID DESC
                LIMIT 1
            ), bi.PRICE) AS price,
            IFNULL(bi.YB_CODE, '') AS yb,
            CASE
                WHEN bi.dirclass IN ('1', '01') THEN '甲'
                WHEN bi.dirclass IN ('2', '02') THEN '乙'
                WHEN bi.dirclass IN ('3', '03', '4') THEN '自费'
                ELSE IFNULL(bi.dirclass, '')
            END AS lv,
            ci.ITEM_NAME,
            IFNULL(bc.name, '') AS unit_name
        FROM b_item bi
        INNER JOIN b_charge_item ci ON ci.CHARGE_ITEM_ID = bi.CHARGE_ITEM_ID
        LEFT JOIN basic_code bc ON bc.id = bi.YP_UNIT_ID
        WHERE IFNULL(bi.IS_VOID, 0) = 0
          AND ci.ITEM_TYPE IN (3, 4, 6, 7)
        ORDER BY bi.YP_CODE
        """
    )
    rows = []
    for code, name, price, yb, lv, charge, unit_name in cur.fetchall():
        rows.append({
            "项目编码": str(code or "").strip(),
            "项目名称": str(name or "").strip(),
            "最新价格": money(price) or Decimal("0.00"),
            "医保码": norm_code(yb),
            "等级": str(lv or "").strip(),
            "收费分类": str(charge or "").strip(),
            "单位": str(unit_name or "").strip(),
        })
    conn.close()
    return rows


def load_training():
    conn = psycopg2.connect(
        host="192.168.1.1", port=5433, user="postgres", password="root", dbname="whale-training",
    )
    cur = conn.cursor()
    cur.execute(
        """
        SELECT character_maximum_length
        FROM information_schema.columns
        WHERE table_name = 'wkf_activity_definition' AND column_name = 'children_json'
        """
    )
    children_json_len = cur.fetchone()[0]
    cur.execute(
        """
        SELECT cs.name, c.code, c.display
        FROM ter_concept c
        JOIN ter_code_system cs ON cs.id = c.code_system_id
        WHERE cs.name IN ('activity_category_code', 'activity_type_code', 'unit_code', 'fin_type_code', 'chrgitm_lv')
          AND c.is_deleted = false AND cs.is_deleted = false
        """
    )
    concepts = defaultdict(dict)
    unit_labels = set()
    fin_labels = set()
    for name, code, display in cur.fetchall():
        concepts[name][str(code)] = display
        if name == "unit_code":
            unit_labels.add(display)
        if name == "fin_type_code":
            fin_labels.add(display)
    cur.execute(
        """
        SELECT p.bus_no, coalesce(p.yb_no, ''), p.name,
               ch.bus_no, coalesce(ch.yb_no, ''), ch.name,
               coalesce(c.children_num, 1), coalesce(c.children_name, ch.name)
        FROM wkf_activity_definition_children c
        JOIN wkf_activity_definition p ON p.id = c.activity_id AND p.is_deleted = false
        JOIN wkf_activity_definition ch ON ch.id = c.children_id AND ch.is_deleted = false
        WHERE c.is_deleted = false
        ORDER BY p.bus_no, ch.bus_no
        """
    )
    edges = []
    for row in cur.fetchall():
        edges.append({
            "parent_bus": row[0], "parent_yb": norm_code(row[1]), "parent_name": row[2] or "",
            "child_bus": row[3], "child_yb": norm_code(row[4]), "child_name": row[5] or "",
            "children_num": int(row[6] or 1), "children_name": row[7] or "",
        })
    graph_bus = set()
    for edge in edges:
        graph_bus.add(edge["parent_bus"])
        graph_bus.add(edge["child_bus"])
    cur.execute(
        """
        SELECT bus_no, coalesce(yb_no, ''), name, category_code, type_enum,
               package_unit_code, chrgitm_lv
        FROM wkf_activity_definition
        WHERE is_deleted = false AND bus_no = ANY(%s)
        """,
        (list(graph_bus),),
    )
    ads = {}
    for bus, yb, name, category, type_enum, unit, lv in cur.fetchall():
        ads[bus] = {
            "bus": bus,
            "yb": norm_code(yb),
            "name": str(name or "").strip(),
            "category": concepts["activity_category_code"].get(str(category), "其他"),
            "activity": concepts["activity_type_code"].get(str(type_enum), "其他"),
            "unit": concepts["unit_code"].get(str(unit), "次"),
            "lv": concepts["chrgitm_lv"].get(str(lv), ""),
        }
    cur.execute(
        """
        SELECT instance_id, price_type_code, price_value
        FROM adm_charge_item_definition
        WHERE is_deleted = false
          AND instance_table = 'wkf_activity_definition'
          AND price_type_code IN ('SALE', 'PURCHASE', 'LIMIT')
          AND instance_id IN (
              SELECT id FROM wkf_activity_definition
              WHERE is_deleted = false AND bus_no = ANY(%s)
          )
        """,
        (list(graph_bus),),
    )
    prices = defaultdict(dict)
    id_to_bus = {}
    cur2 = conn.cursor()
    cur2.execute(
        "SELECT id, bus_no FROM wkf_activity_definition WHERE is_deleted = false AND bus_no = ANY(%s)",
        (list(graph_bus),),
    )
    for ad_id, bus in cur2.fetchall():
        id_to_bus[ad_id] = bus
    for instance_id, price_type, price_value in cur.fetchall():
        bus = id_to_bus.get(instance_id)
        if bus:
            prices[bus][price_type] = money(price_value)
    conn.close()
    return edges, ads, prices, unit_labels, fin_labels, children_json_len


def judge(old, catalog):
    issues = []
    level = "通过"
    if catalog is None:
        if not old["医保码"]:
            issues.append("无医保码")
        else:
            issues.append("未收入现行目录")
        return "失败", "；".join(issues)
    if norm_name(old["项目名称"]) != norm_name(catalog["项目名称"]):
        issues.append("名称不一致")
        level = "警告"
    old_lv = old["等级"]
    cat_lv = catalog["收费等级"]
    if old_lv and cat_lv:
        if old_lv == "自费" and cat_lv == "丙":
            issues.append("自费对应丙类")
            level = "警告"
        elif old_lv != cat_lv and not (old_lv == "自费" and cat_lv == "丙"):
            if not (old_lv in ("甲", "乙") and old_lv == cat_lv):
                if old_lv != cat_lv:
                    issues.append("等级不一致")
                    level = "失败"
    elif not old_lv and cat_lv:
        issues.append("老系统无等级")
        level = "警告"
    cap = catalog["定价上限"]
    if cap is not None and old["最新价格"] > cap:
        issues.append("超定价上限")
        level = "失败"
    return level, "；".join(issues)


def write_table(path, sheet_name, headers, data_rows, fills=None):
    wb = Workbook()
    ws = wb.active
    ws.title = sheet_name
    ws.append(headers)
    for cell in ws[1]:
        cell.fill = HEADER_FILL
        cell.font = HEADER_FONT
        cell.alignment = Alignment(horizontal="center")
    for offset, row in enumerate(data_rows):
        ws.append(row)
        fill = None if fills is None else fills[offset]
        if fill is not None:
            for cell in ws[offset + 2]:
                cell.fill = fill
    ws.auto_filter.ref = "A1:%s%d" % (get_column_letter(len(headers)), ws.max_row)
    ws.freeze_panes = "A2"
    for col in range(1, len(headers) + 1):
        ws.column_dimensions[get_column_letter(col)].width = 18
    ws.column_dimensions["B"].width = 36
    wb.save(path)


def write_import_file(path, data_rows):
    wb = Workbook()
    ws = wb.active
    ws.title = "②诊疗目录"
    ws.append(["基础信息", "", "", "", "", "计价", "", "", "医保与说明"])
    header = []
    for name in COLUMN_HEADERS:
        header.append("*" + name if name in REQUIRED else name)
    ws.append(header)
    example = [""] * 16
    example[0] = "[示例]血常规"
    example[1] = "检验"
    example[2] = "检验"
    example[3] = "次"
    example[4] = "化验"
    example[5] = 10
    example[6] = 25
    ws.append(example)
    example2 = [""] * 16
    example2[0] = "[示例]胸部CT"
    example2[1] = "检查"
    example2[2] = "检查"
    example2[3] = "次"
    example2[4] = "检查"
    example2[6] = 180
    ws.append(example2)
    for row in data_rows:
        values = [row[name] for name in COLUMN_HEADERS]
        ws.append(values)
        excel_row = ws.max_row
        for col in (6, 7, 8):
            cell = ws.cell(excel_row, col)
            if cell.value is not None and cell.value != "":
                cell.number_format = "0.00"
    sys_sheet = wb.create_sheet("_sys")
    sys_sheet.append(["TEMPLATE_CODE", "SVC_CATALOG_IMPORT"])
    sys_sheet.append(["TEMPLATE_VERSION", "v1.0"])
    sys_sheet.append(["COLUMN_FINGERPRINT", FINGERPRINT])
    wb.save(path)


def resolve_unit(raw, unit_labels, unknown):
    text = str(raw or "").strip()
    alias = {
        "人次": "次", "人/次": "次", "次.": "次", "每个": "个", "每例": "例",
    }
    text = alias.get(text, text)
    if text in unit_labels:
        return text
    if text:
        unknown[text] = unknown.get(text, 0) + 1
    return "次"


def trim_desc(token, yp_code):
    legacy = ("\n院内旧编码=" + yp_code) if yp_code else ""
    budget = 255 - len(legacy)
    if budget < 1:
        budget = 1
    return token[:budget]


def main():
    print("load catalog")
    catalog_index = load_catalog()
    print("load old")
    old_rows = load_old_items()
    print("old", len(old_rows))
    print("load training children")
    edges, ads, prices, unit_labels, fin_labels, children_json_len = load_training()
    print("edges", len(edges), "graph", len(ads), "children_json_len", children_json_len)
    for label in ("检验", "检查", "手术", "护理", "其他", "诊查", "次", "化验", "治疗"):
        if label not in unit_labels and label not in fin_labels and label not in ACTIVITY_BY_OFFICIAL.values():
            pass
    missing_fin = sorted({v for v in list(FIN_BY_CHARGE.values()) + list(FIN_BY_CATEGORY.values()) if v not in fin_labels})
    missing_unit_fallback = "次" not in unit_labels
    print("missing_fin", missing_fin, "missing_ci", missing_unit_fallback)

    charge_rows = []
    dict_rows = []
    for old in old_rows:
        if old["收费分类"] == "国临手术代码30":
            dict_rows.append(old)
        else:
            charge_rows.append(old)

    yb_counts = defaultdict(int)
    for old in charge_rows:
        if old["医保码"]:
            yb_counts[old["医保码"]] += 1

    check_rows = []
    import_candidates = []
    unknown_units = {}
    for old in charge_rows:
        catalog = pick_catalog(catalog_index, old["医保码"]) if old["医保码"] else None
        level, issues = judge(old, catalog)
        extra = []
        if old["医保码"] and yb_counts[old["医保码"]] > 1:
            extra.append("同码多条")
            if level == "通过":
                level = "警告"
        problem = "；".join([part for part in [issues, "；".join(extra)] if part])
        check_rows.append({
            "old": old,
            "catalog": catalog,
            "level": level,
            "problem": problem,
        })
        capped = False
        retail = old["最新价格"]
        limit_price = None
        if catalog is not None and catalog["定价上限"] is not None:
            limit_price = catalog["定价上限"]
            if retail > limit_price:
                retail = limit_price
                capped = True
        activity = ACTIVITY_BY_CHARGE.get(old["收费分类"], "其他")
        if catalog is not None and catalog["类型"] in ACTIVITY_BY_OFFICIAL:
            activity = ACTIVITY_BY_OFFICIAL[catalog["类型"]]
        yb_no = ""
        yb_flag = "否"
        match_flag = "否"
        lv_text = LV_OLD.get(old["等级"], "")
        if catalog is not None:
            yb_no = catalog_yb(catalog)
            yb_flag = "是"
            match_flag = "是"
            lv_text = LV_OFFICIAL.get(catalog["收费等级"], lv_text)
        fin = FIN_BY_CHARGE.get(old["收费分类"], "其他费")
        if fin not in fin_labels:
            fin = "其他费"
        import_candidates.append({
            "诊疗名称": old["项目名称"] or old["项目编码"],
            "目录分类": CATEGORY.get(old["收费分类"], "其他"),
            "业务类型": activity,
            "销售/计价单位": resolve_unit(old["单位"], unit_labels, unknown_units),
            "财务类型": fin,
            "成本价": retail,
            "零售价": retail,
            "最高零售价": limit_price if limit_price is not None else "",
            "医保标志": yb_flag,
            "医保编号": yb_no,
            "医保类别": "",
            "医保对码标记": match_flag,
            "医保等级": lv_text,
            "病案结算类型": "",
            "院内旧编码": old["项目编码"],
            "描述": "",
            "_level": level,
            "_capped": capped,
            "_yb_old": old["医保码"],
            "_name_key": norm_name(old["项目名称"]),
            "_train_bus": "",
            "_yp": old["项目编码"],
            "_matched": catalog is not None,
        })

    by_yb = defaultdict(list)
    by_name = defaultdict(list)
    for row in import_candidates:
        if row["_yb_old"]:
            by_yb[row["_yb_old"]].append(row)
        if row["_name_key"]:
            by_name[row["_name_key"]].append(row)

    def claim(ad):
        if ad["yb"]:
            hits = by_yb.get(ad["yb"], [])
            if len(hits) == 1 and not hits[0]["_train_bus"]:
                hits[0]["_train_bus"] = ad["bus"]
                return True
        hits = by_name.get(norm_name(ad["name"]), [])
        if len(hits) == 1 and not hits[0]["_train_bus"]:
            hits[0]["_train_bus"] = ad["bus"]
            return True
        return False

    added = 0
    for bus, ad in ads.items():
        if claim(ad):
            continue
        sale = prices.get(bus, {}).get("SALE")
        retail = sale if sale is not None else Decimal("0.00")
        limit_price = prices.get(bus, {}).get("LIMIT")
        if limit_price is not None and limit_price <= 0:
            limit_price = None
        if limit_price is not None and retail > limit_price:
            retail = limit_price
        purchase = prices.get(bus, {}).get("PURCHASE")
        if purchase is None:
            purchase = retail
        fin = FIN_BY_CATEGORY.get(ad["category"], "其他费")
        if fin not in fin_labels:
            fin = "其他费"
        unit = ad["unit"] if ad["unit"] in unit_labels else "次"
        catalog = pick_catalog(catalog_index, ad["yb"]) if ad["yb"] else None
        yb_no = catalog_yb(catalog) if catalog is not None else ad["yb"]
        row = {
            "诊疗名称": ad["name"] or bus,
            "目录分类": ad["category"] if ad["category"] else "其他",
            "业务类型": ad["activity"] or "其他",
            "销售/计价单位": unit,
            "财务类型": fin,
            "成本价": purchase,
            "零售价": retail,
            "最高零售价": limit_price if limit_price is not None else "",
            "医保标志": "是" if yb_no else "否",
            "医保编号": yb_no,
            "医保类别": "",
            "医保对码标记": "是" if catalog is not None else "否",
            "医保等级": ad["lv"],
            "病案结算类型": "",
            "院内旧编码": "",
            "描述": "",
            "_level": "套餐",
            "_capped": False,
            "_yb_old": ad["yb"],
            "_name_key": norm_name(ad["name"]),
            "_train_bus": bus,
            "_yp": "",
            "_matched": catalog is not None,
        }
        import_candidates.append(row)
        added += 1
    print("package rows added", added, "unknown units", unknown_units)

    groups = defaultdict(list)
    for row in import_candidates:
        groups[(row["诊疗名称"], row["目录分类"])].append(row)
    for key, group in groups.items():
        if len(group) == 1:
            continue
        for row in group:
            marker = row["_yp"] or row["_train_bus"]
            suffix = "（%s）" % marker
            base = row["诊疗名称"]
            if len(base) + len(suffix) > 255:
                base = base[:255 - len(suffix)]
            row["诊疗名称"] = base + suffix

    compliant = []
    self_pay = []
    for row in import_candidates:
        token = ("训练库编码=%s;" % row["_train_bus"]) if row["_train_bus"] else ""
        if row["_capped"]:
            token += "老价已按定价上限压价;"
        row["描述"] = trim_desc(token, row["_yp"])
        if row["最高零售价"] == "":
            row["最高零售价"] = None
        # 未对上现行目录、且不是套餐成员的，另存为自费文件。超限但已对上目录的留在主导入并压价。
        if (not row["_matched"]) and row["_train_bus"] == "":
            self_pay.append(row)
        else:
            compliant.append(row)

    def dump_chunks(rows, stem):
        paths = []
        if not rows:
            return paths
        for index in range(0, len(rows), CHUNK):
            part = rows[index:index + CHUNK]
            seq = index // CHUNK + 1
            path = OUT_DIR / ("%s_%d.xlsx" % (stem, seq))
            write_import_file(path, part)
            paths.append((path, len(part)))
        return paths

    export_path = OUT_DIR / "老系统服务项目.xlsx"
    write_table(
        export_path,
        "服务项目",
        ["项目编码", "项目名称", "最新价格", "医保码", "等级", "收费分类", "单位"],
        [[old["项目编码"], old["项目名称"], float(old["最新价格"]), old["医保码"], old["等级"], old["收费分类"], old["单位"]] for old in old_rows],
    )

    summary = defaultdict(int)
    for item in check_rows:
        summary[item["level"]] += 1
        for part in item["problem"].split("；"):
            if part:
                summary["问题:" + part] += 1
    check_path = OUT_DIR / "服务项目违规校验.xlsx"
    wb = Workbook()
    summary_sheet = wb.active
    summary_sheet.title = "汇总"
    summary_sheet.append(["项", "数量"])
    lines = [
        ("老系统未作废服务项目（检验/诊疗/检查/普通）", len(old_rows)),
        ("其中收费项目", len(charge_rows)),
        ("其中国临手术代码字典（不导入、不进违规明细）", len(dict_rows)),
        ("通过", summary["通过"]),
        ("警告", summary["警告"]),
        ("失败", summary["失败"]),
    ]
    for key in sorted(summary):
        if key.startswith("问题:"):
            lines.append((key.replace("问题:", ""), summary[key]))
    lines.append(("导入文件中的合规及套餐行", len(compliant)))
    lines.append(("另存的未对码自费行", len(self_pay)))
    lines.append(("whale-training 套餐子项行", len(edges)))
    lines.append(("whale-training 套餐涉及项目", len(ads)))
    lines.append(("children_json 列宽", children_json_len))
    for line in lines:
        summary_sheet.append(list(line))
    summary_sheet.append([])
    summary_sheet.append(["规则", "无医保码、未收入现行目录、等级不一致、超定价上限记失败。名称不一致、自费对应丙类、老系统无等级、同码多条记警告。国临手术代码30是手术操作字典，几乎无价格，不进入诊疗导入。超限行导入时零售价压到定价上限。未对码且不是套餐成员的项目另存为自费文件，不进主导入文件。"])
    detail = wb.create_sheet("明细")
    detail_header = ["结论", "问题", "项目编码", "项目名称", "收费分类", "最新价格", "医保码", "老系统等级", "目录名称", "目录类型", "目录等级", "定价上限"]
    detail.append(detail_header)
    for cell in detail[1]:
        cell.fill = HEADER_FILL
        cell.font = HEADER_FONT
    fills = {"失败": FAIL_FILL, "警告": WARN_FILL}
    for item in check_rows:
        old = item["old"]
        catalog = item["catalog"]
        detail.append([
            item["level"], item["problem"], old["项目编码"], old["项目名称"], old["收费分类"],
            float(old["最新价格"]), old["医保码"], old["等级"],
            catalog["项目名称"] if catalog else "",
            catalog["类型"] if catalog else "",
            catalog["收费等级"] if catalog else "",
            float(catalog["定价上限"]) if catalog and catalog["定价上限"] is not None else "",
        ])
        fill = fills.get(item["level"])
        if fill is not None:
            for cell in detail[detail.max_row]:
                cell.fill = fill
    detail.auto_filter.ref = "A1:L%d" % detail.max_row
    detail.freeze_panes = "A2"
    detail.column_dimensions["D"].width = 36
    detail.column_dimensions["I"].width = 36
    wb.save(check_path)

    main_files = dump_chunks(compliant, "诊疗目录导入_国基")
    self_files = dump_chunks(self_pay, "诊疗目录导入_国基_未对码自费")

    sql_path = OUT_DIR / "同步套餐子项到zxplat-bak.sql"
    value_lines = []
    for edge in edges:
        value_lines.append(
            "(%s, %s, %s, %s, %s, %s, %s)" % (
                sql_text(edge["parent_bus"]), sql_text(edge["parent_yb"]), sql_text(edge["parent_name"]),
                sql_text(edge["child_bus"]), sql_text(edge["child_yb"]), sql_text(edge["child_name"]),
                int(edge["children_num"]),
            )
        )
    values_sql = ",\n".join(value_lines)
    sql = """-- 国基：把 whale-training 已维护的套餐子项同步到 zxplat-bak
-- 事实在子表 wkf_activity_definition_children（whale-training 中 children_json 为空）。
-- children_json 列是 varchar(%s)，放得下的写紧凑快照，放不下的保持空，展开以子表为准。
-- 先导入「诊疗目录导入_国基_*.xlsx」，再在同一会话整文件执行。只允许 zxplat-bak。

DO $$
BEGIN
    IF current_database() <> 'zxplat-bak' THEN
        RAISE EXCEPTION '当前库是 %% ，本脚本只允许在 zxplat-bak 执行', current_database();
    END IF;
    IF (SELECT COUNT(*) FROM wkf_activity_definition WHERE is_deleted = false) = 0 THEN
        RAISE EXCEPTION '诊疗目录为空，请先导入诊疗目录 Excel';
    END IF;
END $$;

BEGIN;

CREATE TEMP TABLE svc_pkg (
    parent_bus text NOT NULL,
    parent_yb text NOT NULL,
    parent_name text NOT NULL,
    child_bus text NOT NULL,
    child_yb text NOT NULL,
    child_name text NOT NULL,
    children_num integer NOT NULL
) ON COMMIT DROP;

INSERT INTO svc_pkg (parent_bus, parent_yb, parent_name, child_bus, child_yb, child_name, children_num)
VALUES
%s;

CREATE TEMP TABLE svc_ad ON COMMIT DROP AS
SELECT id,
       name,
       coalesce(yb_no, '') AS yb_no,
       coalesce(description_text, '') AS description_text
FROM wkf_activity_definition
WHERE is_deleted = false;

CREATE TEMP TABLE svc_resolved ON COMMIT DROP AS
SELECT m.*,
       parent_hit.id AS parent_id,
       child_hit.id AS child_id
FROM svc_pkg m
LEFT JOIN LATERAL (
    SELECT d.id
    FROM svc_ad d
    WHERE d.description_text LIKE '%%训练库编码=' || m.parent_bus || ';%%'
       OR (m.parent_yb <> '' AND d.yb_no = m.parent_yb AND (SELECT COUNT(*) FROM svc_ad x WHERE x.yb_no = m.parent_yb) = 1)
       OR (m.parent_name <> '' AND d.name = m.parent_name AND (SELECT COUNT(*) FROM svc_ad x WHERE x.name = m.parent_name) = 1)
    ORDER BY CASE WHEN d.description_text LIKE '%%训练库编码=' || m.parent_bus || ';%%' THEN 0 ELSE 1 END, d.id
    LIMIT 1
) parent_hit ON true
LEFT JOIN LATERAL (
    SELECT d.id
    FROM svc_ad d
    WHERE d.description_text LIKE '%%训练库编码=' || m.child_bus || ';%%'
       OR (m.child_yb <> '' AND d.yb_no = m.child_yb AND (SELECT COUNT(*) FROM svc_ad x WHERE x.yb_no = m.child_yb) = 1)
       OR (m.child_name <> '' AND d.name = m.child_name AND (SELECT COUNT(*) FROM svc_ad x WHERE x.name = m.child_name) = 1)
    ORDER BY CASE WHEN d.description_text LIKE '%%训练库编码=' || m.child_bus || ';%%' THEN 0 ELSE 1 END, d.id
    LIMIT 1
) child_hit ON true;

UPDATE wkf_activity_definition_children c
SET is_deleted = true,
    deleted_at = CURRENT_TIMESTAMP,
    deleted_by = 1,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 1
WHERE c.is_deleted = false
  AND c.activity_id IN (SELECT parent_id FROM svc_resolved WHERE parent_id IS NOT NULL AND child_id IS NOT NULL);

INSERT INTO wkf_activity_definition_children (
    id, tenant_id, created_at, updated_at, created_by, updated_by,
    is_deleted, version, activity_id, children_id, children_num, children_name
)
SELECT (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::bigint * 1000
       + ROW_NUMBER() OVER (ORDER BY parent_id, child_id),
       1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1, 1,
       false, 0, parent_id, child_id, children_num, left(child_name, 255)
FROM svc_resolved
WHERE parent_id IS NOT NULL AND child_id IS NOT NULL;

UPDATE wkf_activity_definition p
SET children_flag = true,
    children_json = CASE
        WHEN length(snapshot.js) <= 255 THEN snapshot.js
        ELSE NULL
    END,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 1
FROM (
    SELECT c.activity_id,
           json_agg(json_build_object('childrenId', c.children_id, 'childrenNum', c.children_num) ORDER BY c.id)::text AS js
    FROM wkf_activity_definition_children c
    WHERE c.is_deleted = false
      AND c.activity_id IN (SELECT parent_id FROM svc_resolved WHERE parent_id IS NOT NULL AND child_id IS NOT NULL)
    GROUP BY c.activity_id
) snapshot
WHERE p.id = snapshot.activity_id;

SELECT '已匹配子项' AS 项, COUNT(*) AS 数量
FROM svc_resolved
WHERE parent_id IS NOT NULL AND child_id IS NOT NULL
UNION ALL
SELECT '未匹配', COUNT(*)
FROM svc_resolved
WHERE parent_id IS NULL OR child_id IS NULL;

SELECT parent_bus, parent_name, child_bus, child_name,
       CASE WHEN parent_id IS NULL THEN '父项未找到' ELSE '' END AS 父项,
       CASE WHEN child_id IS NULL THEN '子项未找到' ELSE '' END AS 子项
FROM svc_resolved
WHERE parent_id IS NULL OR child_id IS NULL
ORDER BY parent_bus, child_bus;

COMMIT;
""" % (str(children_json_len), values_sql)
    # The SQL above is executed later by psql, so LIKE patterns must keep a single %.
    # This generator file is not executed as SQL. Write the psql text with single percents.
    sql = sql.replace("%%", "%")
    # Restore the RAISE EXCEPTION placeholder that also used %%.
    sql = sql.replace("当前库是 % ，", "当前库是 %s ，".replace("%s", "%"))
    sql_path.write_text(sql, encoding="utf-8")
    print("export", export_path)
    print("check", check_path)
    print("main", main_files)
    print("self", self_files)
    print("sql", sql_path)
    print("compliant", len(compliant), "self_pay", len(self_pay))
    print("summary", dict(summary))


if __name__ == "__main__":
    main()
