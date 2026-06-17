package com.openhis.whale.report.application.preview.sample;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LabApplyFormSampleData {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private LabApplyFormSampleData() {
    }

    public static Map<String, Object> build() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("hospital", Map.of("name", "示例人民医院"));
        root.put("patient", Map.of(
                "name", "张三",
                "gender", "男",
                "age", "45岁",
                "bedNo", "内一病区 12床",
                "deptName", "内一科"));
        Map<String, Object> order = new LinkedHashMap<>();
        order.put("sheetNo", "ZY20260604001");
        order.put("prescriberName", "李医生");
        order.put("authoredDate", LocalDate.now().toString());
        order.put("lines", sampleLines());
        root.put("order", order);
        root.put("system", Map.of("printTime", TIME_FMT.format(LocalDateTime.now())));
        return root;
    }

    private static List<Map<String, Object>> sampleLines() {
        List<Map<String, Object>> lines = new ArrayList<>();
        lines.add(line("血常规", "—", "st", "空腹"));
        lines.add(line("肝功能", "—", "st", ""));
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
