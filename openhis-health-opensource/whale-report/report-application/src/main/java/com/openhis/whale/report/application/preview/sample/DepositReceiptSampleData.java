package com.openhis.whale.report.application.preview.sample;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

public final class DepositReceiptSampleData {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private DepositReceiptSampleData() {
    }

    public static Map<String, Object> build() {
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> hospital = new LinkedHashMap<>();
        hospital.put("name", "示例人民医院");
        hospital.put("miniProgramImageUrl", ChargeReceiptSampleData.sampleMiniProgramImageUrl());
        root.put("hospital", hospital);
        Map<String, Object> deposit = new LinkedHashMap<>();
        deposit.put("ticketNo", "PREP202606040001");
        deposit.put("tradeTime", TIME_FMT.format(LocalDateTime.now()));
        deposit.put("bizTypeText", "收预交金");
        deposit.put("amount", "1000.00");
        root.put("deposit", deposit);
        root.put("patient", Map.of(
                "name", "张三",
                "busNo", "P20260604001"));
        root.put("encounter", Map.of(
                "busNo", "ZY20260604001",
                "wardBed", "内一科 12床"));
        root.put("payment", Map.of("methodText", "现金"));
        root.put("account", Map.of("balance", "3500.00"));
        root.put("operator", Map.of("name", "收费员A"));
        root.put("system", Map.of("printTime", TIME_FMT.format(LocalDateTime.now())));
        return root;
    }
}
