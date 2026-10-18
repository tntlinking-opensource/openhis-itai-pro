# -*- coding: utf-8 -*-
"""按 code 复用已有 OD，幂等导入 whale-pro。"""
from __future__ import annotations

import json
from pathlib import Path

PROJ = Path(r"C:\Users\Administrator\Desktop\体检项目json.txt")
DET = Path(r"C:\Users\Administrator\Desktop\体检明细json.txt")
OUT = Path(r"D:\0.product\02.Whale\whale-doc\health\peis-import\V20260819c__import_legacy_peis_catalog.sql")

AD_BASE = 911_200_000_000_000_000
CID_BASE = 911_201_000_000_000_000
OD_BASE = 911_210_000_000_000_000
AOL_BASE = 911_220_000_000_000_000
TENANT = 1
BY = 1

UUID_MALE = "519d4cc8-a962-4995-821e-073b0f59fb80"
UUID_FEMALE = "1ad1e6db-5c56-4b00-997f-4cb5e4d40314"
LAB = {"检验科"}
EXAM = {"影像科", "彩超室", "内镜科", "特检科", "电诊室", "电测听"}


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


def map_type(lx_name, ks_name, iswjfw) -> tuple[str, str]:
    if int(iswjfw or 0) == 1:
        return "6", "14"
    lx = lx_name or ""
    ks = ks_name or ""
    if lx == "PACS模式" or ks in EXAM:
        return "2", "3"
    if ks in LAB or "检验" in ks:
        return "1", "4"
    if any(x in ks for x in ("彩超", "影像", "CT", "DR", "内镜", "超声")):
        return "2", "3"
    return "5", "14"


def map_value_type(lx_name: str | None) -> str:
    if lx_name == "数字":
        return "quantity"
    return "string"


