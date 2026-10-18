-- OPS: 撤回误记的扫码优惠。孙洋 PMR-2026-08-200261 的 9.19 已是 2207 医保个账（INSURANCE_ACCT），
-- 微信 112.94 为银联实收。禁止再记 SCAN_DISCOUNT。

-- ========== 1) 预览 ==========
SELECT
    p.payment_no,
    d.id,
    d.pay_enum,
    d.amount
FROM fin_payment_reconciliation p
JOIN fin_payment_rec_detail d
  ON d.reconciliation_id = p.id AND COALESCE(d.is_deleted, false) = false
WHERE p.payment_no = 'PMR-2026-08-200261'
  AND COALESCE(p.is_deleted, false) = false
ORDER BY d.id;

-- ========== 2) 删除误记优惠（幂等） ==========
UPDATE fin_payment_rec_detail d
SET is_deleted = true,
    deleted_at = NOW(),
    updated_at = NOW()
FROM fin_payment_reconciliation p
WHERE d.reconciliation_id = p.id
  AND p.payment_no = 'PMR-2026-08-200261'
  AND COALESCE(p.is_deleted, false) = false
  AND COALESCE(d.is_deleted, false) = false
  AND d.pay_enum = 'SCAN_DISCOUNT'
  AND d.amount = 9.19;

-- ========== 3) 校验：应剩统筹 + 个账 9.19 + 微信 112.94 ==========
SELECT
    p.payment_no,
    d.pay_enum,
    d.amount
FROM fin_payment_reconciliation p
JOIN fin_payment_rec_detail d
  ON d.reconciliation_id = p.id AND COALESCE(d.is_deleted, false) = false
WHERE p.payment_no = 'PMR-2026-08-200261'
  AND COALESCE(p.is_deleted, false) = false
ORDER BY d.id;
