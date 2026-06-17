package com.openhis.whale.report.application.preview.sample;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

public final class WristbandSampleData {

    private WristbandSampleData() {
    }

    public static Map<String, Object> build() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("patient", Map.of(
                "name", "张三",
                "gender", "男",
                "age", "35岁",
                "mrn", "P20260604001",
                "wristbandBarcode", "WB20260604001"));
        root.put("encounter", Map.of(
                "deptBed", "内一科 12床",
                "admissionDate", LocalDate.now().toString()));
        return root;
    }
}
