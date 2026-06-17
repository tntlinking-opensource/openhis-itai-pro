package com.openhis.whale.report.application.preview.sample;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class InpatientDailyChargeSampleData {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private InpatientDailyChargeSampleData() {
    }

    public static Map<String, Object> build() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("hospital", Map.of("name", "示例人民医院"));
        Map<String, Object> patient = new LinkedHashMap<>();
        patient.put("name", "张三");
        patient.put("bedNo", "12床");
        patient.put("inpatientNo", "2025000123");
        patient.put("deptName", "神经内一科");
        root.put("patient", patient);
        Map<String, Object> bill = new LinkedHashMap<>();
        bill.put("billDate", LocalDate.now().toString());
        bill.put("admissionDays", "16");
        root.put("bill", bill);
        root.put("charge", Map.of("lines", sampleLines()));
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("dailySubtotal", "12.50");
        summary.put("cumulativeTotal", "130.72");
        summary.put("prepayAmount", "500.00");
        summary.put("balance", "369.28");
        root.put("summary", summary);
        root.put("system", Map.of("printTime", TIME_FMT.format(LocalDateTime.now())));
        return root;
    }

    private static List<Map<String, Object>> sampleLines() {
        List<Map<String, Object>> lines = new ArrayList<>();
        lines.add(line("床位费", "1", "50.00", "50.00", "诊疗"));
        lines.add(line("氯化钠注射液", "2", "1.25", "2.50", "药品"));
        return lines;
    }

    private static Map<String, Object> line(
            String itemName, String quantity, String unitPrice, String amount, String category) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("itemName", itemName);
        row.put("quantity", quantity);
        row.put("unitPrice", unitPrice);
        row.put("amount", amount);
        row.put("category", category);
        return row;
    }
}
