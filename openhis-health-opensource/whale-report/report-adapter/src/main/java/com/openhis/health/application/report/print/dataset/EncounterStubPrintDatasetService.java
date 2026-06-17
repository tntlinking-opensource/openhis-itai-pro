/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.report.print.dataset;
import com.openhis.health.application.report.support.ReportDatasetTextSupport;

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
 * 出厂 stub 元素包打印数据集（{@code print_page_stub} / {@code print_roll_stub}）。
 */
@Service
@RequiredArgsConstructor
public class EncounterStubPrintDatasetService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final EncounterContextFacade encounterContextFacade;
    private final OrganizationQueryService organizationQueryService;    /**
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
        String docTitle = extractString(params, "docTitle", "doc_title", "templateCode", "template_code");

        return encounterContextFacade.getInpatientContextByEncounterId(encounterId)
                .map(ctx -> toDataset(ctx.getOrganizationId(), ctx.getPatientName(), ctx.getGenderEnum(),
                        ctx.getAgeText(), ctx.getPatientId(), ctx.getEncounterBusNo(),
                        buildWardBed(ctx.getWardName(), ctx.getBedName()), ctx.getOrganizationName(), docTitle))
                .orElseGet(() -> encounterContextFacade.getOutpatientContextByEncounterId(encounterId)
                        .map(ctx -> {
                            String bedLabel = "—";
                            if (ctx.getDisplayOrder() != null) {
                                bedLabel = String.valueOf(ctx.getDisplayOrder());
                            }
                            return toDataset(ctx.getOrganizationId(), ctx.getPatientName(), ctx.getGenderEnum(),
                                    ctx.getAgeText(), ctx.getPatientId(), ctx.getEncounterBusNo(),
                                    bedLabel, ctx.getOrganizationName(), docTitle);
                        })
                        .orElse(Map.of()));
    }

    private Map<String, Object> toDataset(
            Long organizationId,
            String patientName,
            String genderEnum,
            String ageText,
            Long patientId,
            String encounterBusNo,
            String bedNo,
            String deptName,
            String docTitle) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("hospital", Map.of("name", resolveHospitalDisplayName(organizationId)));

        Map<String, Object> patient = new LinkedHashMap<>();
        patient.put("name", nullToDash(patientName));
        patient.put("gender", toGenderText(genderEnum));
        patient.put("age", nullToEmpty(ageText));
        String mrn = nullToEmpty(encounterBusNo);
        if (patientId != null) {
            mrn = String.valueOf(patientId);
        }
        patient.put("mrn", mrn);
        patient.put("bedNo", nullToEmpty(bedNo));
        patient.put("deptName", nullToEmpty(deptName));
        root.put("patient", patient);

        if (StringUtils.isNotBlank(docTitle)) {
            root.put("doc", Map.of("title", docTitle.trim()));
        }
        root.put("system", Map.of("printTime", TIME_FMT.format(LocalDateTime.now())));
        return root;
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

    private static String buildWardBed(String wardName, String bedName) {
        String ward = nullToEmpty(wardName);
        String bed = nullToEmpty(bedName);
        if (ward.isEmpty() && bed.isEmpty()) {
            return "—";
        }
        if (ward.isEmpty()) {
            return bed;
        }
        if (bed.isEmpty()) {
            return ward;
        }
        return ward + " " + bed;
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
                    // try next
                }
            }
        }
        return null;
    }

    private static String extractString(Map<String, Object> params, String... keys) {
        for (String key : keys) {
            Object value = params.get(key);
            if (value instanceof String text && StringUtils.isNotBlank(text)) {
                return text.trim();
            }
        }
        return "";
    }
}
