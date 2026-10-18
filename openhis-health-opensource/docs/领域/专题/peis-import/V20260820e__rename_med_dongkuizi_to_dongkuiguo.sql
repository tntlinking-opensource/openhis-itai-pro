-- =============================================================================
-- 正向：冬葵子 → 冬葵果（可回滚）
-- 库：172.16.101.2 / whale-pro · bus_no=MD00001249 · id=345478761049231360
-- 检索码：冬葵子 DKZ/TAB → 冬葵果 DKG/TAJ
-- 回滚：V20260820e__rollback_rename_med_dongkuiguo.sql
-- =============================================================================

BEGIN;

DROP TABLE IF EXISTS _bak_20260820_med_def_md00001249;
DROP TABLE IF EXISTS _bak_20260820_charge_def_md00001249;
DROP TABLE IF EXISTS _bak_20260820_inv_item_md00001249;

CREATE TABLE _bak_20260820_med_def_md00001249 AS
SELECT id, tenant_id, bus_no, name, merchandise_name,
       py_str, wb_str, merchandise_py_str, merchandise_wb_str,
       version, updated_at, is_deleted
  FROM med_medication_definition
 WHERE id = 345478761049231360
   AND COALESCE(is_deleted, false) = false;

CREATE TABLE _bak_20260820_charge_def_md00001249 AS
SELECT c.id, c.instance_id, c.charge_name, c.price_type_code, c.lot_no,
       c.status_enum, c.version, c.updated_at, c.is_deleted
  FROM adm_charge_item_definition c
 WHERE c.instance_table = 'med_medication_definition'
   AND c.instance_id = 345478761049231360
   AND COALESCE(c.is_deleted, false) = false;

CREATE TABLE _bak_20260820_inv_item_md00001249 AS
SELECT i.id, i.item_id, i.name, i.py_str, i.wb_str, i.lot_number,
       i.quantity, i.location_id, i.version, i.updated_at, i.is_deleted
  FROM wkf_inventory_item i
 WHERE i.item_table = 'med_medication_definition'
   AND i.item_id = 345478761049231360
   AND COALESCE(i.is_deleted, false) = false;

DO $$
DECLARE
  n_def integer;
  n_chg integer;
  n_inv integer;
  old_name text;
BEGIN
  SELECT COUNT(*) INTO n_def FROM _bak_20260820_med_def_md00001249;
  SELECT COUNT(*) INTO n_chg FROM _bak_20260820_charge_def_md00001249;
  SELECT COUNT(*) INTO n_inv FROM _bak_20260820_inv_item_md00001249;
  SELECT name INTO old_name FROM _bak_20260820_med_def_md00001249;
  IF n_def <> 1 THEN
    RAISE EXCEPTION 'bak 主档行数=%（期望 1）', n_def;
  END IF;
  IF old_name IS DISTINCT FROM '冬葵子' THEN
    RAISE EXCEPTION '主档当前名称=%（期望 冬葵子）', old_name;
  END IF;
  IF n_chg <> 5 THEN
    RAISE EXCEPTION 'bak 定价行数=%（期望 5）', n_chg;
  END IF;
  IF n_inv <> 2 THEN
    RAISE EXCEPTION 'bak 库存行数=%（期望 2）', n_inv;
  END IF;
  IF EXISTS (
        SELECT 1 FROM med_medication_definition
         WHERE name = '冬葵果'
           AND id <> 345478761049231360
           AND COALESCE(is_deleted, false) = false
      ) THEN
    RAISE EXCEPTION '已存在其他「冬葵果」目录项，拒绝改名';
  END IF;
END $$;

DO $$
DECLARE
  n integer;
BEGIN
  UPDATE med_medication_definition d
     SET name               = '冬葵果',
         merchandise_name   = '冬葵果',
         py_str             = 'DKG',
         wb_str             = 'TAJ',
         merchandise_py_str = 'DKG',
         merchandise_wb_str = 'TAJ',
         version            = COALESCE(d.version, 0) + 1,
         updated_at         = NOW()
   WHERE d.id = 345478761049231360
     AND d.bus_no = 'MD00001249'
     AND d.name = '冬葵子'
     AND COALESCE(d.is_deleted, false) = false;

  GET DIAGNOSTICS n = ROW_COUNT;
  IF n <> 1 THEN
    RAISE EXCEPTION '主档更新行数=%（期望 1）', n;
  END IF;
END $$;

UPDATE adm_charge_item_definition c
   SET charge_name = '冬葵果',
       version     = COALESCE(c.version, 0) + 1,
       updated_at  = NOW()
 WHERE c.instance_table = 'med_medication_definition'
   AND c.instance_id = 345478761049231360
   AND COALESCE(c.is_deleted, false) = false
   AND (c.charge_name IS NULL OR btrim(c.charge_name) = '' OR c.charge_name = '冬葵子');

UPDATE wkf_inventory_item i
   SET name       = '冬葵果',
       py_str     = 'DKG',
       wb_str     = 'TAJ',
       version    = COALESCE(i.version, 0) + 1,
       updated_at = NOW()
 WHERE i.item_table = 'med_medication_definition'
   AND i.item_id = 345478761049231360
   AND COALESCE(i.is_deleted, false) = false
   AND i.id IN (SELECT id FROM _bak_20260820_inv_item_md00001249);

SELECT 'med' AS src, id, bus_no, name, merchandise_name, py_str, wb_str, version
  FROM med_medication_definition WHERE id = 345478761049231360
UNION ALL
SELECT 'bak', id, bus_no, name, merchandise_name, py_str, wb_str, version
  FROM _bak_20260820_med_def_md00001249;

SELECT COUNT(*) AS inv_renamed
  FROM wkf_inventory_item
 WHERE item_id = 345478761049231360 AND name = '冬葵果';

SELECT COUNT(*) AS charge_renamed
  FROM adm_charge_item_definition
 WHERE instance_id = 345478761049231360 AND charge_name = '冬葵果';

COMMIT;
