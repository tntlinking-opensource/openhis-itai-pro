package com.openhis.whale.report.application.preview.sample;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

public final class InsuranceSettlementSampleData {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private InsuranceSettlementSampleData() {
    }

    public static Map<String, Object> build() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("hospital", Map.of("name", "示例人民医院"));

        Map<String, Object> patient = new LinkedHashMap<>();
        patient.put("name", "张三");
        patient.put("inpatientNo", "ZY20260604001");
        patient.put("gender", "男");
        patient.put("age", "45岁");
        patient.put("insuranceType", "城镇职工医保");
        root.put("patient", patient);

        Map<String, Object> insurance = new LinkedHashMap<>();
        insurance.put("settleNo", "YB202606040001");
        insurance.put("mdtrtId", "MD202606040001");
        insurance.put("contractNo", "310000");
        insurance.put("settleTime", TIME_FMT.format(LocalDateTime.now()));
        insurance.put("clearedText", "未清算");
        root.put("insurance", insurance);

        Map<String, Object> insuranceFee = new LinkedHashMap<>();
        insuranceFee.put("totalAmount", "1280.50");
        insuranceFee.put("fundPayAmount", "960.00");
        insuranceFee.put("personalPayAmount", "200.00");
        insuranceFee.put("cashPayAmount", "120.50");
        insuranceFee.put("insuranceInFee", "1160.00");
        insuranceFee.put("insuranceOutFee", "120.50");
        root.put("insuranceFee", insuranceFee);

        Map<String, Object> settlement = new LinkedHashMap<>();
        settlement.put("payMethodText", "微信");
        settlement.put("paymentNo", "PAY202606040001");
        root.put("settlement", settlement);

        root.put("operator", Map.of("name", "收费员A"));
        root.put("system", Map.of("printTime", TIME_FMT.format(LocalDateTime.now())));
        return root;
    }
}
