-- =============================================================================
-- 回滚：黄连片 → 黄连（从改前 bak 表精确还原）
-- 依赖正向脚本创建的：
--   _bak_20260820_med_def_md00001238
--   _bak_20260820_charge_def_md00001238
--   _bak_20260820_inv_item_md00001238
-- 库：172.16.101.2 / whale-pro
-- =============================================================================

BEGIN;

DO $$
BEGIN
  IF to_regclass('public._bak_20260820_med_def_md00001238') IS NULL THEN
    RAISE EXCEPTION '缺少 bak 主档表，无法回滚';
  END IF;
  IF to_regclass('public._bak_20260820_charge_def_md00001238') IS NULL THEN
    RAISE EXCEPTION '缺少 bak 定价表，无法回滚';
  END IF;
  IF to_regclass('public._bak_20260820_inv_item_md00001238') IS NULL THEN
    RAISE EXCEPTION '缺少 bak 库存表，无法回滚';
  END IF;
END $$;

-- 主档还原
UPDATE med_medication_definition d
   SET name               = b.name,
       merchandise_name   = b.merchandise_name,
       py_str             = b.py_str,
       wb_str             = b.wb_str,
       merchandise_py_str = b.merchandise_py_str,
       merchandise_wb_str = b.merchandise_wb_str,
       version            = COALESCE(d.version, 0) + 1,
       updated_at         = NOW()
  FROM _bak_20260820_med_def_md00001238 b
 WHERE d.id = b.id;

-- 定价还原
UPDATE adm_charge_item_definition c
   SET charge_name = b.charge_name,
       version     = COALESCE(c.version, 0) + 1,
       updated_at  = NOW()
  FROM _bak_20260820_charge_def_md00001238 b
 WHERE c.id = b.id;

-- 库存还原
UPDATE wkf_inventory_item i
   SET name       = b.name,
       py_str     = b.py_str,
       wb_str     = b.wb_str,
       version    = COALESCE(i.version, 0) + 1,
       updated_at = NOW()
  FROM _bak_20260820_inv_item_md00001238 b
 WHERE i.id = b.id;

-- 校验：应回到「黄连」
DO $$
DECLARE
  n_name text;
  n_inv  integer;
BEGIN
  SELECT name INTO n_name FROM med_medication_definition WHERE id = 341502480882339840;
  IF n_name IS DISTINCT FROM '黄连' THEN
    RAISE EXCEPTION '回滚后主档名称=%（期望 黄连）', n_name;
  END IF;
  SELECT COUNT(*) INTO n_inv
    FROM wkf_inventory_item
   WHERE item_id = 341502480882339840 AND name = '黄连';
  IF n_inv <> 2 THEN
    RAISE EXCEPTION '回滚后库存旧名行数=%（期望 2）', n_inv;
  END IF;
END $$;

SELECT id, bus_no, name, merchandise_name, py_str, wb_str, version
  FROM med_medication_definition WHERE id = 341502480882339840;

SELECT id, name, py_str, wb_str, quantity
  FROM wkf_inventory_item WHERE item_id = 341502480882339840;

SELECT id, charge_name, price_type_code
  FROM adm_charge_item_definition WHERE instance_id = 341502480882339840;

COMMIT;

-- 确认业务正常后，可手工清理 bak（清理后无法再按 bak 回滚）：
-- DROP TABLE IF EXISTS _bak_20260820_med_def_md00001238;
-- DROP TABLE IF EXISTS _bak_20260820_charge_def_md00001238;
-- DROP TABLE IF EXISTS _bak_20260820_inv_item_md00001238;
