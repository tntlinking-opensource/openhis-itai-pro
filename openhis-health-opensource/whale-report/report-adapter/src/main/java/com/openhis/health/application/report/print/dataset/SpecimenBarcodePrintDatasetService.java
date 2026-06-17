/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.report.print.dataset;
import com.openhis.health.application.report.support.ReportDatasetTextSupport;

import com.openhis.health.module.querysupport.encounter.EncounterContextFacade;
import com.openhis.health.domain.administration.encounter.queries.model.OutpatientEncounterContextQueryModel;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 采血条码打印数据集（对齐 {@code bedside_specimen_barcode} 元素包 bindPath）。
 */
@Service
@RequiredArgsConstructor
public class SpecimenBarcodePrintDatasetService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ServiceRequestQueryService serviceRequestQueryService;
    private final ActivityDefinitionQueryService activityDefinitionQueryService;
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
        Long serviceRequestId = extractLong(params, "serviceRequestId", "service_request_id");
        List<Long> serviceRequestIds = extractLongList(params, "serviceRequestIds", "service_request_ids");
        if (serviceRequestId == null && !serviceRequestIds.isEmpty()) {
            serviceRequestId = serviceRequestIds.get(0);
        }
        if (serviceRequestId == null) {
            return Map.of();
        }

        List<ServiceRequestListModel> all =
                serviceRequestQueryService.listServiceRequestsByEncounter(encounterId);
        if (all == null || all.isEmpty()) {
            return Map.of();
        }
        Long targetId = serviceRequestId;
        ServiceRequestListModel row = all.stream()
                .filter(this::isForwardServiceRow)
                .filter(item -> targetId.equals(item.getId()))
                .findFirst()
                .orElse(null);
        if (row == null) {
            return Map.of();
        }

        ActivityDefinition activity = row.getActivityId() != null
                ? activityDefinitionQueryService.findAllByIds(List.of(row.getActivityId())).stream()
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(null)
                : null;
        String itemName = resolveItemName(activity);
        if (!isLabSpecimenService(activity, itemName)) {
            return Map.of();
        }

        OutpatientEncounterContextQueryModel encounter = encounterContextFacade
                .getOutpatientContextByEncounterId(encounterId)
                .orElse(null);

        return toDataset(encounterId, encounter, row, activity, itemName);
    }

    private Map<String, Object> toDataset(
            Long encounterId,
            OutpatientEncounterContextQueryModel encounter,
            ServiceRequestListModel row,
            ActivityDefinition activity,
            String itemName) {
        Map<String, Object> root = new LinkedHashMap<>();

        Map<String, Object> patient = new LinkedHashMap<>();
        patient.put("name", resolvePatientName(encounter));
        patient.put("mrn", encounter != null && encounter.getPatientId() != null
                ? String.valueOf(encounter.getPatientId())
                : "");
        root.put("patient", patient);

        Map<String, Object> specimen = new LinkedHashMap<>();
        specimen.put("typeName", resolveSpecimenTypeName(activity, itemName));
        specimen.put("tubeColor", resolveTubeColor(itemName));
        specimen.put("barcode", buildSpecimenBarcode(encounterId, row.getId()));
        specimen.put("collectTime", TIME_FMT.format(LocalDateTime.now()));
        root.put("specimen", specimen);

        root.put("order", Map.of("itemName", itemName));
        return root;
    }

    static boolean isLabSpecimenService(ActivityDefinition activity, String itemName) {
        String name = nullToEmpty(itemName).toLowerCase();
        if (containsLabKeyword(name)) {
            return true;
        }
        if (activity != null) {
            String categoryCode = nullToEmpty(activity.getCategoryCode());
            if ("1".equals(categoryCode) || "22".equals(categoryCode)) {
                return true;
            }
            String activityName = nullToEmpty(activity.getName()).toLowerCase();
            if (containsLabKeyword(activityName)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsLabKeyword(String text) {
        if (StringUtils.isBlank(text)) {
            return false;
        }
        return text.contains("采血")
                || text.contains("抽血")
                || text.contains("检验")
                || text.contains("化验")
                || text.contains("lab");
    }

    private static String resolveSpecimenTypeName(ActivityDefinition activity, String itemName) {
        if (StringUtils.isNotBlank(itemName) && !"—".equals(itemName)) {
            return itemName.trim();
        }
        if (activity != null && StringUtils.isNotBlank(activity.getName())) {
            return activity.getName().trim();
        }
        return "静脉血";
    }

    private static String resolveTubeColor(String itemName) {
        String name = nullToEmpty(itemName).toLowerCase();
        if (name.contains("edta") || name.contains("紫")) {
            return "紫管(EDTA)";
        }
        if (name.contains("生化") || name.contains("黄")) {
            return "黄管";
        }
        if (name.contains("蓝") || name.contains("凝血")) {
            return "蓝管";
        }
        return "—";
    }

    private static String buildSpecimenBarcode(Long encounterId, Long serviceRequestId) {
        return "SP" + encounterId + "R" + serviceRequestId;
    }

    private static String resolveItemName(ActivityDefinition activity) {
        if (activity != null && StringUtils.isNotBlank(activity.getName())) {
            return activity.getName().trim();
        }
        return "—";
    }

    private boolean isForwardServiceRow(ServiceRequestListModel row) {
        return row != null && row.getId() != null && row.getRefundServiceId() == null;
    }

    private static String resolvePatientName(OutpatientEncounterContextQueryModel encounter) {
        if (encounter != null && StringUtils.isNotBlank(encounter.getPatientName())) {
            return encounter.getPatientName().trim();
        }
        return "—";
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
