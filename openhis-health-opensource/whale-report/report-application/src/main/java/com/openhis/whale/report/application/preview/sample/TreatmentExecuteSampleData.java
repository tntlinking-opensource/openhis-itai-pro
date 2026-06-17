package com.openhis.whale.report.application.preview.sample;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TreatmentExecuteSampleData {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private TreatmentExecuteSampleData() {
    }

    public static Map<String, Object> build() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("hospital", Map.of("name", "示例人民医院"));
        Map<String, Object> patient = new LinkedHashMap<>();
        patient.put("name", "张三");
        patient.put("gender", "男");
        patient.put("age", "35岁");
        patient.put("bedNo", "12");
        patient.put("deptName", "内科门诊");
        root.put("patient", patient);
        Map<String, Object> order = new LinkedHashMap<>();
        order.put("sheetNo", "E20260604001");
        order.put("prescriberName", "李医生");
        order.put("authoredDate", LocalDate.now().toString());
        order.put("lines", sampleLines());
        root.put("order", order);
        root.put("execution", Map.of(
                "nurseName", "",
                "executeTime", TIME_FMT.format(LocalDateTime.now())));
        return root;
    }

    private static List<Map<String, Object>> sampleLines() {
        List<Map<String, Object>> lines = new ArrayList<>();
        lines.add(line("雾化吸入", "—", "qd", "遵医嘱"));
        lines.add(line("换药", "左足", "st", ""));
        return lines;
    }

    private static Map<String, Object> line(String itemName, String bodySite, String frequency, String remark) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("itemName", itemName);
        row.put("bodySite", bodySite);
        row.put("frequency", frequency);
        row.put("remark", remark);
        return row;
    }
}
