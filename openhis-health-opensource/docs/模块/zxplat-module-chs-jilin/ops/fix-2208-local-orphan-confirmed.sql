-- OPS: 修复「平台 2208 已成功，但本地仍 CONFIRMED 且无撤销凭证」的孤儿结算
-- 现象：3202 明细对账报「退费状态不一致」；本院显示有效，中心显示退费；金额常为 0。
-- 动作：settlement → REVOKED；补 yb_settlement_reversal；pre_settle/fee_upload → REVOKED。
-- 幂等：仅处理仍 CONFIRMED 且无未删除撤销凭证的行。
-- 库：whale-pro（按环境改库名）

WITH targets AS (
  SELECT s.id AS settlement_record_id,
         s.setl_id,
         s.mdtrt_id,
         s.psn_no,
         s.encounter_class,
         s.medfee_sumamt,
         s.fund_pay_sumamt,
         COALESCE(s.acct_pay, 0) AS acct_pay,
         s.psn_cash_pay,
         s.fund_breakdown_json,
         s.pre_settle_id,
         s.chrg_bchno,
         s.tenant_id,
         s.updated_by,
         s.created_by,
         (
           SELECT l.id FROM yb_invoke_log l
           WHERE l.settlement_record_id = s.id
             AND l.infno = '2208'
             AND COALESCE(l.infcode, '0') IN ('0', '')
             AND COALESCE(l.is_deleted, FALSE) = FALSE
           ORDER BY l.created_at DESC
           LIMIT 1
         ) AS invoke_log_id,
         (
           SELECT l.created_at FROM yb_invoke_log l
           WHERE l.settlement_record_id = s.id
             AND l.infno = '2208'
             AND COALESCE(l.infcode, '0') IN ('0', '')
             AND COALESCE(l.is_deleted, FALSE) = FALSE
           ORDER BY l.created_at DESC
           LIMIT 1
         ) AS reversed_at
  FROM yb_settlement_record s
  WHERE COALESCE(s.is_deleted, FALSE) = FALSE
    AND s.status = 'CONFIRMED'
    AND EXISTS (
      SELECT 1 FROM yb_invoke_log l
      WHERE l.settlement_record_id = s.id
        AND l.infno = '2208'
        AND COALESCE(l.infcode, '0') IN ('0', '')
        AND COALESCE(l.is_deleted, FALSE) = FALSE
    )
    AND NOT EXISTS (
      SELECT 1 FROM yb_settlement_reversal r
      WHERE r.settlement_record_id = s.id
        AND COALESCE(r.is_deleted, FALSE) = FALSE
    )
),
upd_settle AS (
  UPDATE yb_settlement_record s
  SET status = 'REVOKED',
      updated_at = COALESCE(t.reversed_at, CURRENT_TIMESTAMP)
  FROM targets t
  WHERE s.id = t.settlement_record_id
  RETURNING s.id
),
upd_pre AS (
  UPDATE yb_clinic_pre_settle p
  SET status = 'REVOKED',
      updated_at = COALESCE(t.reversed_at, CURRENT_TIMESTAMP)
  FROM targets t
  WHERE p.id = t.pre_settle_id
    AND COALESCE(p.is_deleted, FALSE) = FALSE
    AND p.status <> 'REVOKED'
  RETURNING p.id
),
upd_fee AS (
  UPDATE yb_clinic_fee_upload f
  SET upload_status = 'REVOKED',
      updated_at = COALESCE(t.reversed_at, CURRENT_TIMESTAMP)
  FROM targets t
  WHERE f.chrg_bchno = t.chrg_bchno
    AND f.tenant_id = t.tenant_id
    AND COALESCE(f.is_deleted, FALSE) = FALSE
    AND f.upload_status <> 'REVOKED'
  RETURNING f.id
),
ins_rev AS (
  INSERT INTO yb_settlement_reversal (
    id, settlement_record_id, revoke_setl_id, mdtrt_id, psn_no, encounter_class,
    medfee_sumamt, fund_pay_sumamt, acct_pay, psn_cash_pay, fund_breakdown_json,
    invoke_log_id, reversed_at, created_at, updated_at, created_by, updated_by,
    is_deleted, deleted_at, deleted_by, tenant_id
  )
  SELECT
    t.settlement_record_id,
    t.settlement_record_id,
    t.setl_id,
    COALESCE(NULLIF(btrim(t.mdtrt_id), ''), t.setl_id),
    COALESCE(NULLIF(btrim(t.psn_no), ''), 'UNKNOWN'),
    COALESCE(NULLIF(btrim(t.encounter_class), ''), 'AMB'),
    t.medfee_sumamt,
    t.fund_pay_sumamt,
    t.acct_pay,
    t.psn_cash_pay,
    t.fund_breakdown_json,
    t.invoke_log_id,
    COALESCE(t.reversed_at, CURRENT_TIMESTAMP),
    COALESCE(t.reversed_at, CURRENT_TIMESTAMP),
    NULL,
    COALESCE(t.updated_by, t.created_by),
    NULL,
    FALSE,
    NULL,
    NULL,
    t.tenant_id
  FROM targets t
  RETURNING id
)
SELECT
  (SELECT COUNT(*) FROM upd_settle) AS updated_settlements,
  (SELECT COUNT(*) FROM upd_pre) AS updated_presettles,
  (SELECT COUNT(*) FROM upd_fee) AS updated_fee_uploads,
  (SELECT COUNT(*) FROM ins_rev) AS inserted_reversals;
