# -*- coding: utf-8 -*-
"""老餐导入 SQL 生成（同项目口径：原价 je、性别、顺序、PEIS、幂等）。"""
from __future__ import annotations

import json
from pathlib import Path

PKG = Path(r"D:\0.product\02.Whale\whale-doc\health\peis-import\体检套餐明细json.txt")
OUT = Path(r"D:\0.product\02.Whale\whale-doc\health\peis-import\V20260820a__import_legacy_peis_packages.sql")

PKG_AD_BASE = 911_230_000_000_000_000
PKG_CID_BASE = 911_231_000_000_000_000
PKG_CHILD_BASE = 911_232_000_000_000_000
TENANT = 1
BY = 1

UUID_MALE = "519d4cc8-a962-4995-821e-073b0f59fb80"
UUID_FEMALE = "1ad1e6db-5c56-4b00-997f-4cb5e4d40314"

# 套餐类型固定「其他」；收费项 type_code 与项目 OTHER 一致
TYPE_ENUM = "5"
FIN_TYPE = "14"


def sql_str(v):
    if v is None:
        return "NULL"
    return "'" + str(v).replace("'", "''") + "'"


def sql_num(v):
    if v is None or v == "":
        return "NULL"
    return str(float(v))


def gender(syxb: str | None) -> str:
    parts = [p for p in (syxb or "").replace(" ", "").split(",") if p]
    has_m = UUID_MALE in parts
    has_f = UUID_FEMALE in parts
    if has_m and not has_f:
        return "MALE"
    if has_f and not has_m:
        return "FEMALE"
    return "BOTH"


