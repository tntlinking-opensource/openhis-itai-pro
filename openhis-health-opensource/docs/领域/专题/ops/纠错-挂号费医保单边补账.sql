-- OPS: 挂号费「平台已 2207、本地缺统筹/个账与 yb 桥表」单边补数
-- 现象：yb_settlement_record=CONFIRMED 且 fund/acct>0，fin_payment_reconciliation=SUCCESS，
--       但无 fin_payment_yb_settlement，且无 INSURANCE_FUND / INSURANCE_ACCT 明细。
-- 根因：先 2207 再扫码时 ACTIVE 预结算已 SETTLED，collect 空列表跳过 merge。
-- 动作：按 yb 回执补写统筹/个账明细 + fin_payment_yb_settlement（幂等）。
-- 不改：payment 头 display/tendered（自付现金口径保持不变）；总收入仍以 charge_item/static 为准。
-- 范围：仅 SUCCESS；CANCELLED 等终态故意跳过（例：342852581765681152）。
-- 库：whale-pro（按环境改连接）
--
-- 用法：
--   1) 先跑下方「预览」确认 targets
--   2) 再跑「补数」整段（同一事务建议）
--   3) 跑「校验」

-- ========== 1) 预览 ==========
SELECT
    r.id AS payment_id,
    r.status_enum,
    r.bill_date,
    s.id AS settlement_record_id,
    s.setl_id,
    s.chrg_bchno,
    s.mdtrt_id,
    s.account_id,
    s.fund_pay_sumamt,
    s.acct_pay,
    s.psn_cash_pay,
    s.medfee_sumamt,
    EXISTS (
        SELECT 1
        FROM fin_payment_yb_settlement y
        WHERE y.reconciliation_id = s.payment_id
          AND y.setl_id = s.setl_id
          AND y.is_deleted = FALSE
    ) AS has_bridge,
    EXISTS (
        SELECT 1
        FROM fin_payment_rec_detail d
        WHERE d.reconciliation_id = s.payment_id
          AND d.pay_enum = 'INSURANCE_FUND'
          AND d.is_deleted = FALSE
    ) AS has_fund_detail,
    EXISTS (
        SELECT 1
        FROM fin_payment_rec_detail d
        WHERE d.reconciliation_id = s.payment_id
          AND d.pay_enum = 'INSURANCE_ACCT'
          AND d.is_deleted = FALSE
    ) AS has_acct_detail
FROM yb_settlement_record s
JOIN fin_payment_reconciliation r ON r.id = s.payment_id
WHERE COALESCE(s.is_deleted, FALSE) = FALSE
  AND s.status = 'CONFIRMED'
  AND r.status_enum = 'SUCCESS'
  AND COALESCE(s.fund_pay_sumamt, 0) + COALESCE(s.acct_pay, 0) > 0
  AND (
      NOT EXISTS (
          SELECT 1
          FROM fin_payment_yb_settlement y
          WHERE y.reconciliation_id = s.payment_id
            AND y.setl_id = s.setl_id
            AND y.is_deleted = FALSE
      )
      OR (
          COALESCE(s.fund_pay_sumamt, 0) > 0
          AND NOT EXISTS (
              SELECT 1
              FROM fin_payment_rec_detail d
              WHERE d.reconciliation_id = s.payment_id
                AND d.pay_enum = 'INSURANCE_FUND'
                AND d.is_deleted = FALSE
          )
      )
      OR (
          COALESCE(s.acct_pay, 0) > 0
          AND NOT EXISTS (
              SELECT 1
              FROM fin_payment_rec_detail d
              WHERE d.reconciliation_id = s.payment_id
                AND d.pay_enum = 'INSURANCE_ACCT'
                AND d.is_deleted = FALSE
          )
      )
  )
ORDER BY r.bill_date NULLS LAST, r.id;

-- ========== 2) 补数（幂等） ==========
BEGIN;

