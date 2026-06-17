/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.report.print.dataset;
import com.openhis.health.application.report.support.ReportDatasetTextSupport;

import com.openhis.health.module.querysupport.encounter.EncounterContextFacade;
import com.openhis.health.module.common.assembler.order.OrderCatalogDisplayAssembler;
import com.openhis.health.domain.administration.encounter.queries.model.OutpatientEncounterContextQueryModel;
import com.openhis.health.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.openhis.health.domain.medication.medicationdefinition.service.MedicationDefinitionQueryService;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
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
 * 输液瓶贴打印数据集（对齐 {@code iv_infusion_label} 元素包 bindPath）。
 */
@Service
@RequiredArgsConstructor
public class InfusionLabelPrintDatasetService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final MedicationRequestQueryService medicationRequestQueryService;
    private final MedicationDefinitionQueryService medicationDefinitionQueryService;
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
        Long groupId = extractLong(params, "groupId", "group_id");
        List<Long> medicationRequestIds = extractLongList(params, "medicationRequestIds", "medication_request_ids");
        String labelScope = extractString(params, "labelScope", "label_scope");

        List<MedicationRequestListQueryModel> all =
                medicationRequestQueryService.listMedicationRequestsByEncounter(encounterId);
        if (all == null || all.isEmpty()) {
            return Map.of();
        }

        List<MedicationRequestListQueryModel> groupRows =
                resolveGroupRows(all, groupId, medicationRequestIds, labelScope);
        if (groupRows.isEmpty()) {
            return Map.of();
        }

        Set<Long> medicationIds = groupRows.stream()
                .map(MedicationRequestListQueryModel::getMedicationId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, MedicationDefinition> medicationMap = medicationDefinitionQueryService.findAllByIds(medicationIds)
                .stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(MedicationDefinition::getId, m -> m, (a, b) -> a, LinkedHashMap::new));

        OutpatientEncounterContextQueryModel encounter = encounterContextFacade
                .getOutpatientContextByEncounterId(encounterId)
                .orElse(null);

        return toDataset(encounterId, encounter, groupRows, medicationMap, groupId);
    }

    private List<MedicationRequestListQueryModel> resolveGroupRows(
            List<MedicationRequestListQueryModel> all,
            Long groupId,
            List<Long> medicationRequestIds,
            String labelScope) {
        List<MedicationRequestListQueryModel> filtered = all.stream()
                .filter(this::isForwardMedicationRow)
                .filter(row -> matchesLabelScope(row, labelScope))
                .toList();
        if (groupId != null) {
            return filtered.stream()
                    .filter(row -> groupId.equals(row.getGroupId()))
                    .sorted(Comparator.comparing(
                            MedicationRequestListQueryModel::getSortNumber,
                            Comparator.nullsLast(Integer::compareTo)))
                    .toList();
        }
        if (!medicationRequestIds.isEmpty()) {
            Set<Long> idSet = new LinkedHashSet<>(medicationRequestIds);
            List<MedicationRequestListQueryModel> picked = filtered.stream()
                    .filter(row -> row.getId() != null && idSet.contains(row.getId()))
                    .sorted(Comparator.comparing(
                            MedicationRequestListQueryModel::getSortNumber,
                            Comparator.nullsLast(Integer::compareTo)))
                    .toList();
            if (picked.isEmpty()) {
                return List.of();
            }
            Long resolvedGroupId = picked.get(0).getGroupId();
            if (resolvedGroupId != null) {
                return filtered.stream()
                        .filter(row -> resolvedGroupId.equals(row.getGroupId()))
                        .sorted(Comparator.comparing(
                                MedicationRequestListQueryModel::getSortNumber,
                                Comparator.nullsLast(Integer::compareTo)))
                        .toList();
            }
            return picked;
        }
        return List.of();
    }

    private boolean isForwardMedicationRow(MedicationRequestListQueryModel row) {
        return row != null
                && row.getId() != null
                && row.getRefundMedicineId() == null;
    }

    private boolean matchesLabelScope(MedicationRequestListQueryModel row, String labelScope) {
        if (StringUtils.isBlank(labelScope)) {
            return true;
        }
        String combined = buildMedicationRouteText(row);
        return switch (labelScope.trim().toLowerCase()) {
            case "infusion" -> isInfusionMedication(row, combined);
            case "injection" -> isInjectionMedication(row, combined);
            case "oral" -> isOralMedication(combined);
            case "skin_test" -> isSkinTestMedication(row, combined);
            case "nebulization" -> isNebulizationMedication(combined);
            default -> true;
        };
    }

    private static boolean isSkinTestMedication(MedicationRequestListQueryModel row, String combined) {
        if (Boolean.TRUE.equals(row.getSkinTestFlag())) {
            return true;
        }
        String text = combined.toLowerCase();
        return text.contains("皮试");
    }

    private static boolean isNebulizationMedication(String combined) {
        String text = combined.toLowerCase();
        return text.contains("雾化") || text.contains("吸入") || text.contains("neb");
    }

    private static String buildMedicationRouteText(MedicationRequestListQueryModel row) {
        return StringUtils.defaultString(row.getMethodCode())
                + StringUtils.defaultString(row.getDosageInstruction())
                + StringUtils.defaultString(row.getRateCode());
    }

    private static boolean isInfusionMedication(MedicationRequestListQueryModel row, String combined) {
        return Boolean.TRUE.equals(row.getInfusionFlag())
                || combined.contains("静滴")
                || combined.contains("输液")
                || combined.contains("静脉滴");
    }

    private static boolean isInjectionMedication(MedicationRequestListQueryModel row, String combined) {
        return !isInfusionMedication(row, combined)
                && (combined.contains("注射") || combined.contains("肌注") || combined.contains("皮下"));
    }

    private static boolean isOralMedication(String combined) {
        return combined.contains("口服") || combined.contains("PO") || combined.contains("顿服");
    }

    private Map<String, Object> toDataset(
            Long encounterId,
            OutpatientEncounterContextQueryModel encounter,
            List<MedicationRequestListQueryModel> groupRows,
            Map<Long, MedicationDefinition> medicationMap,
            Long groupId) {
        Map<String, Object> root = new LinkedHashMap<>();

        Map<String, Object> patient = new LinkedHashMap<>();
        patient.put("name", resolvePatientName(encounter));
        patient.put("bedNo", resolveBedOrSlip(encounter));
        String mrn = "";
        if (encounter != null && encounter.getPatientId() != null) {
            mrn = String.valueOf(encounter.getPatientId());
        }
        patient.put("mrn", mrn);
        root.put("patient", patient);

        Map<String, Object> encounterMap = new LinkedHashMap<>();
        String busNo = "";
        if (encounter != null) {
            busNo = nullToEmpty(encounter.getEncounterBusNo());
        }
        encounterMap.put("busNo", busNo);
        String slipNo = "";
        if (encounter != null && encounter.getDisplayOrder() != null) {
            slipNo = String.valueOf(encounter.getDisplayOrder());
        }
        encounterMap.put("slipNo", slipNo);
        root.put("encounter", encounterMap);

        MedicationRequestListQueryModel primary = groupRows.get(0);
        Map<String, Object> infusion = new LinkedHashMap<>();
        infusion.put("groupName", buildGroupName(groupId, primary));
        infusion.put("drugs", buildDrugLines(groupRows, medicationMap));
        root.put("infusion", infusion);

        Map<String, Object> order = new LinkedHashMap<>();
        String route = nullToEmpty(primary.getMethodCode());
        String frequency = nullToEmpty(primary.getRateCode());
        order.put("route", route);
        order.put("frequency", frequency);
        order.put("routeFrequency", joinRouteFrequency(route, frequency, primary.getDosageInstruction()));
        order.put("planTime", formatPlanTime(primary));
        order.put("executeBarcode", buildExecuteBarcode(encounterId, groupId, primary));
        root.put("order", order);

        return root;
    }

    private List<Map<String, Object>> buildDrugLines(
            List<MedicationRequestListQueryModel> groupRows,
            Map<Long, MedicationDefinition> medicationMap) {
        List<Map<String, Object>> lines = new ArrayList<>();
        for (MedicationRequestListQueryModel row : groupRows) {
            MedicationDefinition def = row.getMedicationId() != null
                    ? medicationMap.get(row.getMedicationId())
                    : null;
            String itemName = def != null && StringUtils.isNotBlank(def.getName())
                    ? def.getName().trim()
                    : "—";
            String specText = OrderCatalogDisplayAssembler.resolveMedicationSpecText(def);
            String quantity = formatDecimal(row.getQuantity());
            String unitText = nullToEmpty(row.getUnitCode());
            String dosePart = formatDose(row.getDose(), row.getDoseUnitCode());
            String drugText = itemName;
            if (StringUtils.isNotBlank(specText)) {
                drugText = drugText + " " + specText;
            }
            if (StringUtils.isNotBlank(dosePart)) {
                drugText = drugText + " " + dosePart;
            }
            if (StringUtils.isNotBlank(quantity)) {
                drugText = drugText + " ×" + quantity + unitText;
            }
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("itemName", itemName);
            line.put("specText", specText);
            line.put("quantity", quantity);
            line.put("unitText", unitText);
            line.put("drugText", drugText.trim());
            lines.add(line);
        }
        return lines;
    }

    private static String buildGroupName(Long groupId, MedicationRequestListQueryModel primary) {
        if (groupId != null) {
            return "输液组 " + groupId;
        }
        if (primary.getGroupId() != null) {
            return "输液组 " + primary.getGroupId();
        }
        return "输液组";
    }

    private static String buildExecuteBarcode(
            Long encounterId,
            Long groupId,
            MedicationRequestListQueryModel primary) {
        if (groupId != null) {
            return "IV" + encounterId + "G" + groupId;
        }
        if (primary.getGroupId() != null) {
            return "IV" + encounterId + "G" + primary.getGroupId();
        }
        return "IV" + encounterId + "R" + primary.getId();
    }

    private static String joinRouteFrequency(String route, String frequency, String dosageInstruction) {
        List<String> parts = new ArrayList<>();
        if (StringUtils.isNotBlank(route)) {
            parts.add(route.trim());
        }
        if (StringUtils.isNotBlank(frequency)) {
            parts.add(frequency.trim());
        }
        if (StringUtils.isNotBlank(dosageInstruction)) {
            parts.add(dosageInstruction.trim());
        }
        return String.join(" ", parts);
    }

    private static String formatPlanTime(MedicationRequestListQueryModel row) {
        LocalDateTime time = row.getEffectiveDoseStart();
        if (time == null) {
            time = row.getReqAuthoredTime();
        }
        return ReportDatasetTextSupport.formatDateTime(TIME_FMT, time, LocalDateTime.now());
    }

    private static String resolvePatientName(OutpatientEncounterContextQueryModel encounter) {
        if (encounter == null) {
            return "—";
        }
        if (StringUtils.isNotBlank(encounter.getPatientName())) {
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

    private static String formatDose(BigDecimal dose, String doseUnitCode) {
        if (dose == null) {
            return "";
        }
        String text = dose.stripTrailingZeros().toPlainString();
        if (StringUtils.isNotBlank(doseUnitCode)) {
            return text + doseUnitCode.trim();
        }
        return text;
    }

    private static String formatDecimal(BigDecimal value) {
        if (value == null) {
            return "";
        }
        return value.stripTrailingZeros().toPlainString();
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