def main() -> None:
    packages = json.loads(PKG.read_text(encoding="utf-8"))["data"]["list"]

    # 同 code 多套餐（如 20260417 男/女）拆开 bus_no，避免合并丢数据
    from collections import Counter

    code_counts = Counter((p.get("code") or f"LEGACY_PKG_{p['id']}").strip() for p in packages)

    lines: list[str] = []
    lines.append(
        f"""-- =============================================================================
-- 老体检套餐导入（幂等 · 同项目口径）
-- 套餐 {len(packages)}；原价=je（不用 yj）；子项按 xm.code ↔ PEIS 项目 bus_no
-- 同 code 多套餐：bus_no = code_性别（如 20260417_MALE）
-- ID 段：套餐 AD {PKG_AD_BASE} / 价 {PKG_CID_BASE} / 子项行 {PKG_CHILD_BASE}
-- =============================================================================
BEGIN;

CREATE TABLE IF NOT EXISTS tmp_peis_legacy_package (
  legacy_id varchar(64) PRIMARY KEY,
  intended_activity_id int8 NOT NULL,
  bus_no varchar(64) NOT NULL,
  display varchar(255) NOT NULL,
  py_str varchar(128),
  permitted_gender_enum varchar(32) NOT NULL,
  sort_order int4,
  sale_price numeric(18,4),
  activity_id int8
);
TRUNCATE tmp_peis_legacy_package;

CREATE TABLE IF NOT EXISTS tmp_peis_legacy_package_item (
  package_legacy_id varchar(64) NOT NULL,
  sort_num int4 NOT NULL,
  child_bus_no varchar(64) NOT NULL,
  child_name varchar(255),
  PRIMARY KEY (package_legacy_id, sort_num)
);
TRUNCATE tmp_peis_legacy_package_item;
"""
    )

    child_link_count = 0
    used_bus_nos: set[str] = set()
    for i, p in enumerate(packages, start=1):
        legacy_id = str(p["id"])
        ad_id = PKG_AD_BASE + i
        raw_code = (p.get("code") or f"LEGACY_PKG_{legacy_id}").strip()
        name = (p.get("name") or "").strip()
        py = (p.get("zjf") or "").strip() or None
        g = gender(p.get("syxb"))
        sx = p.get("sx")
        je = p.get("je")
        je_num = 0.0 if je is None or je == "" else float(je)

        if code_counts[raw_code] > 1:
            bus_no = f"{raw_code}_{g}"
        else:
            bus_no = raw_code
        # 极端兜底：仍冲突则缀 legacy 短 id
        if bus_no in used_bus_nos:
            bus_no = f"{bus_no}_{legacy_id[:8]}"
        used_bus_nos.add(bus_no)

        lines.append(
            "INSERT INTO tmp_peis_legacy_package "
            "(legacy_id, intended_activity_id, bus_no, display, py_str, "
            "permitted_gender_enum, sort_order, sale_price) VALUES ("
            f"{sql_str(legacy_id)}, {ad_id}, {sql_str(bus_no)}, {sql_str(name)}, {sql_str(py)}, "
            f"{sql_str(g)}, {sx if sx is not None else 'NULL'}, {sql_num(je_num)});"
        )

        sort = 0
        seen_child_codes: set[str] = set()
        for t in p.get("tcxms") or []:
            xm = t.get("xm") or {}
            child_code = (xm.get("code") or "").strip()
            if not child_code:
                continue
            if child_code in seen_child_codes:
                continue
            seen_child_codes.add(child_code)
            sort += 1
            child_link_count += 1
            child_name = (xm.get("name") or t.get("name") or "").strip() or None
            lines.append(
                "INSERT INTO tmp_peis_legacy_package_item "
                "(package_legacy_id, sort_num, child_bus_no, child_name) VALUES ("
                f"{sql_str(legacy_id)}, {sort}, {sql_str(child_code)}, {sql_str(child_name)});"
            )

    lines.append(
        f"""
-- ========== 套餐 AD：按 bus_no 幂等 ==========
INSERT INTO wkf_activity_definition (
    id, tenant_id, created_at, updated_at, created_by, updated_by,
    is_deleted, version, category_code, bus_no, name, py_str,
    type_enum, status_enum, org_id, children_flag, pricing_flag,
    permitted_gender_enum, sort_order
)
SELECT
    t.intended_activity_id, {TENANT}, NOW(), NOW(), {BY}, {BY},
    FALSE, 0, 'PEIS', t.bus_no, t.display, t.py_str,
    {sql_str(TYPE_ENUM)}, 'ACTIVE', NULL, TRUE,
    CASE WHEN COALESCE(t.sale_price, 0) > 0 THEN TRUE ELSE FALSE END,
    t.permitted_gender_enum, t.sort_order
FROM tmp_peis_legacy_package t
WHERE NOT EXISTS (
    SELECT 1 FROM wkf_activity_definition a
    WHERE a.tenant_id = {TENANT} AND a.bus_no = t.bus_no AND a.is_deleted = FALSE
)
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    py_str = EXCLUDED.py_str,
    type_enum = EXCLUDED.type_enum,
    status_enum = 'ACTIVE',
    children_flag = TRUE,
    pricing_flag = EXCLUDED.pricing_flag,
    permitted_gender_enum = EXCLUDED.permitted_gender_enum,
    sort_order = EXCLUDED.sort_order,
    category_code = 'PEIS',
    is_deleted = FALSE,
    updated_at = NOW();

-- 已存在同 bus_no 时更新为套餐口径（不改动已占用的单项 bus_no，套餐码与项目码无交叉）
UPDATE wkf_activity_definition a
SET name = t.display,
    py_str = t.py_str,
    type_enum = {sql_str(TYPE_ENUM)},
    status_enum = 'ACTIVE',
    children_flag = TRUE,
    pricing_flag = CASE WHEN COALESCE(t.sale_price, 0) > 0 THEN TRUE ELSE FALSE END,
    permitted_gender_enum = t.permitted_gender_enum,
    sort_order = t.sort_order,
    category_code = 'PEIS',
    is_deleted = FALSE,
    updated_at = NOW(),
    updated_by = {BY}
FROM tmp_peis_legacy_package t
WHERE a.tenant_id = {TENANT}
  AND a.bus_no = t.bus_no
  AND a.is_deleted = FALSE;

-- 回写实际套餐 activity_id
UPDATE tmp_peis_legacy_package t
SET activity_id = a.id
FROM wkf_activity_definition a
WHERE a.tenant_id = {TENANT}
  AND a.bus_no = t.bus_no
  AND a.is_deleted = FALSE;

-- ========== SALE 原价（je，不用 yj）==========
INSERT INTO adm_charge_item_definition (
    id, tenant_id, created_at, updated_at, created_by, updated_by,
    is_deleted, version, instance_table, instance_id, charge_name, status_enum,
    org_id, type_code, price_type_code, price_value, priority
)
SELECT
    COALESCE(
      (SELECT MAX(id) FROM adm_charge_item_definition
       WHERE id >= {PKG_CID_BASE} AND id < {PKG_CID_BASE} + 1000000000),
      {PKG_CID_BASE}
    ) + ROW_NUMBER() OVER (ORDER BY t.legacy_id),
    {TENANT}, NOW(), NOW(), {BY}, {BY},
    FALSE, 0, 'wkf_activity_definition', t.activity_id, t.display, 'ACTIVE',
    NULL, {sql_str(FIN_TYPE)}, 'SALE', t.sale_price, 0
FROM tmp_peis_legacy_package t
WHERE t.activity_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM adm_charge_item_definition c
      WHERE c.instance_table = 'wkf_activity_definition'
        AND c.instance_id = t.activity_id
        AND c.price_type_code = 'SALE'
        AND c.is_deleted = FALSE
  );

UPDATE adm_charge_item_definition c
SET charge_name = t.display,
    price_value = t.sale_price,
    type_code = {sql_str(FIN_TYPE)},
    status_enum = 'ACTIVE',
    is_deleted = FALSE,
    updated_at = NOW(),
    updated_by = {BY}
FROM tmp_peis_legacy_package t
WHERE t.activity_id IS NOT NULL
  AND c.instance_table = 'wkf_activity_definition'
  AND c.instance_id = t.activity_id
  AND c.price_type_code = 'SALE'
  AND c.is_deleted = FALSE;

-- ========== 子项：xm.code → PEIS 单项 bus_no ==========
INSERT INTO wkf_activity_definition_children (
    id, tenant_id, created_at, updated_at, created_by, updated_by,
    is_deleted, version, activity_id, children_id, children_num, children_name
)
SELECT
    COALESCE(
      (SELECT MAX(id) FROM wkf_activity_definition_children
       WHERE id >= {PKG_CHILD_BASE} AND id < {PKG_CHILD_BASE} + 1000000000),
      {PKG_CHILD_BASE}
    ) + ROW_NUMBER() OVER (ORDER BY t.legacy_id, i.sort_num),
    {TENANT}, NOW(), NOW(), {BY}, {BY},
    FALSE, 0, t.activity_id, child.id, 1,
    COALESCE(i.child_name, child.name)
FROM tmp_peis_legacy_package_item i
JOIN tmp_peis_legacy_package t ON t.legacy_id = i.package_legacy_id
JOIN wkf_activity_definition child
  ON child.tenant_id = {TENANT}
 AND child.bus_no = i.child_bus_no
 AND child.category_code = 'PEIS'
 AND COALESCE(child.children_flag, FALSE) = FALSE
 AND child.is_deleted = FALSE
WHERE t.activity_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM wkf_activity_definition_children c
      WHERE c.activity_id = t.activity_id
        AND c.children_id = child.id
        AND c.is_deleted = FALSE
  );

-- 校验
SELECT 'PEIS_PKG' AS k, COUNT(*) AS n FROM wkf_activity_definition
 WHERE category_code='PEIS' AND children_flag=TRUE AND is_deleted=FALSE;
SELECT 'PKG_STAGED' AS k, COUNT(*) AS n FROM tmp_peis_legacy_package;
SELECT 'PKG_MAPPED' AS k, COUNT(*) AS n FROM tmp_peis_legacy_package WHERE activity_id IS NOT NULL;
SELECT 'PKG_CHILDREN' AS k, COUNT(*) AS n
  FROM wkf_activity_definition_children c
  JOIN tmp_peis_legacy_package t ON t.activity_id = c.activity_id
 WHERE c.is_deleted=FALSE;
SELECT 'PKG_CHILD_MISS' AS k, COUNT(*) AS n
  FROM tmp_peis_legacy_package_item i
  JOIN tmp_peis_legacy_package t ON t.legacy_id = i.package_legacy_id
  WHERE t.activity_id IS NOT NULL
    AND NOT EXISTS (
      SELECT 1 FROM wkf_activity_definition child
      WHERE child.tenant_id = {TENANT}
        AND child.bus_no = i.child_bus_no
        AND child.category_code = 'PEIS'
        AND COALESCE(child.children_flag, FALSE) = FALSE
        AND child.is_deleted = FALSE
    );
SELECT permitted_gender_enum, COUNT(*) FROM wkf_activity_definition
 WHERE category_code='PEIS' AND children_flag=TRUE AND is_deleted=FALSE GROUP BY 1 ORDER BY 1;

COMMIT;
"""
    )

    OUT.write_text("\n".join(lines), encoding="utf-8")
    print(f"wrote {OUT}")
    print(f"packages={len(packages)} child_links={child_link_count}")


if __name__ == "__main__":
    main()
