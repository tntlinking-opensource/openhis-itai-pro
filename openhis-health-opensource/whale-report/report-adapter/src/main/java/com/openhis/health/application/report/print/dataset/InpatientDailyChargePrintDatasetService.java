/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.report.print.dataset;
import com.openhis.health.application.report.support.ReportDatasetTextSupport;

import com.openhis.health.module.querysupport.financial.dto.NurseStationCostDetailLineDto;
import com.openhis.health.module.querysupport.financial.NurseStationCostDetailReadAssembler;
import com.openhis.health.module.querysupport.encounter.EncounterContextFacade;
import com.openhis.health.module.querysupport.financial.InpatientNurseStationCostDetailLoader;
import com.openhis.health.module.querysupport.product.ProductDefinitionBatchLoader;
import com.openhis.health.module.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.query.InpatientNurseStationCostDetailChargeRow;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.share.constants.InpatientNurseStationConstants;
import com.openhis.health.domain.share.enums.OrganizationType;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 住院患者日费用清单打印数据集（对齐 {@code inpatient_daily_charge_list} 元素包 bindPath）。
 */
@Service
@RequiredArgsConstructor
public class InpatientDailyChargePrintDatasetService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final EncounterContextFacade encounterContextFacade;
    private final InpatientNurseStationCostDetailLoader inpatientNurseStationCostDetailLoader;
    private final NurseStationCostDetailReadAssembler nurseStationCostDetailReadAssembler;
    private final ProductDefinitionBatchLoader productDefinitionBatchLoader;
    private final ChargeItemQueryService chargeItemQueryService;
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

        InpatientEncounterContextQueryModel encounter = encounterContextFacade
                .getInpatientContextByEncounterId(encounterId)
                .orElse(null);
        if (encounter == null) {
            return Map.of();
        }

        LocalDate dateFrom = resolveBillDate(params);
        LocalDate dateTo = resolveDateTo(params, dateFrom);
        List<InpatientNurseStationCostDetailChargeRow> periodRows =
                loadChargeRows(encounterId, dateFrom, dateTo);
        List<Map<String, Object>> lines = buildChargeLines(periodRows);

        LocalDate admissionDate = encounter.getStartTime() != null
                ? encounter.getStartTime().toLocalDate()
                : dateTo;
        List<InpatientNurseStationCostDetailChargeRow> cumulativeRows =
                loadChargeRows(encounterId, admissionDate, dateTo);
        BigDecimal periodSubtotal = sumChargeAmounts(periodRows);
        BigDecimal cumulativeTotal = resolveCumulativeTotal(encounter, cumulativeRows);

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("hospital", Map.of("name", resolveHospitalDisplayName(encounter)));
        root.put("patient", buildPatientMap(encounter));
        root.put("bill", buildBillMap(encounter, dateFrom, dateTo));
        root.put("charge", Map.of("lines", lines));
        root.put("summary", buildSummaryMap(encounter, periodSubtotal, cumulativeTotal));
        root.put("system", Map.of("printTime", TIME_FMT.format(LocalDateTime.now())));
        return root;
    }

    private List<Map<String, Object>> buildChargeLines(List<InpatientNurseStationCostDetailChargeRow> chargeRows) {
        if (chargeRows == null || chargeRows.isEmpty()) {
            return List.of();
        }
        Map<Long, ChargeItem> parentChargeById = loadParentChargeItemsById(chargeRows);
        List<ProductDefinitionBatchLookup> productLookups =
                nurseStationCostDetailReadAssembler.toProductBatchLookups(chargeRows, parentChargeById);
        Map<String, ProductDefinitionDisplayDto> productByKey =
                productDefinitionBatchLoader.loadAsMap(productLookups);
        List<NurseStationCostDetailLineDto> detailLines = nurseStationCostDetailReadAssembler.buildDetailLines(
                chargeRows, productByKey, parentChargeById);

        List<Map<String, Object>> lines = new ArrayList<>();
        for (NurseStationCostDetailLineDto line : detailLines) {
            if (line == null) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("itemName", nullToDash(line.getItemName()));
            row.put("quantity", formatQuantity(line));
            row.put("unitPrice", formatMoney(line.getUnitPrice()));
            row.put("amount", formatMoney(line.getTotalPrice()));
            row.put("category", resolveCategoryDisplay(line.getExpenseBlockCode()));
            lines.add(row);
        }
        return lines;
    }

    private List<InpatientNurseStationCostDetailChargeRow> loadChargeRows(
            Long encounterId, LocalDate dateFrom, LocalDate dateTo) {
        ChargeItemQueryCommand command = new ChargeItemQueryCommand();
        command.setEncounterId(encounterId);
        command.setBusinessDateFrom(dateFrom);
        command.setBusinessDateTo(dateTo);
        List<InpatientNurseStationCostDetailChargeRow> rows =
                inpatientNurseStationCostDetailLoader.listInpatientNurseStationCostDetailChargeRows(command);
        return ReportDatasetTextSupport.listOrEmpty(rows);
    }

    private Map<String, Object> buildPatientMap(InpatientEncounterContextQueryModel encounter) {
        Map<String, Object> patient = new LinkedHashMap<>();
        patient.put("name", nullToDash(encounter.getPatientName()));
        String bedNo = "—";
        if (StringUtils.isNotBlank(encounter.getBedName())) {
            bedNo = encounter.getBedName().trim() + "床";
        }
        patient.put("bedNo", bedNo);
        patient.put("inpatientNo", nullToDash(encounter.getEncounterBusNo()));
        String dept = nullToEmpty(encounter.getWardName());
        if (StringUtils.isNotBlank(encounter.getOrganizationName())) {
            dept = encounter.getOrganizationName().trim();
        }
        patient.put("deptName", ReportDatasetTextSupport.trimToDash(dept));
        return patient;
    }

    private Map<String, Object> buildBillMap(
            InpatientEncounterContextQueryModel encounter, LocalDate dateFrom, LocalDate dateTo) {
        Map<String, Object> bill = new LinkedHashMap<>();
        if (dateFrom.equals(dateTo)) {
            bill.put("billDate", dateFrom.toString());
        } else {
            bill.put("billDate", dateFrom + " ~ " + dateTo);
        }
        bill.put("admissionDays", String.valueOf(resolveAdmissionDays(encounter, dateTo)));
        return bill;
    }

    private Map<String, Object> buildSummaryMap(
            InpatientEncounterContextQueryModel encounter,
            BigDecimal dailySubtotal,
            BigDecimal cumulativeTotal) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("dailySubtotal", formatMoney(dailySubtotal));
        summary.put("cumulativeTotal", formatMoney(cumulativeTotal));
        BigDecimal prepay = ReportDatasetTextSupport.zeroIfNull(encounter.getPaidAmount());
        summary.put("prepayAmount", formatMoney(prepay));
        BigDecimal balance = prepay.subtract(cumulativeTotal);
        summary.put("balance", formatMoney(balance));
        return summary;
    }

    private BigDecimal resolveCumulativeTotal(
            InpatientEncounterContextQueryModel encounter,
            List<InpatientNurseStationCostDetailChargeRow> cumulativeRows) {
        if (encounter.getTotalAmount() != null) {
            return encounter.getTotalAmount();
        }
        return sumChargeAmounts(cumulativeRows);
    }

    private static BigDecimal sumChargeAmounts(List<InpatientNurseStationCostDetailChargeRow> rows) {
        BigDecimal total = BigDecimal.ZERO;
        if (rows == null) {
            return total;
        }
        for (InpatientNurseStationCostDetailChargeRow row : rows) {
            if (row == null || row.getChargeItem() == null || row.getChargeItem().getTotalPrice() == null) {
                continue;
            }
            total = total.add(row.getChargeItem().getTotalPrice());
        }
        return total;
    }

    private static long resolveAdmissionDays(InpatientEncounterContextQueryModel encounter, LocalDate billDate) {
        if (encounter.getStartTime() == null) {
            return 1L;
        }
        long days = ChronoUnit.DAYS.between(encounter.getStartTime().toLocalDate(), billDate) + 1;
        return Math.max(days, 1L);
    }

    private String resolveHospitalDisplayName(InpatientEncounterContextQueryModel encounter) {
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

    private static String resolveCategoryDisplay(String expenseBlockCode) {
        if (StringUtils.isBlank(expenseBlockCode)) {
            return "—";
        }
        String code = expenseBlockCode.trim();
        if (InpatientNurseStationConstants.COST_DETAIL_EXPENSE_BLOCK_MEDICATION.equals(code)) {
            return InpatientNurseStationConstants.COST_DETAIL_EXPENSE_BLOCK_MEDICATION_DISPLAY;
        }
        if (InpatientNurseStationConstants.COST_DETAIL_EXPENSE_BLOCK_SERVICE.equals(code)) {
            return InpatientNurseStationConstants.COST_DETAIL_EXPENSE_BLOCK_SERVICE_DISPLAY;
        }
        if (InpatientNurseStationConstants.COST_DETAIL_EXPENSE_BLOCK_DEVICE.equals(code)) {
            return InpatientNurseStationConstants.COST_DETAIL_EXPENSE_BLOCK_DEVICE_DISPLAY;
        }
        return code;
    }

    private static String formatQuantity(NurseStationCostDetailLineDto line) {
        Object value = line.getQuantityValue();
        if (value == null || String.valueOf(value).trim().isEmpty()) {
            return "—";
        }
        String unit = line.getQuantityUnitDisplay();
        if (StringUtils.isBlank(unit)) {
            unit = line.getQuantityUnit();
        }
        return ReportDatasetTextSupport.valueWithUnit(value, unit);
    }

    private static String formatMoney(BigDecimal value) {
        if (value == null) {
            return "0.00";
        }
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private Map<Long, ChargeItem> loadParentChargeItemsById(List<InpatientNurseStationCostDetailChargeRow> chargeRows) {
        Map<Long, ChargeItem> parentById = new HashMap<>();
        if (chargeRows == null || chargeRows.isEmpty()) {
            return parentById;
        }
        Set<Long> parentIds = new LinkedHashSet<>();
        for (InpatientNurseStationCostDetailChargeRow row : chargeRows) {
            if (row == null || row.getChargeItem() == null) {
                continue;
            }
            Long parentId = row.getChargeItem().getParentId();
            if (parentId != null) {
                parentIds.add(parentId);
            }
        }
        if (parentIds.isEmpty()) {
            return parentById;
        }
        ChargeItemQueryCommand loadCmd = new ChargeItemQueryCommand();
        loadCmd.setIds(new ArrayList<>(parentIds));
        List<ChargeItem> parents = chargeItemQueryService.loadChargeItemsByIds(loadCmd);
        for (ChargeItem parent : parents) {
            if (parent != null && parent.getId() != null) {
                parentById.put(parent.getId(), parent);
            }
        }
        return parentById;
    }

    private static LocalDate resolveBillDate(Map<String, Object> params) {
        for (String key : new String[] {"billDate", "bill_date", "costDate", "cost_date", "dateFrom", "date_from"}) {
            Object raw = params.get(key);
            if (raw instanceof LocalDate date) {
                return date;
            }
            if (raw instanceof String text && StringUtils.isNotBlank(text)) {
                try {
                    return LocalDate.parse(text.trim());
                } catch (Exception ignored) {
                    // try next key
                }
            }
        }
        return LocalDate.now();
    }

    private static LocalDate resolveDateTo(Map<String, Object> params, LocalDate fallback) {
        for (String key : new String[] {"dateTo", "date_to", "billDate", "bill_date"}) {
            Object raw = params.get(key);
            if (raw instanceof LocalDate date) {
                return date;
            }
            if (raw instanceof String text && StringUtils.isNotBlank(text)) {
                try {
                    return LocalDate.parse(text.trim());
                } catch (Exception ignored) {
                    // try next key
                }
            }
        }
        return fallback;
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
