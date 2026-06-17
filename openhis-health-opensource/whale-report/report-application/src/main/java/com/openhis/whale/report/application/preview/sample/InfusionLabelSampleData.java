package com.openhis.whale.report.application.preview.sample;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class InfusionLabelSampleData {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private InfusionLabelSampleData() {
    }

    public static Map<String, Object> build() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("patient", Map.of(
                "name", "张三",
                "bedNo", "—",
                "mrn", "P20260604001"));
        root.put("encounter", Map.of("busNo", "E20260604001", "slipNo", "20260604001"));
        Map<String, Object> infusion = new LinkedHashMap<>();
        infusion.put("groupName", "输液组 1");
        infusion.put("drugs", sampleDrugs());
        root.put("infusion", infusion);
        root.put("order", Map.of(
                "route", "静滴",
                "frequency", "qd",
                "routeFrequency", "静滴 qd",
                "planTime", TIME_FMT.format(LocalDateTime.now()),
                "executeBarcode", "IV-DEMO-001"));
        return root;
    }

    private static List<Map<String, Object>> sampleDrugs() {
        List<Map<String, Object>> drugs = new ArrayList<>();
        drugs.add(drugLine("0.9%氯化钠注射液", "250ml", "1", "袋", "250ml×1"));
        drugs.add(drugLine("头孢呋辛钠", "0.75g", "1", "支", "0.75g×1"));
        return drugs;
    }

    private static Map<String, Object> drugLine(
            String itemName,
            String specText,
            String quantity,
            String unitText,
            String drugText) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("itemName", itemName);
        row.put("specText", specText);
        row.put("quantity", quantity);
        row.put("unitText", unitText);
        row.put("drugText", drugText);
        return row;
    }
}
