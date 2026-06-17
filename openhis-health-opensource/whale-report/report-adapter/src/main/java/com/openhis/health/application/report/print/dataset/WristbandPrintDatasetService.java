/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.report.print.dataset;
import com.openhis.health.application.report.support.ReportDatasetTextSupport;

import com.openhis.health.module.querysupport.encounter.EncounterContextFacade;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.administration.encounter.queries.model.OutpatientEncounterContextQueryModel;
import com.openhis.health.domain.share.enums.AdministrativeGender;
import com.openhis.health.domain.share.utils.EnumUtil;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 患者腕带打印数据集（对齐 {@code patient_wristband} 元素包 bindPath）。
 */
@Service
@RequiredArgsConstructor
public class WristbandPrintDatasetService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final EncounterContextFacade encounterContextFacade;    /**
     * 组装打印数据集 root Map（供 Port / 预览 bindPath 绑定）。
     *
     * @param params 业务参数（如 encounterId、paymentId、dateFrom 等）
     * @return 数据集 root；必填参数缺失时返回空 Map
     */

    public Map<String, Object> build(Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return Map.of();
        }
        Long encounterId = extractLong(params, "encounterId", "encounter_id");
        if (encounterId == null) {
            return Map.of();
        }

        return encounterContextFacade.getInpatientContextByEncounterId(encounterId)
                .map(this::toDatasetFromInpatient)
                .orElseGet(() -> encounterContextFacade.getOutpatientContextByEncounterId(encounterId)
                        .map(ctx -> toDatasetFromOutpatient(encounterId, ctx))
                        .orElse(Map.of()));
    }

    private Map<String, Object> toDatasetFromInpatient(InpatientEncounterContextQueryModel encounter) {
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> patient = new LinkedHashMap<>();
        patient.put("name", nullToDash(encounter.getPatientName()));
        patient.put("gender", toGenderText(encounter.getGenderEnum()));
        patient.put("age", nullToEmpty(encounter.getAgeText()));
        patient.put("mrn", resolveMrn(encounter.getPatientId(), encounter.getEncounterBusNo()));
        patient.put("wristbandBarcode", buildWristbandBarcode(encounter.getEncounterId(), encounter.getEncounterBusNo()));
        root.put("patient", patient);

        Map<String, Object> encounterMap = new LinkedHashMap<>();
        encounterMap.put("deptBed", buildDeptBed(
                encounter.getWardName(),
                encounter.getOrganizationName(),
                encounter.getBedName()));
        encounterMap.put("admissionDate", formatDate(encounter.getStartTime()));
        root.put("encounter", encounterMap);
        return root;
    }

    private Map<String, Object> toDatasetFromOutpatient(
            Long encounterId,
            OutpatientEncounterContextQueryModel encounter) {
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> patient = new LinkedHashMap<>();
        patient.put("name", nullToDash(encounter.getPatientName()));
        patient.put("gender", toGenderText(encounter.getGenderEnum()));
        patient.put("age", nullToEmpty(encounter.getAgeText()));
        patient.put("mrn", resolveMrn(encounter.getPatientId(), encounter.getEncounterBusNo()));
        patient.put("wristbandBarcode", buildWristbandBarcode(encounterId, encounter.getEncounterBusNo()));
        root.put("patient", patient);

        Map<String, Object> encounterMap = new LinkedHashMap<>();
        String deptBed = nullToEmpty(encounter.getOrganizationName());
        if (encounter.getDisplayOrder() != null) {
            deptBed = joinParts(deptBed, String.valueOf(encounter.getDisplayOrder()));
        }
        encounterMap.put("deptBed", ReportDatasetTextSupport.deptBedOrDash(deptBed));
        encounterMap.put("admissionDate", formatDate(encounter.getStartTime()));
        root.put("encounter", encounterMap);
        return root;
    }

    private static String buildDeptBed(String wardName, String organizationName, String bedName) {
        List<String> parts = new ArrayList<>();
        if (StringUtils.isNotBlank(wardName)) {
            parts.add(wardName.trim());
        } else if (StringUtils.isNotBlank(organizationName)) {
            parts.add(organizationName.trim());
        }
        if (StringUtils.isNotBlank(bedName)) {
            parts.add(bedName.trim());
        }
        if (parts.isEmpty()) {
            return "—";
        }
        return String.join(" ", parts);
    }

    private static String buildWristbandBarcode(Long encounterId, String encounterBusNo) {
        if (StringUtils.isNotBlank(encounterBusNo)) {
            return encounterBusNo.trim();
        }
        if (encounterId != null) {
            return "WB" + encounterId;
        }
        return "";
    }

    private static String resolveMrn(Long patientId, String encounterBusNo) {
        if (patientId != null) {
            return String.valueOf(patientId);
        }
        return nullToEmpty(encounterBusNo);
    }

    private static String joinParts(String left, String right) {
        if (StringUtils.isBlank(left)) {
            return right;
        }
        if (StringUtils.isBlank(right)) {
            return left;
        }
        return left.trim() + " " + right.trim();
    }

    private static String formatDate(LocalDateTime time) {
        if (time == null) {
            return "";
        }
        return time.format(DATE_FMT);
    }

    private static String toGenderText(String genderEnum) {
        AdministrativeGender g = EnumUtil.fromCode(AdministrativeGender.class, genderEnum);
        if (g != null) {
            return g.getDisplay();
        }
        return ReportDatasetTextSupport.genderCodeOrEmpty(genderEnum);
    }

    private static String nullToDash(String value) {
        return ReportDatasetTextSupport.trimToDash(value);
    }

    private static String nullToEmpty(String value) {
        return ReportDatasetTextSupport.trimToEmpty(value);
    }

    private static Long extractLong(Map<String, Object> params, String... keys) {
        for (String key : keys) {
            Object value = params.get(key);
            if (value instanceof Number number) {
                return number.longValue();
            }
            if (value instanceof String text && StringUtils.isNotBlank(text)) {
                try {
                    return Long.parseLong(text.trim());
                } catch (NumberFormatException ignored) {
                    // try next key
                }
            }
        }
        return null;
    }
}
