-- OPS: 修复「平台 2601 已成功，但本地仍 CONFIRMED / 仍参与对账」的孤儿结算
-- 现象：3201 本地比中心多金额/多笔数；3202 报「结算中心不存在该结算数据或对账结果已平」。
-- 根因：2601 平台冲正成功后，本地未与平台同态；或误按 2208 写了 reversal（一收一退计 2）。
-- 口径：2601=冲正（视同未发生）→ 软删 settlement，不保留对账正向/反向行。
-- 动作：settlement → REVOKED + is_deleted；软删其 reversal（若有）；
--       pre_settle/fee_upload/fin_payment_yb_settlement → REVOKED；补 correction（若无）。
-- 幂等：已软删的跳过。
-- 若已执行过 v1（只 REVOKED+reversal），请改跑 fix-2601-local-orphan-confirmed-v2.sql。
-- 库：whale-pro

WITH targets AS (
  SELECT s.id AS settlement_record_id,
         s.setl_id,
         s.mdtrt_id,
         s.psn_no,
         s.encounter_class,
         s.pre_settle_id,
         s.chrg_bchno,
         s.tenant_id,
         s.updated_by,
         s.created_by,
         COALESCE(NULLIF(btrim(s.msgid_2207), ''), NULLIF(btrim(s.medins_setl_id), ''), s.setl_id) AS omsgid,
         (
           SELECT l.id FROM yb_invoke_log l
           WHERE l.settlement_record_id = s.id
             AND l.infno = '2601'
             AND COALESCE(l.infcode, '0') IN ('0', '')
             AND COALESCE(l.is_deleted, FALSE) = FALSE
           ORDER BY l.created_at DESC
           LIMIT 1
         ) AS invoke_log_id,
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
  WHERE COALESCE(s.is_deleted, FALSE) = FALSE
    AND EXISTS (
      SELECT 1 FROM yb_invoke_log l
      WHERE l.settlement_record_id = s.id
        AND l.infno = '2601'
        AND COALESCE(l.infcode, '0') IN ('0', '')
        AND COALESCE(l.is_deleted, FALSE) = FALSE
    )
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
),
ins_corr AS (
  INSERT INTO yb_settlement_correction (
    id, settlement_record_id, psn_no, omsgid, oinfno, encounter_class,
    invoke_log_id, corrected_at, created_at, updated_at, created_by, updated_by,
    is_deleted, deleted_at, deleted_by, tenant_id
  )
  SELECT
    COALESCE(t.invoke_log_id, t.settlement_record_id),
    t.settlement_record_id,
    COALESCE(NULLIF(btrim(t.psn_no), ''), 'UNKNOWN'),
    left(COALESCE(t.omsgid, t.setl_id), 30),
    '2207',
    COALESCE(NULLIF(btrim(t.encounter_class), ''), 'AMB'),
    t.invoke_log_id,
    COALESCE(t.corrected_at, CURRENT_TIMESTAMP),
    COALESCE(t.corrected_at, CURRENT_TIMESTAMP),
    NULL,
    COALESCE(t.updated_by, t.created_by),
    NULL,
    FALSE,
    NULL,
    NULL,
    t.tenant_id
  FROM targets t
  WHERE NOT EXISTS (
    SELECT 1 FROM yb_settlement_correction c
    WHERE c.settlement_record_id = t.settlement_record_id
      AND COALESCE(c.is_deleted, FALSE) = FALSE
  )
  RETURNING id
)
SELECT
  (SELECT COUNT(*) FROM soft_settle) AS soft_deleted_settlements,
  (SELECT COUNT(*) FROM soft_rev) AS soft_deleted_reversals,
  (SELECT COUNT(*) FROM upd_pre) AS updated_presettles,
  (SELECT COUNT(*) FROM upd_fee) AS updated_fee_uploads,
  (SELECT COUNT(*) FROM upd_pay_bridge) AS updated_payment_bridges,
  (SELECT COUNT(*) FROM ins_corr) AS inserted_corrections;
