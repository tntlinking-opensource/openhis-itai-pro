-- =============================================================================
-- 正向：黄连 → 黄连片（可回滚）
-- 库：172.16.101.2 / whale-pro · bus_no=MD00001238 · id=341502480882339840
--
-- 回滚保证：
-- 1) 事务未 COMMIT 前：执行 ROLLBACK 即可全部撤销
-- 2) 已 COMMIT：用配套脚本
--    V20260820d__rollback_rename_med_huanglianpian.sql
--    从下方 bak 表精确还原（bak 表会保留，勿删）
--
-- 技术风险（仍存在，与能否回滚无关）：
-- - 历史医嘱/库存展示名会变为「黄连片」（回滚后可再变回「黄连」）
-- - 绕过产品名称锁，无业务审计日志
-- =============================================================================

BEGIN;

-- -----------------------------------------------------------------------------
-- A. 改前快照（回滚真源；表名固定，便于回滚脚本定位）
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS _bak_20260820_med_def_md00001238;
DROP TABLE IF EXISTS _bak_20260820_charge_def_md00001238;
DROP TABLE IF EXISTS _bak_20260820_inv_item_md00001238;

CREATE TABLE _bak_20260820_med_def_md00001238 AS
SELECT id, tenant_id, bus_no, name, merchandise_name,
       py_str, wb_str, merchandise_py_str, merchandise_wb_str,
       version, updated_at, is_deleted
  FROM med_medication_definition
 WHERE bus_no = 'MD00001238'
   AND COALESCE(is_deleted, false) = false;

CREATE TABLE _bak_20260820_charge_def_md00001238 AS
SELECT c.id, c.instance_id, c.charge_name, c.price_type_code, c.lot_no,
       c.status_enum, c.version, c.updated_at, c.is_deleted
  FROM adm_charge_item_definition c
 WHERE c.instance_table = 'med_medication_definition'
   AND c.instance_id = 341502480882339840
   AND COALESCE(c.is_deleted, false) = false;

CREATE TABLE _bak_20260820_inv_item_md00001238 AS
SELECT i.id, i.item_id, i.name, i.py_str, i.wb_str, i.lot_number,
       i.quantity, i.location_id, i.version, i.updated_at, i.is_deleted
  FROM wkf_inventory_item i
 WHERE i.item_table = 'med_medication_definition'
   AND i.item_id = 341502480882339840
   AND COALESCE(i.is_deleted, false) = false;

-- 快照行数必须与现网一致，否则中止
DO $$
DECLARE
  n_def integer;
  n_chg integer;
  n_inv integer;
BEGIN
  SELECT COUNT(*) INTO n_def FROM _bak_20260820_med_def_md00001238;
  SELECT COUNT(*) INTO n_chg FROM _bak_20260820_charge_def_md00001238;
  SELECT COUNT(*) INTO n_inv FROM _bak_20260820_inv_item_md00001238;
  IF n_def <> 1 THEN
    RAISE EXCEPTION 'bak 主档行数=%（期望 1）', n_def;
  END IF;
  IF n_chg <> 5 THEN
    RAISE EXCEPTION 'bak 定价行数=%（期望 5，与预检不一致请停手）', n_chg;
  END IF;
  IF n_inv <> 2 THEN
    RAISE EXCEPTION 'bak 库存行数=%（期望 2，与预检不一致请停手）', n_inv;
  END IF;
  IF EXISTS (SELECT 1 FROM med_medication_definition
              WHERE name = '黄连片' AND COALESCE(is_deleted, false) = false) THEN
    RAISE EXCEPTION '已存在名称「黄连片」的目录项，拒绝改名以免冲突';
  END IF;
END $$;

-- -----------------------------------------------------------------------------
-- B. 主档更名
-- -----------------------------------------------------------------------------
DO $$
DECLARE
  n integer;
BEGIN
  UPDATE med_medication_definition d
     SET name               = '黄连片',
         merchandise_name   = '黄连片',
         py_str             = 'HLP',
         wb_str             = 'ALT',
         merchandise_py_str = 'HLP',
         merchandise_wb_str = 'ALT',
         version            = COALESCE(d.version, 0) + 1,
         updated_at         = NOW()
   WHERE d.id = 341502480882339840
     AND d.bus_no = 'MD00001238'
     AND d.name = '黄连'
     AND COALESCE(d.is_deleted, false) = false;

  GET DIAGNOSTICS n = ROW_COUNT;
  IF n <> 1 THEN
    RAISE EXCEPTION '主档更新行数=%（期望 1）', n;
  END IF;
END $$;

-- -----------------------------------------------------------------------------
-- C. 定价展示名（现网 charge_name 均为 NULL，写入新名）
-- -----------------------------------------------------------------------------
UPDATE adm_charge_item_definition c
   SET charge_name = '黄连片',
       version     = COALESCE(c.version, 0) + 1,
       updated_at  = NOW()
 WHERE c.instance_table = 'med_medication_definition'
   AND c.instance_id = 341502480882339840
   AND COALESCE(c.is_deleted, false) = false
   AND (c.charge_name IS NULL OR btrim(c.charge_name) = '' OR c.charge_name = '黄连');

-- -----------------------------------------------------------------------------
-- D. 库存冗余名
-- -----------------------------------------------------------------------------
UPDATE wkf_inventory_item i
   SET name       = '黄连片',
       py_str     = 'HLP',
       wb_str     = 'ALT',
       version    = COALESCE(i.version, 0) + 1,
       updated_at = NOW()
 WHERE i.item_table = 'med_medication_definition'
   AND i.item_id = 341502480882339840
   AND COALESCE(i.is_deleted, false) = false
   AND i.id IN (SELECT id FROM _bak_20260820_inv_item_md00001238);

-- -----------------------------------------------------------------------------
-- E. 回读（人工确认后再 COMMIT；有疑点改为 ROLLBACK）
-- -----------------------------------------------------------------------------
SELECT 'med' AS src, id, bus_no, name, merchandise_name, py_str, wb_str, version
  FROM med_medication_definition WHERE id = 341502480882339840
UNION ALL
SELECT 'bak', id, bus_no, name, merchandise_name, py_str, wb_str, version
  FROM _bak_20260820_med_def_md00001238;

SELECT COUNT(*) AS inv_renamed
  FROM wkf_inventory_item
 WHERE item_id = 341502480882339840 AND name = '黄连片';

SELECT COUNT(*) AS charge_renamed
  FROM adm_charge_item_definition
 WHERE instance_id = 341502480882339840 AND charge_name = '黄连片';

COMMIT;
-- 若上面 SELECT 不对：把 COMMIT 改成 ROLLBACK; 并重新执行本脚本
