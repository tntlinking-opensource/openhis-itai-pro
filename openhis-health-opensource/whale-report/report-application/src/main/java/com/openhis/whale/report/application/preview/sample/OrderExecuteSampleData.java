package com.openhis.whale.report.application.preview.sample;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class OrderExecuteSampleData {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private OrderExecuteSampleData() {
    }

    public static Map<String, Object> build() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("hospital", Map.of("name", "示例人民医院"));
        Map<String, Object> patient = new LinkedHashMap<>();
        patient.put("name", "张三");
        patient.put("gender", "男");
        patient.put("age", "45岁");
        patient.put("encounterBusNo", "ZY20260604001");
        patient.put("wardBed", "内一病区 12床");
        patient.put("deptName", "内一科");
        root.put("patient", patient);
        Map<String, Object> sheet = new LinkedHashMap<>();
        sheet.put("executeTabText", "待执行");
        sheet.put("therapyTypeText", "全部");
        sheet.put("executionDeadline", TIME_FMT.format(LocalDateTime.now()));
        root.put("sheet", sheet);
        Map<String, Object> order = new LinkedHashMap<>();
        order.put("lines", sampleLines());
        root.put("order", order);
        root.put("system", Map.of("printTime", TIME_FMT.format(LocalDateTime.now())));
        return root;
    }

    private static List<Map<String, Object>> sampleLines() {
        List<Map<String, Object>> lines = new ArrayList<>();
        lines.add(line("长期/药品", "0.9%氯化钠注射液【250ml】", "250ml", "静滴，qd", "2026-06-01\n—", "06-04 08:00 06-04 16:00", "李医生"));
        lines.add(line("临时/诊疗", "静脉采血", "1次", "—", "2026-06-04\n—", "06-04 06:00", "王医生"));
        return lines;
    }

    private static Map<String, Object> line(
            String typeLabel,
            String content,
            String dosage,
            String usageFreq,
            String startStop,
            String planTimes,
            String prescriberName) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("typeLabel", typeLabel);
        row.put("content", content);
        row.put("dosage", dosage);
        row.put("usageFreq", usageFreq);
        row.put("startStop", startStop);
        row.put("planTimes", planTimes);
        row.put("prescriberName", prescriberName);
        return row;
    }
}
