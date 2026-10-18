-- =============================================================================
-- 回滚：冬葵果 → 冬葵子
-- bak：_bak_20260820_*_md00001249
-- =============================================================================

BEGIN;

DO $$
BEGIN
  IF to_regclass('public._bak_20260820_med_def_md00001249') IS NULL THEN
    RAISE EXCEPTION '缺少 bak 主档表，无法回滚';
  END IF;
  IF to_regclass('public._bak_20260820_charge_def_md00001249') IS NULL THEN
    RAISE EXCEPTION '缺少 bak 定价表，无法回滚';
  END IF;
  IF to_regclass('public._bak_20260820_inv_item_md00001249') IS NULL THEN
    RAISE EXCEPTION '缺少 bak 库存表，无法回滚';
  END IF;
END $$;

UPDATE med_medication_definition d
   SET name               = b.name,
       merchandise_name   = b.merchandise_name,
       py_str             = b.py_str,
       wb_str             = b.wb_str,
       merchandise_py_str = b.merchandise_py_str,
       merchandise_wb_str = b.merchandise_wb_str,
       version            = COALESCE(d.version, 0) + 1,
       updated_at         = NOW()
  FROM _bak_20260820_med_def_md00001249 b
 WHERE d.id = b.id;

UPDATE adm_charge_item_definition c
   SET charge_name = b.charge_name,
       version     = COALESCE(c.version, 0) + 1,
       updated_at  = NOW()
  FROM _bak_20260820_charge_def_md00001249 b
 WHERE c.id = b.id;

UPDATE wkf_inventory_item i
   SET name       = b.name,
       py_str     = b.py_str,
       wb_str     = b.wb_str,
       version    = COALESCE(i.version, 0) + 1,
       updated_at = NOW()
  FROM _bak_20260820_inv_item_md00001249 b
 WHERE i.id = b.id;

DO $$
DECLARE
  n_name text;
  n_inv  integer;
BEGIN
  SELECT name INTO n_name FROM med_medication_definition WHERE id = 345478761049231360;
  IF n_name IS DISTINCT FROM '冬葵子' THEN
    RAISE EXCEPTION '回滚后主档名称=%（期望 冬葵子）', n_name;
  END IF;
  SELECT COUNT(*) INTO n_inv
    FROM wkf_inventory_item
   WHERE item_id = 345478761049231360 AND name = '冬葵子';
  IF n_inv <> 2 THEN
    RAISE EXCEPTION '回滚后库存旧名行数=%（期望 2）', n_inv;
  END IF;
END $$;

SELECT id, bus_no, name, merchandise_name, py_str, wb_str, version
  FROM med_medication_definition WHERE id = 345478761049231360;

COMMIT;
