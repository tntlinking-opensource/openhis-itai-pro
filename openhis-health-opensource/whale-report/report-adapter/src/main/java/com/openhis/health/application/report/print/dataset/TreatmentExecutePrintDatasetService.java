/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.report.print.dataset;
import com.openhis.health.application.report.support.ReportDatasetTextSupport;

import com.openhis.health.module.querysupport.encounter.EncounterContextFacade;
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

import java.time.LocalDate;
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
 * 治疗/处置执行单打印数据集（对齐 {@code treatment_execute_sheet} 元素包 bindPath）。
 */
@Service
@RequiredArgsConstructor
public class TreatmentExecutePrintDatasetService {

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

        List<ServiceRequestListModel> rows = resolveTargetRows(forward, serviceRequestIds, activityMap);
        if (rows.isEmpty()) {
            return Map.of();
        }

        OutpatientEncounterContextQueryModel encounter = encounterContextFacade
                .getOutpatientContextByEncounterId(encounterId)
                .orElse(null);

        return toDataset(encounterId, encounter, rows, activityMap);
    }

    private List<ServiceRequestListModel> resolveTargetRows(
            List<ServiceRequestListModel> forward,
            List<Long> serviceRequestIds,
            Map<Long, ActivityDefinition> activityMap) {
        if (!serviceRequestIds.isEmpty()) {
            Set<Long> idSet = new LinkedHashSet<>(serviceRequestIds);
            return forward.stream()
                    .filter(row -> row.getId() != null && idSet.contains(row.getId()))
                    .filter(row -> !isLabSpecimenRow(row, activityMap.get(row.getActivityId())))
                    .toList();
        }
        return forward.stream()
                .filter(row -> !isLabSpecimenRow(row, activityMap.get(row.getActivityId())))
                .toList();
    }

    private boolean isLabSpecimenRow(ServiceRequestListModel row, ActivityDefinition activity) {
        String itemName = resolveItemName(activity);
        return SpecimenBarcodePrintDatasetService.isLabSpecimenService(activity, itemName);
    }

    private Map<String, Object> toDataset(
            Long encounterId,
            OutpatientEncounterContextQueryModel encounter,
            List<ServiceRequestListModel> rows,
            Map<Long, ActivityDefinition> activityMap) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("hospital", Map.of("name", resolveHospitalDisplayName(encounter)));

        Map<String, Object> patient = new LinkedHashMap<>();
        patient.put("name", resolvePatientName(encounter));
        String genderEnum = null;
        if (encounter != null) {
            genderEnum = encounter.getGenderEnum();
        }
        patient.put("gender", toGenderText(genderEnum));
        String ageText = "";
        if (encounter != null) {
            ageText = nullToEmpty(encounter.getAgeText());
        }
        patient.put("age", ageText);
        patient.put("bedNo", resolveBedOrSlip(encounter));
        String deptName = "";
        if (encounter != null) {
            deptName = nullToEmpty(encounter.getOrganizationName());
        }
        patient.put("deptName", deptName);
        root.put("patient", patient);

        ServiceRequestListModel primary = rows.get(0);
        Map<String, Object> order = new LinkedHashMap<>();
        order.put("sheetNo", resolveSheetNo(encounter));
        order.put("prescriberName", resolvePrescriberName(primary, encounter));
        order.put("authoredDate", formatDate(primary.getReqAuthoredTime()));
        order.put("lines", buildProcedureLines(rows, activityMap));
        root.put("order", order);

        Map<String, Object> execution = new LinkedHashMap<>();
        execution.put("nurseName", "");
        execution.put("executeTime", TIME_FMT.format(LocalDateTime.now()));
        root.put("execution", execution);
        return root;
    }

    private List<Map<String, Object>> buildProcedureLines(
            List<ServiceRequestListModel> rows,
            Map<Long, ActivityDefinition> activityMap) {
        List<Map<String, Object>> lines = new ArrayList<>();
        for (ServiceRequestListModel row : rows) {
            ActivityDefinition activity = row.getActivityId() != null
                    ? activityMap.get(row.getActivityId())
                    : null;
            String itemName = resolveItemName(activity);
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("itemName", itemName);
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

    private String resolvePrescriberName(ServiceRequestListModel primary, OutpatientEncounterContextQueryModel encounter) {
        if (primary.getRequesterId() != null) {
            Practitioner practitioner = practitionerQueryService.findById(primary.getRequesterId());
            if (practitioner != null && StringUtils.isNotBlank(practitioner.getName())) {
                return practitioner.getName().trim();
            }
        }
        if (encounter != null && StringUtils.isNotBlank(encounter.getDoctorName())) {
            return encounter.getDoctorName().trim();
        }
        return "—";
    }

    private static String resolveSheetNo(OutpatientEncounterContextQueryModel encounter) {
        if (encounter != null && StringUtils.isNotBlank(encounter.getEncounterBusNo())) {
            return encounter.getEncounterBusNo().trim();
        }
        if (encounter != null && encounter.getEncounterId() != null) {
            return String.valueOf(encounter.getEncounterId());
        }
        return "—";
    }

    private String resolveHospitalDisplayName(OutpatientEncounterContextQueryModel encounter) {
        if (encounter != null && encounter.getOrganizationId() != null) {
            Long orgId = encounter.getOrganizationId();
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
        }
        return "";
    }

    private static String resolveItemName(ActivityDefinition activity) {
        if (activity != null && StringUtils.isNotBlank(activity.getName())) {
            return activity.getName().trim();
        }
        return "—";
    }

    private static String resolvePatientName(OutpatientEncounterContextQueryModel encounter) {
        if (encounter != null && StringUtils.isNotBlank(encounter.getPatientName())) {
            return encounter.getPatientName().trim();
        }
        return "—";
    }

    private static String resolveBedOrSlip(OutpatientEncounterContextQueryModel encounter) {
        if (encounter == null) {
            return "—";
        }
        if (encounter.getDisplayOrder() != null) {
            return String.valueOf(encounter.getDisplayOrder());
        }
        if (StringUtils.isNotBlank(encounter.getEncounterBusNo())) {
            return encounter.getEncounterBusNo().trim();
        }
        return "—";
    }

    private static String toGenderText(String genderEnum) {
        AdministrativeGender g = EnumUtil.fromCode(AdministrativeGender.class, genderEnum);
        if (g != null) {
            return g.getDisplay();
        }
        return ReportDatasetTextSupport.genderCodeOrEmpty(genderEnum);
    }

    private static String formatDate(LocalDateTime time) {
        if (time == null) {
            return LocalDate.now().format(DATE_FMT);
        }
        return time.format(DATE_FMT);
    }

    private boolean isForwardServiceRow(ServiceRequestListModel row) {
        return row != null && row.getId() != null && row.getRefundServiceId() == null;
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
