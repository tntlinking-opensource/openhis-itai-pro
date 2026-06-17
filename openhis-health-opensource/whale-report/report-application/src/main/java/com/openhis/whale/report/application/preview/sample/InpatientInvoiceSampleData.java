package com.openhis.whale.report.application.preview.sample;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

public final class InpatientInvoiceSampleData {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private InpatientInvoiceSampleData() {
    }

    public static Map<String, Object> build() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("hospital", Map.of("name", "示例人民医院"));

        Map<String, Object> inpatientInvoice = new LinkedHashMap<>();
        inpatientInvoice.put("code", "0000000000");
        inpatientInvoice.put("no", "IP202606040001");
        inpatientInvoice.put("amount", "1280.50");
        inpatientInvoice.put("date", DATE_FMT.format(LocalDateTime.now()));
        inpatientInvoice.put("verifyUrl", "");
        root.put("inpatientInvoice", inpatientInvoice);

        Map<String, Object> patient = new LinkedHashMap<>();
        patient.put("name", "张三");
        patient.put("inpatientNo", "ZY20260604001");
        patient.put("deptName", "内一科");
        patient.put("bedNo", "12床");
        patient.put("payerInfo", "内一科 12床");
        root.put("patient", patient);

        Map<String, Object> settlement = new LinkedHashMap<>();
        settlement.put("amount", "1280.50");
        settlement.put("amountChinese", "壹仟贰佰捌拾元伍角");
        settlement.put("settleTime", TIME_FMT.format(LocalDateTime.now()));
        settlement.put("paymentNo", "PAY202606040001");
        settlement.put("payMethodText", "微信");
        root.put("settlement", settlement);

        Map<String, Object> deposit = new LinkedHashMap<>();
        deposit.put("prepayAmount", "2000.00");
        deposit.put("balanceHint", "719.50");
        root.put("deposit", deposit);

        root.put("operator", Map.of("name", "收费员A"));
        root.put("system", Map.of("printTime", TIME_FMT.format(LocalDateTime.now())));
        return root;
    }
}
