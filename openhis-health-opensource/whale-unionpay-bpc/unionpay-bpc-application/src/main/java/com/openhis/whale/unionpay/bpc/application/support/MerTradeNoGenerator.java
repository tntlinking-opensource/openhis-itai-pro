package com.openhis.whale.unionpay.bpc.application.support;

import com.openhis.whale.unionpay.bpc.application.config.BpcContractConfig;

/**
 * BPC 商户订单号生成（默认 {@code PAYMENT_INDEX_TS}）。
 */
public final class MerTradeNoGenerator {

    private MerTradeNoGenerator() {
    }

    public static String generate(Long paymentId, Integer detailIndex, BpcContractConfig config) {
        String rule = config != null && config.getMerTradeNoRule() != null
                ? config.getMerTradeNoRule()
                : "PAYMENT_INDEX_TS";
        if ("PAYMENT_INDEX_TS".equalsIgnoreCase(rule)) {
            int index = detailIndex != null ? detailIndex : 0;
            return paymentId + "_" + index + "_" + System.currentTimeMillis();
        }
        throw new IllegalStateException("不支持的 mer_trade_no_rule: " + rule);
    }

    public static String generateRefundMerTradeNo(String originalMerTradeNo) {
        return originalMerTradeNo + "_R_" + System.currentTimeMillis();
    }
}
