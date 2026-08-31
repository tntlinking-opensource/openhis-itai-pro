-- unionpay_bpc Flyway V3 · 扫码 POS 回传实付（分）
-- txn_amt_fen 仍为院内下单；paid_amt_fen 为银联回传实付。
-- 禁止再用「实付 < 下单」覆盖下单额，也禁止把实付丢掉。

ALTER TABLE upc_bpc_order
    ADD COLUMN IF NOT EXISTS paid_amt_fen BIGINT;

COMMENT ON COLUMN upc_bpc_order.paid_amt_fen IS '银联/POS 回传实付金额（分）；可小于下单 txn_amt_fen';
COMMENT ON COLUMN upc_bpc_order.txn_amt_fen IS '院内下单金额（分）；不被 POS 实付覆盖';