def main() -> None:
    projects = json.loads(PROJ.read_text(encoding="utf-8"))["data"]["list"]
    details = json.loads(DET.read_text(encoding="utf-8"))["data"]["list"]

    # intended OD id by legacy id (for staging); real id resolved in SQL
    legacy_od_intended: dict[str, tuple[int, str]] = {}
    for i, d in enumerate(details, start=1):
        code = (d.get("code") or f"LEGACY_OD_{d['id']}").strip()
        legacy_od_intended[d["id"]] = (OD_BASE + i, code)

    lines: list[str] = []
    lines.append(
        f"""-- =============================================================================
-- 老体检目录导入（幂等 · 复用 tenant+code）
-- 项目 {len(projects)} + 明细 {len(details)}
-- 明细：code 已存在则复用 id；不存在则插入预分配 id
-- =============================================================================
BEGIN;

ALTER TABLE wkf_activity_definition ADD COLUMN IF NOT EXISTS permitted_gender_enum varchar(32);
ALTER TABLE wkf_activity_definition ADD COLUMN IF NOT EXISTS sort_order int4;
UPDATE wkf_activity_definition
SET permitted_gender_enum = 'BOTH'
WHERE permitted_gender_enum IS NULL OR btrim(permitted_gender_enum) = '';
ALTER TABLE wkf_activity_definition ALTER COLUMN permitted_gender_enum SET DEFAULT 'BOTH';
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name = 'wkf_activity_definition' AND column_name = 'permitted_gender_enum'
      AND is_nullable = 'YES'
  ) THEN
    ALTER TABLE wkf_activity_definition ALTER COLUMN permitted_gender_enum SET NOT NULL;
  END IF;
END $$;

CREATE TABLE IF NOT EXISTS tmp_peis_legacy_project (
  legacy_id varchar(64) PRIMARY KEY,
  activity_id int8 NOT NULL,
  bus_no varchar(64) NOT NULL,
  mx_legacy_ids text,
  jkcode varchar(128),
  jkname varchar(255),
  dept_name varchar(128)
);
TRUNCATE tmp_peis_legacy_project;

CREATE TABLE IF NOT EXISTS tmp_peis_legacy_detail (
  legacy_id varchar(64) PRIMARY KEY,
  intended_od_id int8 NOT NULL,
  code varchar(128) NOT NULL,
  display varchar(255) NOT NULL,
  value_type_enum varchar(64) NOT NULL,
  unit_code varchar(64),
  observation_definition_id int8
);
TRUNCATE tmp_peis_legacy_detail;
"""
    )

    # ---- stage details ----
    lines.append("\n-- ========== stage details ==========\n")
    for i, d in enumerate(details, start=1):
        od_id = OD_BASE + i
        code = (d.get("code") or f"LEGACY_OD_{d['id']}").strip()
        name = (d.get("name") or "").strip()
        unit = (d.get("dw") or "").strip() or None
        vt = map_value_type((d.get("lx") or {}).get("name"))
        lines.append(
            "INSERT INTO tmp_peis_legacy_detail "
            "(legacy_id, intended_od_id, code, display, value_type_enum, unit_code) VALUES ("
            f"{sql_str(d['id'])}, {od_id}, {sql_str(code)}, {sql_str(name)}, "
            f"{sql_str(vt)}, {sql_str(unit)});"
        )

    lines.append(
        """
-- 按 code 去重插入（源数据有重复 code：4044800、4994300）
-- 冲突目标：部分唯一索引 uk_cln_obs_def_tenant_code (tenant_id, code) WHERE is_deleted=false
INSERT INTO cln_observation_definition (
    id, tenant_id, org_id, created_at, updated_at, created_by, updated_by,
    is_deleted, version, code, display, value_type_enum, unit_code, category_code, status_enum
)
SELECT DISTINCT ON (t.code)
    t.intended_od_id, 1, NULL, NOW(), NOW(), 1, 1,
    FALSE, 0, t.code, t.display, t.value_type_enum, t.unit_code, 'exam_item', 'active'
FROM tmp_peis_legacy_detail t
ORDER BY t.code, t.intended_od_id
ON CONFLICT (tenant_id, code) WHERE (is_deleted = FALSE) DO UPDATE SET
    display = EXCLUDED.display,
    value_type_enum = EXCLUDED.value_type_enum,
    unit_code = EXCLUDED.unit_code,
    status_enum = 'active',
    is_deleted = FALSE,
    updated_at = NOW();

-- 回填实际 OD id（同一 code 的多条 legacy 明细共用同一 OD）
UPDATE tmp_peis_legacy_detail t
SET observation_definition_id = o.id
FROM cln_observation_definition o
WHERE o.tenant_id = 1 AND o.code = t.code AND o.is_deleted = FALSE;
"""
    )

    # ---- projects ----
    lines.append("\n-- ========== projects ==========\n")
    aol_rows: list[tuple[int, int, int, int]] = []  # aol_id, ad_id, intended will use SQL join
    for i, r in enumerate(projects, start=1):
        ad_id = AD_BASE + i
        cid_id = CID_BASE + i
        code = (r.get("code") or f"LEGACY_PROJ_{r['id']}").strip()
        name = (r.get("name") or "").strip()
        py = (r.get("zjf") or "").strip() or None
        ks = r.get("ks") or {}
        ks_name = ks.get("name")
        lx_name = (r.get("lx") or {}).get("name")
        type_enum, fin = map_type(lx_name, ks_name, r.get("iswjfw"))
        g = gender(r.get("syxb"))
        dj = float(r.get("dj") or 0)
        isfy = ((r.get("isfy") or {}).get("name") or "是") == "是"
        # 有原价则必须可收费并落 SALE；避免 isfy=否但有价时体检收费整单失败
        chargeable = isfy or dj > 0
        sx = r.get("sx")
        mx = (r.get("mx") or "").strip() or None
        jkcode = (r.get("jkcode") or "").strip() or None
        jkname = (r.get("jkname") or "").strip() or None

        lines.append(
            "INSERT INTO tmp_peis_legacy_project "
            "(legacy_id, activity_id, bus_no, mx_legacy_ids, jkcode, jkname, dept_name) VALUES ("
            f"{sql_str(r['id'])}, {ad_id}, {sql_str(code)}, {sql_str(mx)}, "
            f"{sql_str(jkcode)}, {sql_str(jkname)}, {sql_str(ks_name)});"
        )

        # AD: insert if bus_no not exists for PEIS, else update by bus_no
        lines.append(
            f"""
INSERT INTO wkf_activity_definition (
    id, tenant_id, created_at, updated_at, created_by, updated_by,
    is_deleted, version, category_code, bus_no, name, py_str,
    type_enum, status_enum, org_id, children_flag, pricing_flag,
    permitted_gender_enum, sort_order,
    package_unit_code, def_encounter_unit_code
)
SELECT
    {ad_id}, {TENANT}, NOW(), NOW(), {BY}, {BY},
    FALSE, 0, 'PEIS', {sql_str(code)}, {sql_str(name)}, {sql_str(py)},
    {sql_str(type_enum)}, 'ACTIVE', NULL, FALSE, {'TRUE' if chargeable else 'FALSE'},
    {sql_str(g)}, {sx if sx is not None else 'NULL'},
    '6', '6'
WHERE NOT EXISTS (
    SELECT 1 FROM wkf_activity_definition a
    WHERE a.tenant_id = {TENANT} AND a.bus_no = {sql_str(code)} AND a.is_deleted = FALSE
)
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name, py_str = EXCLUDED.py_str, type_enum = EXCLUDED.type_enum,
    status_enum = EXCLUDED.status_enum, pricing_flag = EXCLUDED.pricing_flag,
    permitted_gender_enum = EXCLUDED.permitted_gender_enum,
    sort_order = EXCLUDED.sort_order,
    package_unit_code = COALESCE(NULLIF(btrim(wkf_activity_definition.package_unit_code), ''), EXCLUDED.package_unit_code),
    def_encounter_unit_code = COALESCE(NULLIF(btrim(wkf_activity_definition.def_encounter_unit_code), ''), EXCLUDED.def_encounter_unit_code),
    is_deleted = FALSE, updated_at = NOW();

UPDATE wkf_activity_definition a
SET name = {sql_str(name)},
    py_str = {sql_str(py)},
    type_enum = {sql_str(type_enum)},
    status_enum = 'ACTIVE',
    pricing_flag = {'TRUE' if chargeable else 'FALSE'},
    permitted_gender_enum = {sql_str(g)},
    sort_order = {sx if sx is not None else 'NULL'},
    category_code = 'PEIS',
    children_flag = FALSE,
    package_unit_code = COALESCE(NULLIF(btrim(a.package_unit_code), ''), '6'),
    def_encounter_unit_code = COALESCE(NULLIF(btrim(a.def_encounter_unit_code), ''), '6'),
    is_deleted = FALSE,
    updated_at = NOW(),
    updated_by = {BY}
WHERE a.tenant_id = {TENANT} AND a.bus_no = {sql_str(code)} AND a.is_deleted = FALSE;

-- 回写实际 activity_id（可能是库中已有行）
UPDATE tmp_peis_legacy_project t
SET activity_id = a.id
FROM wkf_activity_definition a
WHERE t.legacy_id = {sql_str(r['id'])}
  AND a.tenant_id = {TENANT} AND a.bus_no = {sql_str(code)} AND a.is_deleted = FALSE;
"""
        )

        if chargeable:
            lines.append(
                f"""
INSERT INTO adm_charge_item_definition (
    id, tenant_id, created_at, updated_at, created_by, updated_by,
    is_deleted, version, instance_table, instance_id, charge_name, status_enum,
    org_id, type_code, price_type_code, price_value, priority
)
SELECT
    {cid_id}, {TENANT}, NOW(), NOW(), {BY}, {BY},
    FALSE, 0, 'wkf_activity_definition', t.activity_id, {sql_str(name)}, 'ACTIVE',
    NULL, {sql_str(fin)}, 'SALE', {sql_num(dj)}, 0
FROM tmp_peis_legacy_project t
WHERE t.legacy_id = {sql_str(r['id'])}
  AND NOT EXISTS (
      SELECT 1 FROM adm_charge_item_definition c
      WHERE c.instance_table = 'wkf_activity_definition'
        AND c.instance_id = t.activity_id
        AND c.price_type_code = 'SALE'
        AND c.is_deleted = FALSE
  )
ON CONFLICT (id) DO UPDATE SET
    charge_name = EXCLUDED.charge_name,
    price_value = EXCLUDED.price_value,
    type_code = EXCLUDED.type_code,
    instance_id = EXCLUDED.instance_id,
    status_enum = 'ACTIVE',
    is_deleted = FALSE,
    updated_at = NOW();

UPDATE adm_charge_item_definition c
SET charge_name = {sql_str(name)},
    price_value = {sql_num(dj)},
    type_code = {sql_str(fin)},
    status_enum = 'ACTIVE',
    is_deleted = FALSE,
    updated_at = NOW(),
    updated_by = {BY}
FROM tmp_peis_legacy_project t
WHERE t.legacy_id = {sql_str(r['id'])}
  AND c.instance_table = 'wkf_activity_definition'
  AND c.instance_id = t.activity_id
  AND c.price_type_code = 'SALE'
  AND c.is_deleted = FALSE;
"""
            )

    # AOL in bulk via unnest of mx
    lines.append(
        """
-- ========== AOL：按项目 mx 串拆解并绑定（复用实际 OD / AD id）==========
CREATE TEMP TABLE tmp_peis_aol_src (
  project_legacy_id varchar(64) NOT NULL,
  detail_legacy_id varchar(64) NOT NULL,
  sort_num int4 NOT NULL
) ON COMMIT DROP;
"""
    )

    for r in projects:
        mx = (r.get("mx") or "").strip()
        if not mx:
            continue
        sort = 0
        for mid in mx.replace(" ", "").split(","):
            if not mid:
                continue
            sort += 1
            lines.append(
                "INSERT INTO tmp_peis_aol_src (project_legacy_id, detail_legacy_id, sort_num) VALUES ("
                f"{sql_str(r['id'])}, {sql_str(mid)}, {sort});"
            )

    lines.append(
        f"""
-- 同一项目若 mx 含多个 legacy 明细且 code 相同，会落到同一 OD，需按 (AD,OD) 去重
INSERT INTO cus_activity_observation_link (
    id, tenant_id, org_id, created_at, updated_at, created_by, updated_by,
    is_deleted, version, activity_definition_id, observation_definition_id,
    sort_num, required_flag
)
SELECT
    COALESCE(
      (SELECT MAX(id) FROM cus_activity_observation_link
       WHERE id >= {AOL_BASE} AND id < {AOL_BASE} + 1000000000),
      {AOL_BASE}
    ) + row_number() OVER (ORDER BY x.activity_id, x.observation_definition_id),
    {TENANT}, NULL, NOW(), NOW(), {BY}, {BY},
    FALSE, 0, x.activity_id, x.observation_definition_id,
    x.sort_num, TRUE
FROM (
    SELECT DISTINCT ON (p.activity_id, d.observation_definition_id)
        p.activity_id,
        d.observation_definition_id,
        s.sort_num
    FROM tmp_peis_aol_src s
    JOIN tmp_peis_legacy_project p ON p.legacy_id = s.project_legacy_id
    JOIN tmp_peis_legacy_detail d ON d.legacy_id = s.detail_legacy_id
    WHERE d.observation_definition_id IS NOT NULL
    ORDER BY p.activity_id, d.observation_definition_id, s.sort_num
) x
ON CONFLICT (activity_definition_id, observation_definition_id) WHERE (is_deleted = FALSE)
DO UPDATE SET
    sort_num = EXCLUDED.sort_num,
    required_flag = TRUE,
    is_deleted = FALSE,
    updated_at = NOW();

-- 校验
SELECT 'PEIS_AD' AS k, COUNT(*) AS n FROM wkf_activity_definition
 WHERE category_code='PEIS' AND is_deleted=FALSE;
SELECT 'OD_mapped' AS k, COUNT(*) AS n FROM tmp_peis_legacy_detail
 WHERE observation_definition_id IS NOT NULL;
SELECT 'AOL' AS k, COUNT(*) AS n FROM cus_activity_observation_link l
 JOIN tmp_peis_legacy_project p ON p.activity_id = l.activity_definition_id
 WHERE l.is_deleted=FALSE;
SELECT type_enum, COUNT(*) FROM wkf_activity_definition
 WHERE category_code='PEIS' AND is_deleted=FALSE GROUP BY 1 ORDER BY 1;
SELECT permitted_gender_enum, COUNT(*) FROM wkf_activity_definition
 WHERE category_code='PEIS' AND is_deleted=FALSE GROUP BY 1;

COMMIT;
"""
    )

    OUT.write_text("\n".join(lines), encoding="utf-8")
    print(f"wrote {OUT}")
    print(f"projects={len(projects)} details={len(details)}")


if __name__ == "__main__":
    main()
