/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.report.print.dataset;
import com.openhis.health.application.report.support.ReportDatasetTextSupport;

import com.openhis.health.module.querysupport.procedure.dto.NurseStationOrderExecutePatientGroupDto;
import com.openhis.health.module.querysupport.procedure.dto.NurseStationOrderExecuteRowDto;
import com.openhis.health.module.querysupport.procedure.dto.NurseStationOrderExecuteSlotDto;
import com.openhis.health.module.querysupport.procedure.NurseStationOrderExecuteListQuery;
import com.openhis.health.module.querysupport.procedure.NurseStationOrderExecuteListResult;
import com.openhis.health.module.querysupport.procedure.NurseStationOrderExecuteListLoader;
import com.openhis.health.module.querysupport.encounter.EncounterContextFacade;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 住院医嘱执行单打印数据集（对齐 {@code order_execute_sheet} 元素包 bindPath）。
 */
@Service
@RequiredArgsConstructor
public class OrderExecutePrintDatasetService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter SLOT_FMT = DateTimeFormatter.ofPattern("MM-dd HH:mm");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final NurseStationOrderExecuteListLoader nurseStationOrderExecuteListLoader;
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
        LocalDateTime executionDeadline = extractDateTime(params, "executionDeadline", "execution_deadline");
        if (executionDeadline == null) {
            return Map.of();
        }

        String executeTab = extractString(params, "executeTab", "execute_tab");
        if (StringUtils.isBlank(executeTab)) {
            executeTab = "pending";
        }
        String therapyType = extractString(params, "therapyType", "therapy_type");
        if (StringUtils.isBlank(therapyType)) {
            therapyType = "all";
        }
        Set<String> listRowKeyFilter = extractStringSet(params, "listRowKeys", "list_row_keys");

        NurseStationOrderExecuteListQuery query = new NurseStationOrderExecuteListQuery();
        query.setEncounterIds(List.of(encounterId));
        query.setExecuteTab(executeTab.trim());
        query.setTherapyType(therapyType.trim());
        query.setExecutionDeadline(executionDeadline);

        NurseStationOrderExecuteListResult result =
                nurseStationOrderExecuteListLoader.listNurseStationOrderExecute(query);
        List<NurseStationOrderExecuteRowDto> rows = resolveRows(result, encounterId, listRowKeyFilter);
        rows = applyRowFilter(rows, params);
        if (rows.isEmpty()) {
            return Map.of();
        }

        InpatientEncounterContextQueryModel encounter = encounterContextFacade
                .getInpatientContextByEncounterId(encounterId)
                .orElse(null);
        return toDataset(encounter, executeTab, therapyType, executionDeadline, rows);
    }

    private List<NurseStationOrderExecuteRowDto> applyRowFilter(
            List<NurseStationOrderExecuteRowDto> rows,
            Map<String, Object> params) {
        String rowFilter = extractString(params, "rowFilter", "row_filter");
        if (StringUtils.isBlank(rowFilter) || "all".equalsIgnoreCase(rowFilter.trim())) {
            return rows;
        }
        if ("infusion".equalsIgnoreCase(rowFilter.trim())) {
            return rows.stream().filter(this::isInfusionExecuteRow).toList();
        }
        return rows;
    }

    private boolean isInfusionExecuteRow(NurseStationOrderExecuteRowDto row) {
        if (row == null || !"MED_REQUEST".equalsIgnoreCase(StringUtils.trimToEmpty(row.getOrderType()))) {
            return false;
        }
        String combined = StringUtils.defaultString(row.getUsageDisplay())
                + StringUtils.defaultString(row.getAdviceDisplayName())
                + StringUtils.defaultString(row.getFrequencyDisplay());
        return combined.contains("静滴")
                || combined.contains("输液")
                || combined.contains("静脉滴")
                || combined.contains("IV");
    }

    private List<NurseStationOrderExecuteRowDto> resolveRows(
            NurseStationOrderExecuteListResult result,
            Long encounterId,
            Set<String> listRowKeyFilter) {
        if (result == null || result.getGroups() == null) {
            return List.of();
        }
        List<NurseStationOrderExecuteRowDto> rows = new ArrayList<>();
        for (NurseStationOrderExecutePatientGroupDto group : result.getGroups()) {
            if (group == null || group.getOrders() == null) {
                continue;
            }
            if (group.getEncounterId() != null && !encounterId.equals(group.getEncounterId())) {
                continue;
            }
            rows.addAll(group.getOrders());
        }
        if (listRowKeyFilter.isEmpty()) {
            return rows;
        }
        return rows.stream()
                .filter(row -> row != null && matchesListRowKey(row, listRowKeyFilter))
                .toList();
    }

    private static boolean matchesListRowKey(NurseStationOrderExecuteRowDto row, Set<String> listRowKeyFilter) {
        String listRowKey = StringUtils.trimToEmpty(row.getListRowKey());
        if (StringUtils.isNotBlank(listRowKey) && listRowKeyFilter.contains(listRowKey)) {
            return true;
        }
        if (row.getOrderId() != null && StringUtils.isNotBlank(row.getOrderType())) {
            String fallback = row.getOrderId() + "\u0001" + row.getOrderType().trim().toUpperCase();
            return listRowKeyFilter.contains(fallback);
        }
        return false;
    }

    private Map<String, Object> toDataset(
            InpatientEncounterContextQueryModel encounter,
            String executeTab,
            String therapyType,
            LocalDateTime executionDeadline,
            List<NurseStationOrderExecuteRowDto> rows) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("hospital", Map.of("name", resolveHospitalDisplayName(encounter)));

        Map<String, Object> patient = new LinkedHashMap<>();
        if (encounter != null) {
            patient.put("name", nullToDash(encounter.getPatientName()));
            patient.put("gender", toGenderText(encounter.getGenderEnum()));
            patient.put("age", nullToEmpty(encounter.getAgeText()));
            patient.put("encounterBusNo", nullToEmpty(encounter.getEncounterBusNo()));
            patient.put("wardBed", buildWardBed(
                    encounter.getWardName(),
                    encounter.getOrganizationName(),
                    encounter.getBedName()));
            patient.put("deptName", nullToEmpty(encounter.getOrganizationName()));
        } else {
            patient.put("name", "—");
            patient.put("gender", "");
            patient.put("age", "");
            patient.put("encounterBusNo", "");
            patient.put("wardBed", "");
            patient.put("deptName", "");
        }
        root.put("patient", patient);

        Map<String, Object> sheet = new LinkedHashMap<>();
        sheet.put("executeTabText", resolveExecuteTabText(executeTab));
        sheet.put("therapyTypeText", resolveTherapyTypeText(therapyType));
        sheet.put("executionDeadline", TIME_FMT.format(executionDeadline));
        root.put("sheet", sheet);

        Map<String, Object> order = new LinkedHashMap<>();
        order.put("lines", buildOrderLines(rows, executeTab));
        root.put("order", order);

        root.put("system", Map.of("printTime", TIME_FMT.format(LocalDateTime.now())));
        return root;
    }

    private List<Map<String, Object>> buildOrderLines(List<NurseStationOrderExecuteRowDto> rows, String executeTab) {
        List<Map<String, Object>> lines = new ArrayList<>();
        for (NurseStationOrderExecuteRowDto row : rows) {
            if (row == null) {
                continue;
            }
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("typeLabel", buildTypeLabel(row));
            line.put("content", buildContent(row));
            line.put("dosage", nullToDash(row.getDosageDisplay()));
            line.put("usageFreq", buildUsageFreq(row));
            line.put("startStop", buildStartStop(row));
            line.put("planTimes", buildPlanTimes(row, executeTab));
            line.put("prescriberName", nullToDash(row.getRequesterName()));
            lines.add(line);
        }
        return lines;
    }

    private static String buildTypeLabel(NurseStationOrderExecuteRowDto row) {
        String therapy = StringUtils.firstNonBlank(row.getTherapyEnumText(), resolveTherapyEnumShort(row.getTherapyEnum()));
        String kind = ReportDatasetTextSupport.orderKindLabel(row.getOrderType());
        if (StringUtils.isNotBlank(therapy)) {
            return therapy + "/" + kind;
        }
        return kind;
    }

    private static String resolveTherapyEnumShort(String therapyEnum) {
        if (StringUtils.isBlank(therapyEnum)) {
            return "";
        }
        return switch (therapyEnum.trim().toUpperCase()) {
            case "LONG_TERM" -> "长期";
            case "TEMPORARY" -> "临时";
            case "DISCHARGE_MEDICATION" -> "出院带药";
            default -> therapyEnum.trim();
        };
    }

    private static String buildContent(NurseStationOrderExecuteRowDto row) {
        String name = StringUtils.trimToEmpty(row.getAdviceDisplayName());
        String spec = StringUtils.trimToEmpty(row.getSpecificationText());
        if (StringUtils.isNotBlank(name) && StringUtils.isNotBlank(spec)) {
            return name + "【" + spec + "】";
        }
        return StringUtils.firstNonBlank(name, spec, "—");
    }

    private static String buildUsageFreq(NurseStationOrderExecuteRowDto row) {
        String usage = StringUtils.trimToEmpty(row.getUsageDisplay());
        String freq = StringUtils.trimToEmpty(row.getFrequencyDisplay());
        if (StringUtils.isNotBlank(usage) && StringUtils.isNotBlank(freq)) {
            return usage + "，" + freq;
        }
        return StringUtils.firstNonBlank(usage, freq, "—");
    }

    private static String buildStartStop(NurseStationOrderExecuteRowDto row) {
        String start = ReportDatasetTextSupport.formatDateTimeOrEmpty(DATE_FMT, row.getAuthoredTime());
        String stop = ReportDatasetTextSupport.formatDateTimeOrEmpty(DATE_FMT, row.getStopTime());
        if (StringUtils.isNotBlank(start) && StringUtils.isNotBlank(stop)) {
            return start + "\n" + stop;
        }
        return StringUtils.firstNonBlank(start, stop, "—");
    }

    private String buildPlanTimes(NurseStationOrderExecuteRowDto row, String executeTab) {
        List<NurseStationOrderExecuteSlotDto> slots = resolveSlotsForTab(row, executeTab);
        if (slots.isEmpty()) {
            return "—";
        }
        return slots.stream()
                .map(NurseStationOrderExecuteSlotDto::getPlanTime)
                .filter(Objects::nonNull)
                .sorted()
                .map(time -> time.format(SLOT_FMT))
                .collect(Collectors.joining(" "));
    }

    private static List<NurseStationOrderExecuteSlotDto> resolveSlotsForTab(
            NurseStationOrderExecuteRowDto row,
            String executeTab) {
        List<NurseStationOrderExecuteSlotDto> slots = switch (StringUtils.trimToEmpty(executeTab).toLowerCase()) {
            case "executed" -> row.getExecutedSlots();
            case "skipped" -> row.getSkippedSlots();
            case "cancelled" -> row.getCancelledSlots();
            default -> row.getPendingSlots();
        };
        return ReportDatasetTextSupport.listOrEmpty(slots);
    }

    private static String resolveExecuteTabText(String executeTab) {
        return switch (StringUtils.trimToEmpty(executeTab).toLowerCase()) {
            case "executed" -> "已执行";
            case "skipped" -> "不执行";
            case "cancelled" -> "取消执行";
            default -> "待执行";
        };
    }

    private static String resolveTherapyTypeText(String therapyType) {
        return switch (StringUtils.trimToEmpty(therapyType).toLowerCase()) {
            case "long" -> "长期";
            case "temp" -> "临时";
            default -> "全部";
        };
    }

    private String resolveHospitalDisplayName(InpatientEncounterContextQueryModel encounter) {
        if (encounter == null || encounter.getOrganizationId() == null) {
            return "";
        }
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
        return "";
    }

    private static String buildWardBed(String wardName, String organizationName, String bedName) {
        String ward = StringUtils.firstNonBlank(wardName, organizationName, "");
        if (StringUtils.isNotBlank(bedName)) {
            return ReportDatasetTextSupport.wardBedLabel(ward, bedName);
        }
        return ward;
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
            Object raw = params.get(key);
            if (raw instanceof Number number) {
                return number.longValue();
            }
            if (raw instanceof String text && StringUtils.isNotBlank(text)) {
                try {
                    return Long.parseLong(text.trim());
                } catch (NumberFormatException ignored) {
                    // next key
                }
            }
        }
        return null;
    }

    private static String extractString(Map<String, Object> params, String... keys) {
        for (String key : keys) {
            Object raw = params.get(key);
            if (raw instanceof String text) {
                return text;
            }
        }
        return null;
    }

    private static LocalDateTime extractDateTime(Map<String, Object> params, String... keys) {
        for (String key : keys) {
            Object raw = params.get(key);
            if (raw instanceof LocalDateTime time) {
                return time;
            }
            if (raw instanceof String text && StringUtils.isNotBlank(text)) {
                String normalized = text.trim();
                try {
                    return LocalDateTime.parse(normalized, TIME_FMT);
                } catch (Exception ignored) {
                    try {
                        return LocalDateTime.parse(normalized);
                    } catch (Exception ignored2) {
                        // next key
                    }
                }
            }
        }
        return null;
    }

    private static Set<String> extractStringSet(Map<String, Object> params, String... keys) {
        for (String key : keys) {
            Object raw = params.get(key);
            if (raw instanceof List<?> list) {
                Set<String> out = new LinkedHashSet<>();
                for (Object item : list) {
                    if (item != null && StringUtils.isNotBlank(String.valueOf(item))) {
                        out.add(String.valueOf(item).trim());
                    }
                }
                return out;
            }
        }
        return Set.of();
    }
}
