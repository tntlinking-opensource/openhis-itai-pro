package com.openhis.whale.report.application.preview.sample;

import java.util.LinkedHashMap;
import java.util.Map;

public final class OutpatientRefundReceiptSampleData {

    private OutpatientRefundReceiptSampleData() {
    }

    public static Map<String, Object> build() {
        Map<String, Object> root = new LinkedHashMap<>(ChargeReceiptSampleData.build());
        root.put("refund", Map.of(
                "originalPaymentNo", "PAY202606040001",
                "refundAmount", "28.00"));
        return root;
    }
}
