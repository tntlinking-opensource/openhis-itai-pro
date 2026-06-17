package com.openhis.whale.report.application.preview.sample;

import java.util.LinkedHashMap;
import java.util.Map;

public final class RegistrationReceiptSampleData {

    private RegistrationReceiptSampleData() {
    }

    public static Map<String, Object> build() {
        Map<String, Object> root = new LinkedHashMap<>(ChargeReceiptSampleData.build());
        root.put("registration", Map.of(
                "departmentName", "内科门诊",
                "doctorName", "李医生",
                "serviceTypeName", "普通号",
                "queueNo", "15",
                "registerTime", "2026-06-05 09:30:00",
                "encounterBusNo", "E20260605001"));
        return root;
    }
}
