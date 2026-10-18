# -*- coding: utf-8 -*-
"""从老体检「项目」JSON 生成 Whale PEIS 体检项目导入 SQL。"""
from __future__ import annotations

import json
import re
from pathlib import Path

SRC = Path(r"C:\Users\Administrator\Desktop\体检项目json.txt")
OUT_DIR = Path(r"D:\0.product\02.Whale\whale-doc\health\peis-import")
OUT_DIR.mkdir(parents=True, exist_ok=True)

# 预留 ID 段（勿与线上雪花冲突；执行前可按院点改）
AD_ID_BASE = 911_200_000_000_000_000
CID_ID_BASE = 911_201_000_000_000_000
TENANT_ID = 1
CREATED_BY = 1

# 老系统适用性别 UUID（从样例反推）
UUID_MALE = "519d4cc8-a962-4995-821e-073b0f59fb80"
UUID_FEMALE = "1ad1e6db-5c56-4b00-997f-4cb5e4d40314"

LAB_DEPTS = {"检验科"}
EXAM_DEPTS = {"影像科", "彩超室", "内镜科", "特检科", "电诊室", "电测听"}


def sql_str(v: object | None) -> str:
    if v is None:
        return "NULL"
    s = str(v)
    s = s.replace("'", "''")
    return f"'{s}'"


def sql_num(v: object | None) -> str:
    if v is None or v == "":
        return "NULL"
    return str(float(v))


def map_gender(syxb: str | None) -> str:
    raw = (syxb or "").replace(" ", "")
    parts = [p for p in raw.split(",") if p]
    has_m = UUID_MALE in parts
    has_f = UUID_FEMALE in parts
    if has_m and has_f:
        return "both"
    if has_m:
        return "male"
    if has_f:
        return "female"
    return "both"


def map_type(lx_name: str | None, ks_name: str | None) -> tuple[str, str]:
    """返回 (ActivityType.code, fin_type_code)。"""
    lx = lx_name or ""
    ks = ks_name or ""
    if lx == "PACS模式" or ks in EXAM_DEPTS:
        return "2", "3"  # 检查 / 检查费
    if ks in LAB_DEPTS:
        return "1", "4"  # 检验 / 化验费
    if "检验" in ks:
        return "1", "4"
    if any(x in ks for x in ("彩超", "影像", "CT", "DR", "内镜", "超声")):
        return "2", "3"
    return "5", "14"  # 其他 / 其他费


def sale_price(dj, yhj) -> float:
    try:
        y = float(yhj or 0)
    except Exception:
        y = 0.0
    try:
        d = float(dj or 0)
    except Exception:
        d = 0.0
    return y if y > 0 else d


