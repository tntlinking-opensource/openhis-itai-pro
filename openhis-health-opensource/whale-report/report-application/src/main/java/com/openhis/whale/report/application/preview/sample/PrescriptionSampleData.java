package com.openhis.whale.report.application.preview.sample;

import com.openhis.whale.report.contracts.prescription.PrescriptionDrugLineGroupSupport;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 门诊处方元素包（{@code outpatient_prescription}）预览样例，对齐 bindPath 与 Port 输出结构。
 * <p>flow / overlay 模板共用；overlay 套打预览与设计器试打均使用本数据集。</p>
 */
public final class PrescriptionSampleData {

    private static final DateTimeFormatter AUTHORED_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Long SAMPLE_INFUSION_GROUP_ID = 1001L;

    private PrescriptionSampleData() {
    }

    public static Map<String, Object> build() {
        String now = TIME_FMT.format(LocalDateTime.now());
        String authored = AUTHORED_FMT.format(LocalDateTime.now());

        Map<String, Object> root = new LinkedHashMap<>();

        Map<String, Object> hospital = new LinkedHashMap<>();
        hospital.put("name", "同一医院");
        hospital.put("code", "H-DEMO-001");
        root.put("hospital", hospital);

        Map<String, Object> patient = new LinkedHashMap<>();
        patient.put("name", "李思雨");
        patient.put("gender", "女性");
        patient.put("age", "22岁");
        patient.put("busNo", "M2606110009175777");
        patient.put("idCard", "000000200309231357");
        patient.put("phone", "13844178165");
        root.put("patient", patient);

        Map<String, Object> encounter = new LinkedHashMap<>();
        encounter.put("busNo", "M2606110009175777");
        encounter.put("deptName", "皮外科");
        encounter.put("classText", "普通门诊");
        encounter.put("payTypeText", "自费");
        encounter.put("startTime", authored);
        root.put("encounter", encounter);

        Map<String, Object> prescription = new LinkedHashMap<>();
        prescription.put("no", "PCN00001176");
        prescription.put("rxTypeText", "普通处方");
        prescription.put("authoredDate", "2026-06-01");
        prescription.put("deptName", "皮外科");
        prescription.put("diagnosis", "手癣");
        prescription.put("diagnosisIcd", "B35.2");
        prescription.put("totalAmount", "4.75");
        prescription.put("lines", sampleLines());
        root.put("prescription", prescription);

        root.put("practitioner", Map.of(
                "prescriberName", "晏万刚",
                "reviewerName", "何丽霞",
                "pharmacistName", "何丽霞",
                "dispenserName", "何丽霞",
                "checkerName", "孙涛",
                "issuerName", "孙涛"));

        root.put("footer", Map.of(
                "prescriberName", "晏万刚",
                "reviewerName", "何丽霞",
                "totalAmount", "4.75"));

        root.put("system", Map.of("printTime", now));
        return root;
    }

    private static List<Map<String, Object>> sampleLines() {
        List<Map<String, Object>> raw = new ArrayList<>();
        raw.add(PrescriptionDrugLineGroupSupport.baseLine(
                1,
                SAMPLE_INFUSION_GROUP_ID,
                "0.9%氯化钠注射液",
                "250ml:2.25g",
                "1",
                "瓶",
                "静滴 qd",
                "静滴",
                "qd",
                ""));
        raw.add(PrescriptionDrugLineGroupSupport.baseLine(
                2,
                SAMPLE_INFUSION_GROUP_ID,
                "注射用头孢呋辛钠",
                "0.75g",
                "1",
                "支",
                "静滴 qd",
                "静滴",
                "qd",
                ""));
        raw.add(PrescriptionDrugLineGroupSupport.baseLine(
                3,
                null,
                "联苯苄唑乳膏",
                "15g:0.15g",
                "1",
                "支",
                "外用 每日两次",
                "外用",
                "bid",
                ""));
        return PrescriptionDrugLineGroupSupport.applyGrouping(raw);
    }
}
