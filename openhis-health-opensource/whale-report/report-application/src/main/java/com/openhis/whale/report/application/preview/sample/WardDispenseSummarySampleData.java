package com.openhis.whale.report.application.preview.sample;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class WardDispenseSummarySampleData {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private WardDispenseSummarySampleData() {
    }

    public static Map<String, Object> build() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("hospital", Map.of("name", "示例人民医院"));
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("summaryNo", "MSY202606050001");
        summary.put("applyTime", "2026-06-05 08:00:00");
        summary.put("pharmacyName", "住院西药房");
        summary.put("patientCount", "2");
        summary.put("lineCount", "3");
        summary.put("lines", sampleLines());
        root.put("summary", summary);
        root.put("system", Map.of("printTime", TIME_FMT.format(LocalDateTime.now())));
        return root;
    }

    private static List<Map<String, Object>> sampleLines() {
        List<Map<String, Object>> lines = new ArrayList<>();
        lines.add(line("01", "张三", "H202606001", "0.9%氯化钠注射液", "2", "袋", "长期", "住院西药房"));
        lines.add(line("01", "张三", "H202606001", "维生素C注射液", "1", "支", "临时", "住院西药房"));
        lines.add(line("03", "李四", "H202606002", "阿莫西林胶囊", "1", "盒", "长期", "住院西药房"));
        return lines;
    }

    private static Map<String, Object> line(
            String bedNo,
            String patientName,
            String encounterBusNo,
            String drugName,
            String quantity,
            String unitText,
            String orderClass,
            String pharmacyName) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("bedNo", bedNo);
        row.put("patientName", patientName);
        row.put("encounterBusNo", encounterBusNo);
        row.put("drugName", drugName);
        row.put("quantity", quantity);
        row.put("unitText", unitText);
        row.put("orderClass", orderClass);
        row.put("pharmacyName", pharmacyName);
        return row;
    }
}