def main() -> None:
    data = json.loads(SRC.read_text(encoding="utf-8"))
    rows = data["data"]["list"]
    assert len(rows) == data["data"]["count"]

    staging_lines: list[str] = []
    ad_lines: list[str] = []
    cid_lines: list[str] = []
    gap_rows: list[str] = []

    staging_lines.append(
        """-- 老体检项目导入 · 生成自 体检项目json.txt
-- 目标：wkf_activity_definition (category=PEIS) + adm_charge_item_definition (SALE)
-- 注意：
-- 1) 执行前请确认 ID 段未占用；org_id 需按科室名回填
-- 2) 明细(mx)/小项绑定不在本脚本（items 为空，需另导「明细」JSON）
-- 3) 适用性别/接口码/优惠价双价 当前主表无对位字段，落 staging 备查

BEGIN;

CREATE TABLE IF NOT EXISTS tmp_peis_legacy_project (
  legacy_id           varchar(64) PRIMARY KEY,
  activity_id         int8 NOT NULL,
  bus_no              varchar(64) NOT NULL,
  name                varchar(255) NOT NULL,
  py_str              varchar(255),
  sort_num            int4,
  dept_name           varchar(128),
  dept_code           varchar(64),
  lx_name             varchar(64),
  type_enum           varchar(8),
  fin_type_code       varchar(32),
  gender_code         varchar(16),
  price_dj            numeric(19,6),
  price_yhj           numeric(19,6),
  price_sale          numeric(19,6),
  pricing_flag        boolean,
  jkcode              varchar(128),
  jkname              varchar(255),
  jkjcff              varchar(255),
  mx_legacy_ids       text,
  remark              text
);
TRUNCATE tmp_peis_legacy_project;
"""
    )

    for i, r in enumerate(rows, start=1):
        ad_id = AD_ID_BASE + i
        cid_id = CID_ID_BASE + i
        code = (r.get("code") or f"LEGACY_{r['id']}").strip()
        name = (r.get("name") or "").strip()
        py = (r.get("zjf") or "").strip() or None
        ks = r.get("ks") or {}
        ks_name = ks.get("name")
        ks_code = ks.get("code")
        lx_name = (r.get("lx") or {}).get("name")
        type_enum, fin_type = map_type(lx_name, ks_name)
        gender = map_gender(r.get("syxb"))
        dj = r.get("dj") or 0
        yhj = r.get("yhj") or 0
        sale = sale_price(dj, yhj)
        isfy = ((r.get("isfy") or {}).get("name") or "是") == "是"
        jkcode = (r.get("jkcode") or "").strip() or None
        jkname = (r.get("jkname") or "").strip() or None
        jkjcff = (r.get("jkjcff") or "").strip() or None
        mx = (r.get("mx") or "").strip() or None
        bz = (r.get("bz") or "").strip() or None
        sx = r.get("sx")

        staging_lines.append(
            "INSERT INTO tmp_peis_legacy_project ("
            "legacy_id, activity_id, bus_no, name, py_str, sort_num, dept_name, dept_code, "
            "lx_name, type_enum, fin_type_code, gender_code, price_dj, price_yhj, price_sale, "
            "pricing_flag, jkcode, jkname, jkjcff, mx_legacy_ids, remark) VALUES ("
            f"{sql_str(r['id'])}, {ad_id}, {sql_str(code)}, {sql_str(name)}, {sql_str(py)}, "
            f"{sx if sx is not None else 'NULL'}, {sql_str(ks_name)}, {sql_str(ks_code)}, "
            f"{sql_str(lx_name)}, {sql_str(type_enum)}, {sql_str(fin_type)}, {sql_str(gender)}, "
            f"{sql_num(dj)}, {sql_num(yhj)}, {sql_num(sale)}, "
            f"{'TRUE' if isfy else 'FALSE'}, {sql_str(jkcode)}, {sql_str(jkname)}, {sql_str(jkjcff)}, "
            f"{sql_str(mx)}, {sql_str(bz)});"
        )

        # description 暂存：科室/性别/接口，便于 UI 排查（非业务字段）
        desc_parts = [
            f"legacy={r['id']}",
            f"dept={ks_name or '-'}",
            f"gender={gender}",
        ]
        if jkcode:
            desc_parts.append(f"jk={jkcode}")
        if lx_name:
            desc_parts.append(f"lx={lx_name}")
        desc = "; ".join(desc_parts)

        ad_lines.append(
            "INSERT INTO wkf_activity_definition ("
            "id, tenant_id, created_at, updated_at, created_by, updated_by, "
            "is_deleted, version, category_code, bus_no, name, py_str, "
            "type_enum, status_enum, description_text, org_id, children_flag, pricing_flag"
            ") VALUES ("
            f"{ad_id}, {TENANT_ID}, NOW(), NOW(), {CREATED_BY}, {CREATED_BY}, "
            f"FALSE, 0, 'PEIS', {sql_str(code)}, {sql_str(name)}, {sql_str(py)}, "
            f"{sql_str(type_enum)}, 'ACTIVE', {sql_str(desc)}, NULL, FALSE, "
            f"{'TRUE' if isfy else 'FALSE'}"
            ") ON CONFLICT (id) DO UPDATE SET "
            "name = EXCLUDED.name, py_str = EXCLUDED.py_str, type_enum = EXCLUDED.type_enum, "
            "status_enum = EXCLUDED.status_enum, description_text = EXCLUDED.description_text, "
            "pricing_flag = EXCLUDED.pricing_flag, is_deleted = FALSE, updated_at = NOW();"
        )

        # 仅 isfy=是 且有价时写 SALE；0 元也写（免费项）
        if isfy:
            cid_lines.append(
                "INSERT INTO adm_charge_item_definition ("
                "id, tenant_id, created_at, updated_at, created_by, updated_by, "
                "is_deleted, version, instance_table, instance_id, charge_name, status_enum, "
                "org_id, type_code, price_type_code, price_value, priority"
                ") VALUES ("
                f"{cid_id}, {TENANT_ID}, NOW(), NOW(), {CREATED_BY}, {CREATED_BY}, "
                f"FALSE, 0, 'wkf_activity_definition', {ad_id}, {sql_str(name)}, 'ACTIVE', "
                f"NULL, {sql_str(fin_type)}, 'SALE', {sql_num(sale)}, 0"
                ") ON CONFLICT (id) DO UPDATE SET "
                "charge_name = EXCLUDED.charge_name, price_value = EXCLUDED.price_value, "
                "type_code = EXCLUDED.type_code, status_enum = 'ACTIVE', is_deleted = FALSE, "
                "updated_at = NOW();"
            )

        if gender != "both":
            gap_rows.append(f"| {code} | {name} | 适用性别={gender} | AD 无 gender 字段 |")
        if jkcode:
            gap_rows.append(f"| {code} | {name} | 接口码={jkcode}/{jkname} | AD 无 jkcode；需院点 LIS/PACS 对码 |")

    # bus_no unique: prefer ON CONFLICT on bus_no if unique index exists — use delete+insert guard
    header = f"""-- =============================================================================
-- PEIS 老体检「项目」→ Whale 体检项目 导入 SQL
-- 源：体检项目json.txt（{len(rows)} 条）
-- 生成：peis-import/gen_peis_project_import.py
-- =============================================================================
-- 映射摘要：
--   code/name/zjf     → bus_no / name / py_str
--   lx+ks             → type_enum（1检验/2检查/5其他）+ fin_type（4化验/3检查/14其他）
--   dj/yhj            → SALE 价（优惠价优先，否则原价）
--   isfy              → pricing_flag
--   ks                → 仅写入 staging/description；org_id 需回填
--   syxb              → staging.gender_code（male/female/both）
--   jkcode/jkname     → staging（主表无字段）
--   mx                → staging（小项绑定另导）
-- =============================================================================

"""

    footer = """
-- 按科室名回填执行科室（名称需与 adm_organization.name 一致，按院点改）
-- UPDATE wkf_activity_definition a
-- SET org_id = o.id, updated_at = NOW()
-- FROM tmp_peis_legacy_project t
-- JOIN adm_organization o
--   ON o.name = t.dept_name AND o.is_deleted = FALSE AND o.tenant_id = 1
-- WHERE a.id = t.activity_id;

-- 校验
-- SELECT COUNT(*) FROM wkf_activity_definition WHERE category_code='PEIS' AND id BETWEEN 911200000000000001 AND 911200000000000300;
-- SELECT COUNT(*) FROM adm_charge_item_definition WHERE instance_table='wkf_activity_definition' AND id BETWEEN 911201000000000001 AND 911201000000000300;
-- SELECT gender_code, COUNT(*) FROM tmp_peis_legacy_project GROUP BY 1;
-- SELECT type_enum, COUNT(*) FROM tmp_peis_legacy_project GROUP BY 1;

COMMIT;
"""

    sql_path = OUT_DIR / "V20260819__import_legacy_peis_projects.sql"
    sql_path.write_text(
        header
        + "\n".join(staging_lines)
        + "\n\n-- ========== wkf_activity_definition ==========\n"
        + "\n".join(ad_lines)
        + "\n\n-- ========== adm_charge_item_definition (SALE) ==========\n"
        + "\n".join(cid_lines)
        + footer,
        encoding="utf-8",
    )

    readme = OUT_DIR / "README-体检项目导入.md"
    readme.write_text(
        f"""# 老体检「项目」→ Whale「体检项目」导入说明

## 源数据

- 文件：`体检项目json.txt`
- 条数：**{len(rows)}**
- 老界面：字典 → 项目

## 生成文件

| 文件 | 说明 |
|------|------|
| `V20260819__import_legacy_peis_projects.sql` | 一次性导入 SQL（staging + AD + SALE 价） |
| `gen_peis_project_import.py` | 重新生成脚本 |

## 字段对照

| 老字段 | 含义 | Whale 落点 | 完备度 |
|--------|------|------------|--------|
| `code` | 编码 | `wkf_activity_definition.bus_no` | ✅ |
| `name` | 名称 | `name` | ✅ |
| `zjf` | 助记符 | `py_str` | ✅ |
| `flag` | 状态 | `status_enum=ACTIVE`（本批全正常） | ✅ |
| `lx` | 正常/PACS/列表 | 启发式 → `type_enum` 1/2/5 | ⚠️ 非 1:1 |
| `ks` | 执行科室 | staging + description；`org_id` 待回填 | ⚠️ |
| `dj` | 原价 | staging.`price_dj` | ⚠️ 主路径用 SALE |
| `yhj` | 优惠价 | **优先**写入 `adm_charge_item_definition.price_value`（SALE） | ⚠️ 无「双价」模型 |
| `cbj` | 成本价 | 未导入 | ❌ |
| `isfy` | 是否费用 | `pricing_flag` | ✅ |
| `syxb` | 适用性别 | staging.`gender_code` | ❌ **AD 无性别限制字段** |
| `jkcode/jkname/jkjcff` | 接口编码/名称/方法 | staging | ❌ **AD 无接口码；院点对码表另维护** |
| `fl` | 项目分类 | 未导入（几乎全是「项目分类1」） | ❌ 无 PEIS 项目分类维 |
| `mx` | 明细 ID 串 | staging.`mx_legacy_ids` | ⚠️ 需另导「明细」再写 AOL |
| `sx` | 顺序 | staging only | ❌ AD 无 sort |
| `wjcode/wjname` 等外检字段 | 外检 | 未导入 | ❌ |
| `sfsqz` 等危急值配置 | 危急值 | 未导入 | ❌（`peis_auto_express` 未成码） |
| `isdztp/ishavetp` | 单张/有图 | 未导入 | ❌ 影像附件能力缺口 |

## 数据结构是否欠缺？

**结论：主路径（编码/名称/类型/执行科室/单价）大体够用；现场列表上的若干列在 Whale 体检项目模型里没有对位字段。**

### 建议补齐（按现场优先级）

1. **适用性别**（男/女/不限）— 登记展开套餐时过滤；现无字段，仅靠人工选套餐  
2. **优惠价 vs 原价** — 现仅 `ChargeItemDefinition.SALE` 单价值；若要展示「划价/实收」需双价或定价策略  
3. **接口编码** — LIS/PACS 外发依赖院点 mapping，不在 `ActivityDefinition`；建议把 `jkcode` 导入院点对码表  
4. **执行科室** — 有 `org_id`，但导入后必须按 `adm_organization` 名称回填  
5. **项目→明细** — `mx` 有值但 JSON 的 `items` 为空，**本脚本不建 AOL**；需再导「明细」字典  
6. **PACS 模式** — 映射为 `type_enum=检查`，没有独立「PACS模式」枚举（符合产品：靠 type + 外发）

### 不建议硬塞进 AD 的字段

危急值规则、外检标志、问卷、技术规范等，应落扩展表/配置，而不是继续加宽 `wkf_activity_definition`。

## 执行步骤

1. 备份库  
2. 在目标库执行 `V20260819__import_legacy_peis_projects.sql`  
3. 按科室回填 `org_id`（脚本末尾有示例 UPDATE）  
4. 前端打开「体检目录 → 体检项目」核对  
5. 另导「明细」后再写 `cus_activity_observation_link`（用 staging.`mx_legacy_ids`）

## 类型启发式（可改脚本重生成）

| 条件 | type_enum | fin_type |
|------|-----------|----------|
| `lx=PACS模式` 或 影像/彩超/内镜等科室 | 2 检查 | 3 检查费 |
| 检验科 | 1 检验 | 4 化验费 |
| 其它 | 5 其他 | 14 其他费 |
""",
        encoding="utf-8",
    )

    # copy generator beside
    gen_path = OUT_DIR / "gen_peis_project_import.py"
    gen_path.write_text(Path(__file__).read_text(encoding="utf-8"), encoding="utf-8")

    print(f"wrote {sql_path}")
    print(f"wrote {readme}")
    print(f"activities={len(ad_lines)} prices={len(cid_lines)}")
    print(f"gender-restricted={sum(1 for r in rows if map_gender(r.get('syxb'))!='both')}")
    print(f"with jkcode={sum(1 for r in rows if (r.get('jkcode') or '').strip())}")


if __name__ == "__main__":
    main()
