-- OPS: 孙泽 PMR-2026-08-200261 银联实收 112.94，HIS 误记微信 122.13
-- 商户单号 20260820151040410426517256；差额 9.19 = 本笔医保个账。
-- 根因：POS 实付 < 下单时被当成优惠丢掉。
-- 动作：BPC 记下 paid_amt_fen；微信渠道/支付头与银联对账单对齐 112.94。
-- 注意：收费项仍 357；统筹+个账+微信 = 347.81，短收 9.19（医保口径个人现金 122.13 − 实收 112.94）。
-- 须先跑 unionpay_bpc V3（paid_amt_fen 列）。

-- ========== 1) 预览 ==========
SELECT
    p.id,
    p.payment_no,
    p.display_amount,
    p.tendered_amount,
    d.pay_enum,
    d.amount,
    o.trade_no,
    o.txn_amt_fen,
    o.paid_amt_fen
FROM fin_payment_reconciliation p
JOIN fin_payment_rec_detail d
  ON d.reconciliation_id = p.id AND COALESCE(d.is_deleted, false) = false
LEFT JOIN upc_bpc_order o
  ON o.payment_id = p.id AND COALESCE(o.is_deleted, false) = false AND o.tran_type = 'C'
WHERE p.payment_no = 'PMR-2026-08-200261'
  AND COALESCE(p.is_deleted, false) = false
ORDER BY d.id;

-- ========== 2) 修复 ==========
UPDATE upc_bpc_order
SET paid_amt_fen = 11294,
    updated_at = NOW()
WHERE trade_no = '20260820151040410426517256'
  AND COALESCE(is_deleted, false) = false
  AND txn_amt_fen = 12213
  AND (paid_amt_fen IS NULL OR paid_amt_fen <> 11294);

UPDATE fin_payment_rec_detail d
SET amount = 112.94,
    charge_amount = 112.94,
    updated_at = NOW()
FROM fin_payment_reconciliation p
WHERE d.reconciliation_id = p.id
  AND p.payment_no = 'PMR-2026-08-200261'
  AND COALESCE(p.is_deleted, false) = false
  AND COALESCE(d.is_deleted, false) = false
  AND d.pay_enum = 'WECHAT'
  AND d.amount = 122.13;

UPDATE fin_payment_reconciliation
SET display_amount = 112.94,
    tendered_amount = 112.94,
    updated_at = NOW()
WHERE payment_no = 'PMR-2026-08-200261'
  AND COALESCE(is_deleted, false) = false
  AND display_amount = 122.13;

-- ========== 3) 校验 ==========
SELECT
    p.payment_no,
    p.display_amount,
    p.tendered_amount,
    d.pay_enum,
    d.amount,
    o.txn_amt_fen AS order_fen,
    o.paid_amt_fen AS paid_fen
FROM fin_payment_reconciliation p
JOIN fin_payment_rec_detail d
  ON d.reconciliation_id = p.id AND COALESCE(d.is_deleted, false) = false
LEFT JOIN upc_bpc_order o
  ON o.payment_id = p.id AND COALESCE(o.is_deleted, false) = false AND o.tran_type = 'C'
WHERE p.payment_no = 'PMR-2026-08-200261'
  AND COALESCE(p.is_deleted, false) = false
ORDER BY d.id;
