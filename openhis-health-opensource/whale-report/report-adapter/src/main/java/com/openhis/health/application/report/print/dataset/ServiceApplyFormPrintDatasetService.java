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
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.share.enums.AdministrativeGender;
import com.openhis.health.domain.share.enums.OrganizationType;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.workflow.activitydefinition.entity.ActivityDefinition;
import com.openhis.health.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 检验/检查申请单打印数据集（对齐 {@code lab_apply_form} / {@code exam_apply_form}）。
 */
@Service
@RequiredArgsConstructor
public class ServiceApplyFormPrintDatasetService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final ServiceRequestQueryService serviceRequestQueryService;
    private final ActivityDefinitionQueryService activityDefinitionQueryService;
    private final EncounterContextFacade encounterContextFacade;
    private final OrganizationQueryService organizationQueryService;
    private final PractitionerQueryService practitionerQueryService;    /**
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
        String applyScope = extractString(params, "applyScope", "apply_scope");
        if (StringUtils.isBlank(applyScope)) {
            applyScope = "lab";
        }
        List<Long> serviceRequestIds = extractLongList(params, "serviceRequestIds", "service_request_ids");

        List<ServiceRequestListModel> all =
                serviceRequestQueryService.listServiceRequestsByEncounter(encounterId);
        if (all == null || all.isEmpty()) {
            return Map.of();
        }

        List<ServiceRequestListModel> forward = all.stream()
                .filter(this::isForwardServiceRow)
                .sorted(Comparator.comparing(
                        ServiceRequestListModel::getSortNumber,
                        Comparator.nullsLast(Integer::compareTo)))
                .toList();
        Set<Long> forwardActivityIds = forward.stream()
                .map(ServiceRequestListModel::getActivityId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, ActivityDefinition> activityMap = activityDefinitionQueryService.findAllByIds(forwardActivityIds)
                .stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(ActivityDefinition::getId, item -> item, (a, b) -> a, LinkedHashMap::new));

        List<ServiceRequestListModel> rows = resolveTargetRows(forward, serviceRequestIds, activityMap, applyScope);
        if (rows.isEmpty()) {
            return Map.of();
        }

        InpatientEncounterContextQueryModel inpatient = encounterContextFacade
                .getInpatientContextByEncounterId(encounterId)
                .orElse(null);
        if (inpatient != null) {
            return toDataset(inpatient, rows, activityMap);
        }
        OutpatientEncounterContextQueryModel outpatient = encounterContextFacade
                .getOutpatientContextByEncounterId(encounterId)
                .orElse(null);
        if (outpatient == null) {
            return Map.of();
        }
        return toDataset(outpatient, rows, activityMap);
    }

    private List<ServiceRequestListModel> resolveTargetRows(
            List<ServiceRequestListModel> forward,
            List<Long> serviceRequestIds,
            Map<Long, ActivityDefinition> activityMap,
            String applyScope) {
        List<ServiceRequestListModel> scoped = forward.stream()
                .filter(row -> matchesApplyScope(row, activityMap.get(row.getActivityId()), applyScope))
                .toList();
        if (!serviceRequestIds.isEmpty()) {
            Set<Long> idSet = new LinkedHashSet<>(serviceRequestIds);
            return scoped.stream()
                    .filter(row -> row.getId() != null && idSet.contains(row.getId()))
                    .toList();
        }
        return scoped;
    }

    private boolean matchesApplyScope(
            ServiceRequestListModel row,
            ActivityDefinition activity,
            String applyScope) {
        String itemName = resolveItemName(activity);
        if ("lab".equalsIgnoreCase(applyScope)) {
            return SpecimenBarcodePrintDatasetService.isLabSpecimenService(activity, itemName);
        }
        if ("exam".equalsIgnoreCase(applyScope)) {
            return isExamService(activity, itemName);
        }
        if ("procedure".equalsIgnoreCase(applyScope)) {
            return isProcedureService(activity, itemName);
        }
        return true;
    }

    static boolean isProcedureService(ActivityDefinition activity, String itemName) {
        if (SpecimenBarcodePrintDatasetService.isLabSpecimenService(activity, itemName)) {
            return false;
        }
        if (isExamService(activity, itemName)) {
            return false;
        }
        String name = nullToEmpty(itemName).toLowerCase();
        if (name.contains("处置") || name.contains("治疗") || name.contains("理疗") || name.contains("康复")) {
            return true;
        }
        if (activity != null) {
            String categoryCode = nullToEmpty(activity.getCategoryCode());
            if ("3".equals(categoryCode) || "4".equals(categoryCode) || "5".equals(categoryCode)) {
                return true;
            }
            String activityName = nullToEmpty(activity.getName()).toLowerCase();
            if (activityName.contains("处置") || activityName.contains("治疗")) {
                return true;
            }
        }
        return StringUtils.isNotBlank(itemName);
    }

    static boolean isExamService(ActivityDefinition activity, String itemName) {
        if (SpecimenBarcodePrintDatasetService.isLabSpecimenService(activity, itemName)) {
            return false;
        }
        String name = nullToEmpty(itemName).toLowerCase();
        if (containsExamKeyword(name)) {
            return true;
        }
        if (activity != null) {
            String categoryCode = nullToEmpty(activity.getCategoryCode());
            if ("2".equals(categoryCode) || "11".equals(categoryCode)) {
                return true;
            }
            String activityName = nullToEmpty(activity.getName()).toLowerCase();
            if (containsExamKeyword(activityName)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsExamKeyword(String text) {
        if (StringUtils.isBlank(text)) {
            return false;
        }
        return text.contains("检查")
                || text.contains("超声")
                || text.contains("ct")
                || text.contains("mri")
                || text.contains("影像")
                || text.contains("dr")
                || text.contains("x线")
                || text.contains("x-ray");
    }

    private Map<String, Object> toDataset(
            InpatientEncounterContextQueryModel encounter,
            List<ServiceRequestListModel> rows,
            Map<Long, ActivityDefinition> activityMap) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("hospital", Map.of("name", resolveHospitalDisplayName(encounter.getOrganizationId())));

        Map<String, Object> patient = new LinkedHashMap<>();
        patient.put("name", nullToDash(encounter.getPatientName()));
        patient.put("gender", toGenderText(encounter.getGenderEnum()));
        patient.put("age", nullToEmpty(encounter.getAgeText()));
        patient.put("bedNo", buildWardBed(encounter.getWardName(), encounter.getBedName()));
        patient.put("deptName", nullToEmpty(encounter.getOrganizationName()));
        root.put("patient", patient);

        ServiceRequestListModel primary = rows.get(0);
        Map<String, Object> order = new LinkedHashMap<>();
        order.put("sheetNo", nullToEmpty(encounter.getEncounterBusNo()));
        order.put("prescriberName", resolvePrescriberName(primary, encounter.getAttendingDoctorName()));
        order.put("authoredDate", formatDate(primary.getReqAuthoredTime()));
        order.put("lines", buildApplyLines(rows, activityMap));
        root.put("order", order);

        root.put("system", Map.of("printTime", TIME_FMT.format(LocalDateTime.now())));
        return root;
    }

    private Map<String, Object> toDataset(
            OutpatientEncounterContextQueryModel encounter,
            List<ServiceRequestListModel> rows,
            Map<Long, ActivityDefinition> activityMap) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("hospital", Map.of("name", resolveHospitalDisplayName(encounter.getOrganizationId())));

        Map<String, Object> patient = new LinkedHashMap<>();
        patient.put("name", nullToDash(encounter.getPatientName()));
        patient.put("gender", toGenderText(encounter.getGenderEnum()));
        patient.put("age", nullToEmpty(encounter.getAgeText()));
        String bedNo = "—";
        if (encounter.getDisplayOrder() != null) {
            bedNo = String.valueOf(encounter.getDisplayOrder());
        }
        patient.put("bedNo", bedNo);
        patient.put("deptName", nullToEmpty(encounter.getOrganizationName()));
        root.put("patient", patient);

        ServiceRequestListModel primary = rows.get(0);
        Map<String, Object> order = new LinkedHashMap<>();
        order.put("sheetNo", nullToEmpty(encounter.getEncounterBusNo()));
        order.put("prescriberName", resolvePrescriberName(primary, encounter.getDoctorName()));
        order.put("authoredDate", formatDate(primary.getReqAuthoredTime()));
        order.put("lines", buildApplyLines(rows, activityMap));
        root.put("order", order);

        root.put("system", Map.of("printTime", TIME_FMT.format(LocalDateTime.now())));
        return root;
    }

    private List<Map<String, Object>> buildApplyLines(
            List<ServiceRequestListModel> rows,
            Map<Long, ActivityDefinition> activityMap) {
        List<Map<String, Object>> lines = new ArrayList<>();
        for (ServiceRequestListModel row : rows) {
            ActivityDefinition activity = null;
            if (row.getActivityId() != null) {
                activity = activityMap.get(row.getActivityId());
            }
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("itemName", resolveItemName(activity));
            line.put("bodySite", "—");
            line.put("frequency", nullToEmpty(row.getRateCode()));
            line.put("remark", joinRemark(row.getRemark(), row.getEntrustContent()));
            lines.add(line);
        }
        return lines;
    }

    private static String joinRemark(String remark, String entrustContent) {
        List<String> parts = new ArrayList<>();
        if (StringUtils.isNotBlank(remark)) {
            parts.add(remark.trim());
        }
        if (StringUtils.isNotBlank(entrustContent)) {
            parts.add(entrustContent.trim());
        }
        return String.join("；", parts);
    }

    private String resolvePrescriberName(ServiceRequestListModel primary, String fallbackName) {
        if (primary.getRequesterId() != null) {
            Practitioner practitioner = practitionerQueryService.findById(primary.getRequesterId());
            if (practitioner != null && StringUtils.isNotBlank(practitioner.getName())) {
                return practitioner.getName().trim();
            }
        }
        return ReportDatasetTextSupport.trimToDash(fallbackName);
    }

    private static String resolveItemName(ActivityDefinition activity) {
        if (activity != null && StringUtils.isNotBlank(activity.getName())) {
            return activity.getName().trim();
        }
        return "—";
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
        List<String> parts = new ArrayList<>();
        if (StringUtils.isNotBlank(wardName)) {
            parts.add(wardName.trim());
        }
        if (StringUtils.isNotBlank(bedName)) {
            parts.add(bedName.trim());
        }
        return ReportDatasetTextSupport.joinPartsOrDash(parts);
    }

    private static String formatDate(LocalDateTime time) {
        if (time == null) {
            return DATE_FMT.format(LocalDateTime.now());
        }
        return DATE_FMT.format(time);
    }

    private boolean isForwardServiceRow(ServiceRequestListModel row) {
        return row != null && row.getId() != null && row.getRefundServiceId() == null;
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

    private static List<Long> extractLongList(Map<String, Object> params, String... keys) {
        for (String key : keys) {
            Object value = params.get(key);
            if (value instanceof List<?> list) {
                List<Long> out = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof Number number) {
                        out.add(number.longValue());
                    } else if (item instanceof String text && StringUtils.isNotBlank(text)) {
                        try {
                            out.add(Long.parseLong(text.trim()));
                        } catch (NumberFormatException ignored) {
                            // skip invalid
                        }
                    }
                }
                return out;
            }
        }
        return List.of();
    }
}
