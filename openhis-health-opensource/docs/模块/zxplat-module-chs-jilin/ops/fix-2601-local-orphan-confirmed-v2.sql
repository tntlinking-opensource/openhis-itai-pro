-- OPS v2: 纠正「2601 冲正」误按 2208 一收一退修复的数据
--
-- 背景：
--   v1 (fix-2601-local-orphan-confirmed.sql) 把 2601 孤儿改成 REVOKED + 写 reversal，
--   金额能抵净，但笔数按一收一退计 2；平台 2601 冲正视同未发生（计 0）。
--   例如 8/11 390：修后本地 2539.83/71，中心 2539.83/69 → 金额平、笔数仍多 2。
--
-- 动作（幂等）：
--   对「存在成功 2601」的结算：软删 settlement + 软删其 reversal（对账不再装载）。
--   保留 yb_settlement_correction / invoke_log 审计。
--   同步 pre_settle / fee_upload / fin_payment_yb_settlement → REVOKED（若尚未）。
--
-- 库：whale-pro
-- 执行后：刷新日对账页，勿用手填旧金额；让系统按本地汇总重报 3201。

WITH targets AS (
  SELECT s.id AS settlement_record_id,
         s.setl_id,
         s.pre_settle_id,
         s.chrg_bchno,
         s.tenant_id,
         (
           SELECT l.created_at FROM yb_invoke_log l
           WHERE l.settlement_record_id = s.id
             AND l.infno = '2601'
             AND COALESCE(l.infcode, '0') IN ('0', '')
             AND COALESCE(l.is_deleted, FALSE) = FALSE
           ORDER BY l.created_at DESC
           LIMIT 1
         ) AS corrected_at
  FROM yb_settlement_record s
  WHERE EXISTS (
      SELECT 1 FROM yb_invoke_log l
      WHERE l.settlement_record_id = s.id
        AND l.infno = '2601'
        AND COALESCE(l.infcode, '0') IN ('0', '')
        AND COALESCE(l.is_deleted, FALSE) = FALSE
    )
    -- 已软删的跳过；仍参与对账的（含 v1 改成 REVOKED 的）纳入
    AND COALESCE(s.is_deleted, FALSE) = FALSE
),
soft_settle AS (
  UPDATE yb_settlement_record s
  SET status = 'REVOKED',
      is_deleted = TRUE,
      deleted_at = COALESCE(t.corrected_at, CURRENT_TIMESTAMP),
      updated_at = COALESCE(t.corrected_at, CURRENT_TIMESTAMP)
  FROM targets t
  WHERE s.id = t.settlement_record_id
  RETURNING s.id
),
soft_rev AS (
  UPDATE yb_settlement_reversal r
  SET is_deleted = TRUE,
      deleted_at = COALESCE(t.corrected_at, CURRENT_TIMESTAMP),
      updated_at = COALESCE(t.corrected_at, CURRENT_TIMESTAMP)
  FROM targets t
  WHERE r.settlement_record_id = t.settlement_record_id
    AND COALESCE(r.is_deleted, FALSE) = FALSE
  RETURNING r.id
),
upd_pre AS (
  UPDATE yb_clinic_pre_settle p
  SET status = 'REVOKED',
      updated_at = COALESCE(t.corrected_at, CURRENT_TIMESTAMP)
  FROM targets t
  WHERE p.id = t.pre_settle_id
    AND COALESCE(p.is_deleted, FALSE) = FALSE
    AND p.status <> 'REVOKED'
  RETURNING p.id
),
upd_fee AS (
  UPDATE yb_clinic_fee_upload f
  SET upload_status = 'REVOKED',
      updated_at = COALESCE(t.corrected_at, CURRENT_TIMESTAMP)
  FROM targets t
  WHERE f.chrg_bchno = t.chrg_bchno
    AND f.tenant_id = t.tenant_id
    AND COALESCE(f.is_deleted, FALSE) = FALSE
    AND f.upload_status <> 'REVOKED'
  RETURNING f.id
),
upd_pay_bridge AS (
  UPDATE fin_payment_yb_settlement y
  SET status = 'REVOKED',
      updated_at = COALESCE(t.corrected_at, CURRENT_TIMESTAMP)
  FROM targets t
  WHERE COALESCE(y.is_deleted, FALSE) = FALSE
    AND y.status <> 'REVOKED'
    AND (
      y.settlement_record_id = t.settlement_record_id
      OR y.setl_id = t.setl_id
    )
  RETURNING y.id
)
SELECT
  (SELECT COUNT(*) FROM soft_settle) AS soft_deleted_settlements,
  (SELECT COUNT(*) FROM soft_rev) AS soft_deleted_reversals,
  (SELECT COUNT(*) FROM upd_pre) AS updated_presettles,
  (SELECT COUNT(*) FROM upd_fee) AS updated_fee_uploads,
  (SELECT COUNT(*) FROM upd_pay_bridge) AS updated_payment_bridges;