WITH targets AS (
    SELECT
        s.id AS settlement_record_id,
        s.payment_id AS reconciliation_id,
        s.setl_id,
        s.chrg_bchno,
        s.mdtrt_id,
        COALESCE(s.account_id, cash.account_id) AS account_id,
        cash.account_code,
        COALESCE(s.fund_pay_sumamt, 0) AS fund_pay_sumamt,
        COALESCE(s.acct_pay, 0) AS acct_pay,
        s.tenant_id,
        COALESCE(s.created_by, r.created_by) AS created_by,
        COALESCE(r.bill_date, s.created_at, CURRENT_TIMESTAMP) AS bill_ts,
        COALESCE(s.created_at, CURRENT_TIMESTAMP) AS created_at
    FROM yb_settlement_record s
    JOIN fin_payment_reconciliation r ON r.id = s.payment_id
    LEFT JOIN LATERAL (
        SELECT d.account_id, d.account_code
        FROM fin_payment_rec_detail d
        WHERE d.reconciliation_id = s.payment_id
          AND d.is_deleted = FALSE
          AND d.pay_enum NOT IN ('INSURANCE_FUND', 'INSURANCE_ACCT')
        ORDER BY d.id
        LIMIT 1
    ) cash ON TRUE
    WHERE COALESCE(s.is_deleted, FALSE) = FALSE
      AND s.status = 'CONFIRMED'
      AND r.status_enum = 'SUCCESS'
      AND COALESCE(s.fund_pay_sumamt, 0) + COALESCE(s.acct_pay, 0) > 0
      AND COALESCE(s.account_id, cash.account_id) IS NOT NULL
),
ins_fund AS (
    INSERT INTO fin_payment_rec_detail (
        id, tenant_id, created_at, updated_at, created_by, updated_by,
        is_deleted, deleted_at, deleted_by, version,
        reconciliation_id, predecessor_id, target_enum,
        account_id, account_code, pay_enum, pay_level_enum,
        amount, return_amount, charge_amount,
        pay_trans_no, pay_trans_text, pay_trans_date,
        before_balance, after_balance, result_enum
    )
    SELECT
        t.settlement_record_id,                 -- 确定性 id：与 yb_settlement_record.id 对齐
        t.tenant_id,
        t.created_at,
        t.created_at,
        t.created_by,
        t.created_by,
        FALSE,
        NULL,
        NULL,
        0,
        t.reconciliation_id,
        NULL,
        NULL,
        t.account_id,
        t.account_code,
        'INSURANCE_FUND',
        NULL,
        t.fund_pay_sumamt,
        NULL,
        t.fund_pay_sumamt,
        t.setl_id,
        NULL,
        t.bill_ts,
        NULL,
        NULL,
        'SUCCESS'
    FROM targets t
    WHERE t.fund_pay_sumamt > 0
      AND NOT EXISTS (
          SELECT 1
          FROM fin_payment_rec_detail d
          WHERE d.reconciliation_id = t.reconciliation_id
            AND d.pay_enum = 'INSURANCE_FUND'
            AND d.is_deleted = FALSE
      )
      AND NOT EXISTS (
          SELECT 1 FROM fin_payment_rec_detail d WHERE d.id = t.settlement_record_id
      )
    RETURNING id, reconciliation_id
),
ins_acct AS (
    INSERT INTO fin_payment_rec_detail (
        id, tenant_id, created_at, updated_at, created_by, updated_by,
        is_deleted, deleted_at, deleted_by, version,
        reconciliation_id, predecessor_id, target_enum,
        account_id, account_code, pay_enum, pay_level_enum,
        amount, return_amount, charge_amount,
        pay_trans_no, pay_trans_text, pay_trans_date,
        before_balance, after_balance, result_enum
    )
    SELECT
        t.settlement_record_id + 1,             -- 个账明细：settlement_record_id+1
        t.tenant_id,
        t.created_at,
        t.created_at,
        t.created_by,
        t.created_by,
        FALSE,
        NULL,
        NULL,
        0,
        t.reconciliation_id,
        NULL,
        NULL,
        t.account_id,
        t.account_code,
        'INSURANCE_ACCT',
        NULL,
        t.acct_pay,
        NULL,
        t.acct_pay,
        t.setl_id,
        NULL,
        t.bill_ts,
        NULL,
        NULL,
        'SUCCESS'
    FROM targets t
    WHERE t.acct_pay > 0
      AND NOT EXISTS (
          SELECT 1
          FROM fin_payment_rec_detail d
          WHERE d.reconciliation_id = t.reconciliation_id
            AND d.pay_enum = 'INSURANCE_ACCT'
            AND d.is_deleted = FALSE
      )
      AND NOT EXISTS (
          SELECT 1 FROM fin_payment_rec_detail d WHERE d.id = t.settlement_record_id + 1
      )
    RETURNING id, reconciliation_id
),
ins_bridge AS (
    INSERT INTO fin_payment_yb_settlement (
        id, reconciliation_id, setl_id, chrg_bchno, mdtrt_id,
        settlement_record_id, account_id, status,
        created_at, updated_at, created_by, updated_by,
        is_deleted, deleted_at, deleted_by, tenant_id
    )
    SELECT
        t.settlement_record_id,                 -- 桥表 id 与 settlement_record 对齐
        t.reconciliation_id,
        t.setl_id,
        t.chrg_bchno,
        t.mdtrt_id,
        t.settlement_record_id,
        t.account_id,
        'CONFIRMED',
        t.created_at,
        t.created_at,
        t.created_by,
        t.created_by,
        FALSE,
        NULL,
        NULL,
        t.tenant_id
    FROM targets t
    WHERE NOT EXISTS (
        SELECT 1
        FROM fin_payment_yb_settlement y
        WHERE y.reconciliation_id = t.reconciliation_id
          AND y.setl_id = t.setl_id
          AND y.is_deleted = FALSE
    )
      AND NOT EXISTS (
          SELECT 1 FROM fin_payment_yb_settlement y WHERE y.id = t.settlement_record_id
      )
    RETURNING id, reconciliation_id
)
SELECT
    (SELECT COUNT(*) FROM targets) AS target_count,
    (SELECT COUNT(*) FROM ins_fund) AS inserted_fund_details,
    (SELECT COUNT(*) FROM ins_acct) AS inserted_acct_details,
    (SELECT COUNT(*) FROM ins_bridge) AS inserted_yb_bridges;

