package com.openhis.whale.report.application.preview.sample;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

public final class InpatientCertificateSampleData {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private InpatientCertificateSampleData() {
    }

    public static Map<String, Object> build() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("hospital", Map.of("name", "示例人民医院"));
        root.put("patient", Map.of(
                "name", "张三",
                "gender", "男",
                "age", "45岁",
                "encounterBusNo", "ZY20260604001"));
        Map<String, Object> encounter = new LinkedHashMap<>();
        encounter.put("deptName", "内一科");
        encounter.put("wardName", "内一病区");
        encounter.put("bedName", "12床");
        encounter.put("attendingDoctorName", "李医生");
        encounter.put("primaryNurseName", "王护士");
        encounter.put("admissionTime", TIME_FMT.format(LocalDateTime.now().minusDays(3)));
        encounter.put("dischargeTime", "—");
        encounter.put("diagnosisSummary", "社区获得性肺炎");
        encounter.put("statusText", "在院");
        root.put("encounter", encounter);
        root.put("system", Map.of("printTime", TIME_FMT.format(LocalDateTime.now())));
        return root;
    }
}
