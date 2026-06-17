/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.report.print.dataset;
import com.openhis.health.application.report.support.ReportDatasetTextSupport;

import com.openhis.health.module.common.assembler.inpatient.AdmissionPrimaryDiagnosisAssembler;
import com.openhis.health.module.querysupport.encounter.EncounterContextFacade;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.administration.encounter.queries.model.OutpatientEncounterContextQueryModel;
import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.share.enums.AdministrativeGender;
import com.openhis.health.domain.share.enums.OrganizationType;
import com.openhis.health.domain.share.utils.EnumUtil;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 入院证 / 出院证打印数据集。
 */
@Service
@RequiredArgsConstructor
public class InpatientCertificatePrintDatasetService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final EncounterContextFacade encounterContextFacade;
    private final OrganizationQueryService organizationQueryService;
    private final AdmissionPrimaryDiagnosisAssembler admissionPrimaryDiagnosisAssembler;    /**
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
        String certificateTypeRaw = extractString(params, "certificateType", "certificate_type");
        final String certificateType = StringUtils.isBlank(certificateTypeRaw)
                ? "admission"
                : certificateTypeRaw.trim().toLowerCase();

        return encounterContextFacade.getInpatientContextByEncounterId(encounterId)
                .map(ctx -> toDataset(ctx, certificateType))
                .orElseGet(() -> encounterContextFacade.getOutpatientContextByEncounterId(encounterId)
                        .map(ctx -> toDatasetFromOutpatient(encounterId, ctx, certificateType))
                        .orElse(Map.of()));
    }

    private Map<String, Object> toDataset(InpatientEncounterContextQueryModel encounter, String certificateType) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("hospital", Map.of("name", resolveHospitalDisplayName(encounter.getOrganizationId())));

        Map<String, Object> patient = new LinkedHashMap<>();
        patient.put("name", nullToDash(encounter.getPatientName()));
        patient.put("gender", toGenderText(encounter.getGenderEnum()));
        patient.put("age", nullToEmpty(encounter.getAgeText()));
        patient.put("encounterBusNo", nullToEmpty(encounter.getEncounterBusNo()));
        root.put("patient", patient);

        Map<String, Object> encounterMap = new LinkedHashMap<>();
        encounterMap.put("deptName", nullToEmpty(encounter.getOrganizationName()));
        encounterMap.put("wardName", nullToEmpty(encounter.getWardName()));
        encounterMap.put("bedName", nullToEmpty(encounter.getBedName()));
        encounterMap.put("attendingDoctorName", nullToEmpty(encounter.getAttendingDoctorName()));
        encounterMap.put("primaryNurseName", nullToEmpty(encounter.getPrimaryNurseName()));
        encounterMap.put("admissionTime", formatDateTime(encounter.getStartTime()));
        encounterMap.put("dischargeTime", formatDateTime(encounter.getEndTime()));
        encounterMap.put("inWayCode", nullToEmpty(encounter.getInWayCode()));
        encounterMap.put("outWayCode", nullToEmpty(encounter.getOutWayCode()));
        encounterMap.put("statusText", nullToEmpty(encounter.getStatusEnumText()));
        encounterMap.put("diagnosisSummary", resolveDiagnosisSummary(encounter.getDiagnosisJson()));
        encounterMap.put("certificateType", certificateType);
        root.put("encounter", encounterMap);

        root.put("system", Map.of("printTime", TIME_FMT.format(LocalDateTime.now())));
        return root;
    }

    private Map<String, Object> toDatasetFromOutpatient(
            Long encounterId,
            OutpatientEncounterContextQueryModel encounter,
            String certificateType) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("hospital", Map.of("name", resolveHospitalDisplayName(encounter.getOrganizationId())));

        Map<String, Object> patient = new LinkedHashMap<>();
        patient.put("name", nullToDash(encounter.getPatientName()));
        patient.put("gender", toGenderText(encounter.getGenderEnum()));
        patient.put("age", nullToEmpty(encounter.getAgeText()));
        patient.put("encounterBusNo", nullToEmpty(encounter.getEncounterBusNo()));
        root.put("patient", patient);

        Map<String, Object> encounterMap = new LinkedHashMap<>();
        encounterMap.put("deptName", nullToEmpty(encounter.getOrganizationName()));
        encounterMap.put("wardName", "");
        String bedName = "";
        if (encounter.getDisplayOrder() != null) {
            bedName = String.valueOf(encounter.getDisplayOrder());
        }
        encounterMap.put("bedName", bedName);
        encounterMap.put("attendingDoctorName", nullToEmpty(encounter.getDoctorName()));
        encounterMap.put("primaryNurseName", "");
        encounterMap.put("admissionTime", formatDateTime(encounter.getStartTime()));
        encounterMap.put("dischargeTime", formatDateTime(encounter.getEndTime()));
        encounterMap.put("inWayCode", "");
        encounterMap.put("outWayCode", "");
        encounterMap.put("statusText", nullToEmpty(encounter.getStatusEnumText()));
        encounterMap.put("diagnosisSummary", resolveDiagnosisSummary(encounter.getDiagnosisJson()));
        encounterMap.put("certificateType", certificateType);
        root.put("encounter", encounterMap);

        root.put("system", Map.of("printTime", TIME_FMT.format(LocalDateTime.now())));
        return root;
    }

    private String resolveDiagnosisSummary(String diagnosisJson) {
        if (StringUtils.isBlank(diagnosisJson)) {
            return "—";
        }
        String summary = admissionPrimaryDiagnosisAssembler.resolveDiagnosisSummary(diagnosisJson.trim());
        return ReportDatasetTextSupport.trimToDash(summary);
    }

    private String resolveHospitalDisplayName(Long organizationId) {
        if (organizationId == null) {
            return "—";
        }
        Long orgId = organizationId;
        for (int depth = 0; depth < 12; depth++) {
            Organization org = organizationQueryService.loadOrganizationByIdForDisplay(orgId);
            if (org == null) {
                break;
            }
            if (OrganizationType.HOSPITAL.getCode().equals(org.getTypeEnum())
                    && StringUtils.isNotBlank(org.getName())) {
                return org.getName().trim();
            }
            if (org.getParentId() == null) {
                if (StringUtils.isNotBlank(org.getName())) {
                    return org.getName().trim();
                }
                break;
            }
            orgId = org.getParentId();
        }
        return "—";
    }

    private static String formatDateTime(LocalDateTime time) {
        if (time == null) {
            return "—";
        }
        return TIME_FMT.format(time);
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

    private static String extractString(Map<String, Object> params, String... keys) {
        if (params == null) {
            return "";
        }
        for (String key : keys) {
            Object value = params.get(key);
            if (value instanceof String text && StringUtils.isNotBlank(text)) {
                return text.trim();
            }
        }
        return "";
    }
}
