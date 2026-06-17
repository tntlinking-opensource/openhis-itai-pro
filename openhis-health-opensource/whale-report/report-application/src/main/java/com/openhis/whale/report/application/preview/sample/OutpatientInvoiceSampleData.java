package com.openhis.whale.report.application.preview.sample;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

public final class OutpatientInvoiceSampleData {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private OutpatientInvoiceSampleData() {
    }

    public static Map<String, Object> build() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("hospital", Map.of("name", "示例人民医院"));

        Map<String, Object> invoice = new LinkedHashMap<>();
        invoice.put("code", "0000000000");
        invoice.put("no", "OP202606040001");
        invoice.put("amount", "128.50");
        invoice.put("amountChinese", "壹佰贰拾捌元伍角");
        invoice.put("date", DATE_FMT.format(LocalDateTime.now()));
        invoice.put("verifyUrl", "");
        root.put("invoice", invoice);

        Map<String, Object> patient = new LinkedHashMap<>();
        patient.put("name", "李四");
        patient.put("busNo", "P20260604001");
        patient.put("payerInfo", "P20260604001");
        root.put("patient", patient);

        Map<String, Object> encounter = new LinkedHashMap<>();
        encounter.put("busNo", "MZ20260604001");
        root.put("encounter", encounter);

        Map<String, Object> payment = new LinkedHashMap<>();
        payment.put("methodText", "微信");
        payment.put("paymentNo", "PAY202606040001");
        root.put("payment", payment);

        root.put("operator", Map.of("name", "收费员A"));
        root.put("system", Map.of("printTime", TIME_FMT.format(LocalDateTime.now())));
        return root;
    }
}
