/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.report.print.dataset;
import com.openhis.health.application.report.support.ReportDatasetTextSupport;

import com.openhis.health.module.querysupport.financial.dto.InpatientSettleChargeLineDto;
import com.openhis.health.module.querysupport.financial.InpatientSettleChargesQuery;
import com.openhis.health.module.querysupport.financial.InpatientSettleChargesResult;
import com.openhis.health.module.querysupport.financial.InpatientSettleSummaryQuery;
import com.openhis.health.module.querysupport.financial.InpatientSettleSummaryResult;
import com.openhis.health.module.querysupport.financial.SettledInpatientPaymentViewQuery;
import com.openhis.health.module.querysupport.financial.SettledInpatientPaymentViewResult;
import com.openhis.health.module.querysupport.financial.InpatientSettleReadLoader;
import com.openhis.health.module.querysupport.encounter.EncounterContextFacade;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.share.constants.InpatientSettleConstants;
import com.openhis.health.domain.share.enums.OrganizationType;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 住院费用总清单打印数据集（对齐 {@code inpatient_charge_summary} 元素包 bindPath）。
 */
@Service
@RequiredArgsConstructor
public class InpatientChargeSummaryPrintDatasetService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter OCCURRENCE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final InpatientSettleReadLoader inpatientSettleReadLoader;
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
        Long patientId = extractLong(params, "patientId", "patient_id");
        if (encounterId == null || patientId == null) {
            return Map.of();
        }

        Long paymentId = extractLong(params, "paymentId", "payment_id");
        List<Long> chargeItemIds = extractLongList(params, "chargeItemIds", "charge_item_ids");
        Set<Long> chargeItemIdFilter;
        if (chargeItemIds.isEmpty()) {
            chargeItemIdFilter = Set.of();
        } else {
            chargeItemIdFilter = new LinkedHashSet<>(chargeItemIds);
        }

        InpatientEncounterContextQueryModel encounter = encounterContextFacade
                .getInpatientContextByEncounterId(encounterId)
                .orElse(null);

        List<InpatientSettleChargeLineDto> lines;
        LoadContext context;
        if (paymentId != null) {
            context = loadSettledContext(encounterId, patientId, paymentId);
            lines = filterLines(context.lines(), chargeItemIdFilter);
        } else {
            context = loadPendingContext(encounterId, patientId, chargeItemIds);
            lines = filterLines(context.lines(), chargeItemIdFilter);
        }
        if (lines.isEmpty()) {
            return Map.of();
        }
        return toDataset(encounter, context, lines);
    }

    private LoadContext loadPendingContext(Long encounterId, Long patientId, List<Long> selectedChargeItemIds) {
        InpatientSettleChargesQuery chargeQuery = new InpatientSettleChargesQuery();
        chargeQuery.setEncounterId(encounterId);
        chargeQuery.setPatientId(patientId);
        chargeQuery.setPresentationMode(InpatientSettleConstants.PRESENTATION_MODE_DETAIL);
        InpatientSettleChargesResult chargeResult =
                inpatientSettleReadLoader.queryInpatientSettleCharges(chargeQuery);
        List<InpatientSettleChargeLineDto> lines = ReportDatasetTextSupport.listOrEmpty(chargeResult.getLines());

        InpatientSettleSummaryQuery summaryQuery = new InpatientSettleSummaryQuery();
        summaryQuery.setEncounterId(encounterId);
        summaryQuery.setPatientId(patientId);
        if (!selectedChargeItemIds.isEmpty()) {
            summaryQuery.setSelectedChargeItemIds(selectedChargeItemIds);
        }
        InpatientSettleSummaryResult summary = null;
        try {
            summary = inpatientSettleReadLoader.loadInpatientSettleSummary(summaryQuery);
        } catch (BusinessException ignored) {
            // 已结算患者走 paymentId 路径；此处忽略摘要失败
        }
        BigDecimal pendingTotal = chargeResult.getPendingSettlementTotal();
        BigDecimal prepayNetBalance = null;
        BigDecimal balanceHint = null;
        if (summary != null) {
            pendingTotal = summary.getPendingSettlementTotal();
            prepayNetBalance = summary.getPrepayNetBalance();
            balanceHint = summary.getBalanceHint();
        }
        return new LoadContext(lines, pendingTotal, prepayNetBalance, balanceHint, null, null);
    }

    private LoadContext loadSettledContext(Long encounterId, Long patientId, Long paymentId) {
        SettledInpatientPaymentViewQuery query = new SettledInpatientPaymentViewQuery();
        query.setEncounterId(encounterId);
        query.setPatientId(patientId);
        query.setPaymentId(paymentId);
        query.setPresentationMode(InpatientSettleConstants.PRESENTATION_MODE_DETAIL);
        SettledInpatientPaymentViewResult response =
                inpatientSettleReadLoader.loadSettledInpatientPaymentView(query);
        return new LoadContext(
                ReportDatasetTextSupport.listOrEmpty(response.getLines()),
                response.getReceivableTotal(),
                null,
                null,
                response.getSettleTime(),
                response.getOperatorName());
    }

    private Map<String, Object> toDataset(
            InpatientEncounterContextQueryModel encounter,
            LoadContext context,
            List<InpatientSettleChargeLineDto> lines) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("hospital", Map.of("name", resolveHospitalDisplayName(encounter)));
        root.put("patient", buildPatientMap(encounter, context));
        root.put("settle", buildSettleMap(context));
        root.put("charge", Map.of("lines", buildChargeLines(lines)));
        root.put("summary", buildSummaryMap(context));
        root.put("system", Map.of("printTime", TIME_FMT.format(LocalDateTime.now())));
        return root;
    }

    private static Map<String, Object> buildPatientMap(
            InpatientEncounterContextQueryModel encounter,
            LoadContext context) {
        Map<String, Object> patient = new LinkedHashMap<>();
        if (encounter != null) {
            patient.put("name", nullToDash(encounter.getPatientName()));
            String bedNo = "—";
            if (StringUtils.isNotBlank(encounter.getBedName())) {
                bedNo = encounter.getBedName().trim() + "床";
            }
            patient.put("bedNo", bedNo);
            patient.put("inpatientNo", nullToDash(encounter.getEncounterBusNo()));
            String dept = StringUtils.firstNonBlank(encounter.getOrganizationName(), encounter.getWardName(), "");
            patient.put("deptName", ReportDatasetTextSupport.trimToDash(dept));
        } else {
            patient.put("name", "—");
            patient.put("bedNo", "—");
            patient.put("inpatientNo", "—");
            patient.put("deptName", "—");
        }
        return patient;
    }

    private static Map<String, Object> buildSettleMap(LoadContext context) {
        Map<String, Object> settle = new LinkedHashMap<>();
        settle.put("settleTime", context.settleTime() != null
                ? TIME_FMT.format(context.settleTime())
                : TIME_FMT.format(LocalDateTime.now()));
        settle.put("operatorName", nullToDash(context.operatorName()));
        return settle;
    }

    private static Map<String, Object> buildSummaryMap(LoadContext context) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalAmount", formatMoney(context.totalAmount()));
        summary.put("prepayAmount", formatMoney(context.prepayAmount()));
        summary.put("balanceHint", formatMoney(context.balanceHint()));
        return summary;
    }

    private static List<Map<String, Object>> buildChargeLines(List<InpatientSettleChargeLineDto> lines) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (InpatientSettleChargeLineDto line : lines) {
            if (line == null) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("itemName", nullToDash(line.getItemName()));
            row.put("spec", nullToDash(line.getSpecText()));
            row.put("quantity", formatQuantity(line.getQuantityValue(), line.getQuantityUnit()));
            row.put("unitPrice", formatMoney(line.getUnitPrice()));
            row.put("amount", formatMoney(line.getLineAmount()));
            row.put("category", nullToDash(line.getCategoryDisplay()));
            row.put("occurrenceTime", line.getOccurrenceTime() != null
                    ? OCCURRENCE_FMT.format(line.getOccurrenceTime())
                    : "—");
            out.add(row);
        }
        return out;
    }

    private static List<InpatientSettleChargeLineDto> filterLines(
            List<InpatientSettleChargeLineDto> lines,
            Set<Long> chargeItemIdFilter) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        if (chargeItemIdFilter.isEmpty()) {
            return lines;
        }
        return lines.stream()
                .filter(line -> line != null
                        && line.getChargeItemId() != null
                        && chargeItemIdFilter.contains(line.getChargeItemId()))
                .toList();
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

    private static String formatQuantity(BigDecimal quantity, String unit) {
        if (quantity == null) {
            return "—";
        }
        String qty = quantity.stripTrailingZeros().toPlainString();
        if (StringUtils.isNotBlank(unit)) {
            return qty + " " + unit.trim();
        }
        return qty;
    }

    private static String formatMoney(BigDecimal value) {
        if (value == null) {
            return "0.00";
        }
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String nullToDash(String value) {
        return ReportDatasetTextSupport.trimToDash(value);
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

    private static List<Long> extractLongList(Map<String, Object> params, String... keys) {
        for (String key : keys) {
            Object raw = params.get(key);
            if (raw instanceof List<?> list) {
                List<Long> out = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof Number number) {
                        out.add(number.longValue());
                    } else if (item instanceof String text && StringUtils.isNotBlank(text)) {
                        try {
                            out.add(Long.parseLong(text.trim()));
                        } catch (NumberFormatException ignored) {
                            // skip
                        }
                    }
                }
                return out;
            }
        }
        return List.of();
    }

    private record LoadContext(
            List<InpatientSettleChargeLineDto> lines,
            BigDecimal totalAmount,
            BigDecimal prepayAmount,
            BigDecimal balanceHint,
            LocalDateTime settleTime,
            String operatorName) {
    }
}