-- 确认无误后 COMMIT；异常则 ROLLBACK
COMMIT;

-- ========== 3) 校验 ==========
-- 本轮 8/7 两笔挂号单边（交账 CS2026-08-080001 差额 5.70 来源）
-- payment_id: 343915222401552384 / 343934167087058944
SELECT
    d.reconciliation_id,
    d.pay_enum,
    d.amount,
    d.pay_trans_no
FROM fin_payment_rec_detail d
WHERE d.reconciliation_id IN (343915222401552384, 343934167087058944)
  AND COALESCE(d.is_deleted, FALSE) = FALSE
ORDER BY d.reconciliation_id, d.pay_enum, d.id;

SELECT
    y.reconciliation_id,
    y.setl_id,
    y.settlement_record_id,
    y.status
FROM fin_payment_yb_settlement y
WHERE y.reconciliation_id IN (343915222401552384, 343934167087058944)
  AND COALESCE(y.is_deleted, FALSE) = FALSE;

-- 应返回 0 行：SUCCESS 单边已清零
SELECT
    r.id AS payment_id,
    r.payment_no,
    s.id AS settlement_record_id,
    s.fund_pay_sumamt,
    s.acct_pay
FROM yb_settlement_record s
JOIN fin_payment_reconciliation r ON r.id = s.payment_id
WHERE COALESCE(s.is_deleted, FALSE) = FALSE
  AND s.status = 'CONFIRMED'
  AND r.status_enum = 'SUCCESS'
  AND COALESCE(s.fund_pay_sumamt, 0) + COALESCE(s.acct_pay, 0) > 0
  AND (
      NOT EXISTS (
          SELECT 1
          FROM fin_payment_yb_settlement y
          WHERE y.reconciliation_id = s.payment_id
            AND y.setl_id = s.setl_id
            AND COALESCE(y.is_deleted, FALSE) = FALSE
      )
      OR (
          COALESCE(s.fund_pay_sumamt, 0) > 0
          AND NOT EXISTS (
              SELECT 1
              FROM fin_payment_rec_detail d
              WHERE d.reconciliation_id = s.payment_id
                AND d.pay_enum = 'INSURANCE_FUND'
                AND COALESCE(d.is_deleted, FALSE) = FALSE
          )
      )
      OR (
          COALESCE(s.acct_pay, 0) > 0
          AND NOT EXISTS (
              SELECT 1
              FROM fin_payment_rec_detail d
              WHERE d.reconciliation_id = s.payment_id
                AND d.pay_enum = 'INSURANCE_ACCT'
                AND COALESCE(d.is_deleted, FALSE) = FALSE
          )
      )
  );
