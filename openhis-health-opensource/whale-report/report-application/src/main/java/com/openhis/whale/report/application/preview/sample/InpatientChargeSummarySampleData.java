package com.openhis.whale.report.application.preview.sample;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class InpatientChargeSummarySampleData {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private InpatientChargeSummarySampleData() {
    }

    public static Map<String, Object> build() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("hospital", Map.of("name", "示例人民医院"));
        Map<String, Object> patient = new LinkedHashMap<>();
        patient.put("name", "张三");
        patient.put("bedNo", "12床");
        patient.put("inpatientNo", "ZY20260604001");
        patient.put("deptName", "内一科");
        root.put("patient", patient);
        Map<String, Object> settle = new LinkedHashMap<>();
        settle.put("settleTime", TIME_FMT.format(LocalDateTime.now()));
        settle.put("operatorName", "收费员A");
        root.put("settle", settle);
        Map<String, Object> charge = new LinkedHashMap<>();
        charge.put("lines", sampleLines());
        root.put("charge", charge);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalAmount", "5680.50");
        summary.put("prepayAmount", "8000.00");
        summary.put("balanceHint", "2319.50");
        root.put("summary", summary);
        root.put("system", Map.of("printTime", TIME_FMT.format(LocalDateTime.now())));
        return root;
    }

    private static List<Map<String, Object>> sampleLines() {
        List<Map<String, Object>> lines = new ArrayList<>();
        lines.add(line("床位费", "—", "1 天", "50.00", "50.00", "床位", "2026-06-01 08:00"));
        lines.add(line("阿莫西林胶囊", "0.25g×24粒", "2 盒", "12.50", "25.00", "药品", "2026-06-02 09:30"));
        lines.add(line("血常规", "—", "1 次", "28.00", "28.00", "检验", "2026-06-03 07:00"));
        return lines;
    }

    private static Map<String, Object> line(
            String itemName,
            String spec,
            String quantity,
            String unitPrice,
            String amount,
            String category,
            String occurrenceTime) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("itemName", itemName);
        row.put("spec", spec);
        row.put("quantity", quantity);
        row.put("unitPrice", unitPrice);
        row.put("amount", amount);
        row.put("category", category);
        row.put("occurrenceTime", occurrenceTime);
        return row;
    }
}
