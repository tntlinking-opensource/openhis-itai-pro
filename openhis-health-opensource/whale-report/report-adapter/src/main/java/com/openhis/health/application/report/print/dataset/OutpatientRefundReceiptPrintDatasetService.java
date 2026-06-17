/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.report.print.dataset;

import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentReconciliationQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationQueryService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 门诊退费凭条打印数据集（基于退费支付单，bindPath 对齐 {@code outpatient_refund_receipt}）。
 */
@Service
@RequiredArgsConstructor
public class OutpatientRefundReceiptPrintDatasetService {

    private final ChargeReceiptPrintDatasetService chargeReceiptPrintDatasetService;
    private final PaymentReconciliationQueryService paymentReconciliationQueryService;    /**
     * 组装打印数据集 root Map（供 Port / 预览 bindPath 绑定）。
     *
     * @param params 业务参数（如 encounterId、paymentId、dateFrom 等）
     * @return 数据集 root；必填参数缺失时返回空 Map
     */

    public Map<String, Object> build(Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return Map.of();
        }
        Long paymentId = extractLong(params, "paymentId", "payment_id");
        if (paymentId == null) {
            return Map.of();
        }

        Map<String, Object> root = chargeReceiptPrintDatasetService.build(params);
        if (root.isEmpty()) {
            return Map.of();
        }

        Long originalPaymentId = extractLong(params, "originalPaymentId", "original_payment_id");
        PaymentReconciliationQueryModel originalPayment = null;
        if (originalPaymentId != null) {
            originalPayment = paymentReconciliationQueryService.mustGetPayment(originalPaymentId);
        }

        PaymentReconciliationQueryModel refundPayment = paymentReconciliationQueryService.mustGetPayment(paymentId);
        BigDecimal refundAmount = refundPayment.getDisplayAmount() != null
                ? refundPayment.getDisplayAmount().abs()
                : (refundPayment.getTenderedAmount() != null
                ? refundPayment.getTenderedAmount().abs()
                : null);

        Map<String, Object> refund = new LinkedHashMap<>();
        refund.put("originalPaymentNo", originalPayment != null
                ? StringUtils.defaultIfBlank(originalPayment.getPaymentNo(), "—")
                : "—");
        refund.put("refundAmount", formatMoney(refundAmount));
        root.put("refund", refund);
        return root;
    }

    private static String formatMoney(BigDecimal value) {
        if (value == null) {
            return "0.00";
        }
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static Long extractLong(Map<String, Object> params, String... keys) {
        for (String key : keys) {
            Object raw = params.get(key);
            if (raw instanceof Number number) {
                return number.longValue();
            }
            if (raw instanceof String text && StringUtils.isNotBlank(text)) {
                try {
                    return Long.parseLong(text.trim());
                } catch (NumberFormatException ignored) {
                    // next key
                }
            }
        }
        return null;
    }
}
