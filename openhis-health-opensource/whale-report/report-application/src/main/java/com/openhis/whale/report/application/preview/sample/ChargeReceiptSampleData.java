package com.openhis.whale.report.application.preview.sample;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ChargeReceiptSampleData {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** 预览用 1×1 PNG，便于设计器/样例 PDF 展示 image 元素 */
    private static final String SAMPLE_MINI_PROGRAM_IMAGE =
            "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";

    private ChargeReceiptSampleData() {
    }

    public static String sampleMiniProgramImageUrl() {
        return SAMPLE_MINI_PROGRAM_IMAGE;
    }

    public static Map<String, Object> build() {
        String now = TIME_FMT.format(LocalDateTime.now());
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> hospital = new LinkedHashMap<>();
        hospital.put("name", "东北师范大学医院");
        hospital.put("miniProgramImageUrl", SAMPLE_MINI_PROGRAM_IMAGE);
        root.put("hospital", hospital);

        Map<String, Object> charge = new LinkedHashMap<>();
        charge.put("busNo", "PAY202606110001");
        charge.put("occurrenceTime", now);
        charge.put("subtotalAmount", "22.25");
        charge.put("discountAmount", "0.00");
        charge.put("totalAmount", "22.25");
        charge.put("lines", sampleLines());
        root.put("charge", charge);

        Map<String, Object> patient = new LinkedHashMap<>();
        patient.put("name", "董莹");
        patient.put("genderText", "女");
        patient.put("ageText", "58");
        patient.put("patientTypeText", "职工医保");
        patient.put("outpatientNo", "M2606110009175777");
        patient.put("internalOutpatientNo", "");
        patient.put("busNo", "M2606110009175777");
        root.put("patient", patient);

        root.put("encounter", Map.of("busNo", "M2606110009175777"));

        Map<String, Object> paymentSummary = new LinkedHashMap<>();
        paymentSummary.put("totalAmount", "22.25");
        paymentSummary.put("actualReceivedAmount", "0");
        paymentSummary.put("cardPayAmount", "11.24");
        paymentSummary.put("accountBalanceBefore", "7108.00");
        paymentSummary.put("seriousDiseaseFund", "0");
        paymentSummary.put("accountBalanceAfter", "7096.76");
        paymentSummary.put("medicalAssistance", "0");
        paymentSummary.put("civilServantSubsidy", "0");
        paymentSummary.put("retiredOfficialFund", "0");
        paymentSummary.put("povertyAlleviation", "0");
        paymentSummary.put("mutualAidPay", "0");
        paymentSummary.put("workInjuryFund", "0");
        paymentSummary.put("insuranceFundTotal", "11.01");
        paymentSummary.put("wechatCardPay", "");
        paymentSummary.put("fundPayAmount", "11.01");
        root.put("paymentSummary", paymentSummary);

        root.put("payment", Map.of(
                "methodText", "医保",
                "paidAmount", "0",
                "changeAmount", "0.00"));

        root.put("footer", Map.of(
                "operatorName", "柴华",
                "chargeTime", now));

        root.put("operator", Map.of("name", "柴华"));
        Map<String, Object> invoice = new LinkedHashMap<>();
        invoice.put("statusText", "未开票，无电子票据");
        invoice.put("verifyUrl", "https://invoice.example.com/verify/E202606110001");
        root.put("invoice", invoice);
        root.put("system", Map.of("printTime", now));
        return root;
    }

    private static List<Map<String, Object>> sampleLines() {
        List<Map<String, Object>> lines = new ArrayList<>();
        lines.add(line("佐匹克隆片", "7.5mg*12", "1", "22.25", "Z"));
        return lines;
    }

    private static Map<String, Object> line(
            String itemName, String specText, String quantity, String lineAmount, String categoryCode) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("itemName", itemName);
        row.put("specText", specText);
        row.put("quantity", quantity);
        row.put("lineAmount", lineAmount);
        row.put("categoryCode", categoryCode);
        return row;
    }
}
