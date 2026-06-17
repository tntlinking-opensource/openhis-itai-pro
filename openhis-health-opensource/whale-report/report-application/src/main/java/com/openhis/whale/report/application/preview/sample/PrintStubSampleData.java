package com.openhis.whale.report.application.preview.sample;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 出厂 stub 元素包（{@code print_page_stub} / {@code print_roll_stub} / {@code print_page_overlay_stub}）预览样例。
 */
public final class PrintStubSampleData {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private PrintStubSampleData() {
    }

    public static Map<String, Object> buildPageStub() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("hospital", Map.of("name", "示例人民医院"));
        root.put("system", Map.of("printTime", TIME_FMT.format(LocalDateTime.now())));
        return root;
    }

    public static Map<String, Object> buildRollStub() {
        return buildPageStub();
    }

    public static Map<String, Object> buildOverlayStub() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("hospital", Map.of("name", "示例人民医院"));
        Map<String, Object> patient = new LinkedHashMap<>();
        patient.put("name", "张三");
        patient.put("gender", "男");
        patient.put("age", "35岁");
        patient.put("busNo", "P20260530001");
        root.put("patient", patient);
        root.put("system", Map.of("printTime", TIME_FMT.format(LocalDateTime.now())));
        return root;
    }
}
