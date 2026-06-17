package com.openhis.whale.report.application.preview.sample;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SpecimenBarcodeSampleData {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private SpecimenBarcodeSampleData() {
    }

    public static Map<String, Object> build() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("patient", Map.of(
                "name", "张三",
                "mrn", "P20260604001"));
        root.put("specimen", Map.of(
                "typeName", "静脉血",
                "tubeColor", "紫管(EDTA)",
                "barcode", "SP20260604001R1001",
                "collectTime", TIME_FMT.format(LocalDateTime.now())));
        root.put("order", Map.of("itemName", "血常规"));
        return root;
    }
}
