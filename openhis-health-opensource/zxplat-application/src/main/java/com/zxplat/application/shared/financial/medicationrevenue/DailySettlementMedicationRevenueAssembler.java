/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.financial.medicationrevenue;

import com.zxplat.application.shared.financial.DailySettlementFinTypeAmountLoader;
import com.zxplat.domain.administration.chargeitem.entity.ChargeItem;
import com.zxplat.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.zxplat.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.zxplat.domain.financial.paymentreconciliation.queries.model.PaymentReconciliationQueryModel;
import com.zxplat.domain.medication.medicationdispense.entity.MedicationDispense;
import com.zxplat.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.zxplat.domain.share.constants.DailySettlementSharedConstants;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.share.enums.DispenseStatus;
import com.zxplat.domain.share.financial.DailySettlementRefundPaymentRules;
import com.zxplat.framework.core.application.dto.OptionDto;
import com.zxplat.framework.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 日结药品收入 TAB 组装：发药口径汇总 + 收费侧药品明细 + 未发提醒。
 */
@Component
@RequiredArgsConstructor
public class DailySettlementMedicationRevenueAssembler {

    private static final List<String> DRUG_FIN_TYPE_CODES = List.of(
        DailySettlementSharedConstants.WESTERN_MEDICINE_FIN_TYPE_CODE,
        DailySettlementSharedConstants.PURCHASED_MEDICINE_FIN_TYPE_CODE,
        DailySettlementSharedConstants.CHINESE_HERBAL_MEDICINE_FIN_TYPE_CODE,
        DailySettlementSharedConstants.CHINESE_PATENT_MEDICINE_FIN_TYPE_CODE);

    private static final int UNDISPENSED_ALERT_LIMIT = 200;

    private final DailySettlementFinTypeAmountLoader dailySettlementFinTypeAmountLoader;
    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;
    private final MedicationDispenseQueryService medicationDispenseQueryService;

    /**
     * 组装药品收入 TAB 响应。
     *
     * @param paymentModels          日结筛选窗内 payment
     * @param chargeItemsByPaymentId payment → charge_item
     * @param dispenseWindowFrom     发药口径时间起（含）
     * @param dispenseWindowTo       发药口径时间止（含）
     * @param finTypeOptions         fin_type 字典
     * @return 预览响应
     */
    public PreviewDailySettlementMedicationRevenueResponse assemble(
        List<PaymentReconciliationQueryModel> paymentModels,
        Map<Long, List<ChargeItem>> chargeItemsByPaymentId,
        LocalDateTime dispenseWindowFrom,
        LocalDateTime dispenseWindowTo,
        List<OptionDto> finTypeOptions) {
        PreviewDailySettlementMedicationRevenueResponse response =
            new PreviewDailySettlementMedicationRevenueResponse();
        List<DailySettlementMedicationChargeLineDto> drugLines = new ArrayList<>();
        if (paymentModels == null || paymentModels.isEmpty()) {
            response.setChargeDrugLines(drugLines);
            response.setMedicationRevenueSummary(this.emptySummary());
            response.setUndispensedAlert(this.emptyAlert());
            return response;
        }
        List<ChargeItem> allChargeItems = this.flattenChargeItems(chargeItemsByPaymentId);
        Map<Long, String> finTypeByChargeItemId = this.safeMapFinTypes(allChargeItems);
        Map<Long, ChargeItemDefinition> definitionById = this.loadDefinitions(allChargeItems);
        Map<Long, MedicationDispense> dispenseById = this.loadDispenses(allChargeItems);
        Map<String, String> finTypeNameByCode = this.buildFinTypeNameMap(finTypeOptions);
        Map<String, BigDecimal> dispenseRevenueByFinType = new LinkedHashMap<>();
        BigDecimal totalDispenseRevenue = BigDecimal.ZERO;
        List<DailySettlementMedicationChargeLineDto> undispensed = new ArrayList<>();
        for (PaymentReconciliationQueryModel payment : paymentModels) {
            if (payment == null || payment.getId() == null) {
                continue;
            }
            boolean refund = DailySettlementRefundPaymentRules.isRefundPaymentForSettlement(
                payment.getPaymentEnum(),
                payment.getRelationId(),
                payment.getStatusEnum());
            List<ChargeItem> chargeItems = chargeItemsByPaymentId == null
                ? List.of()
                : chargeItemsByPaymentId.getOrDefault(payment.getId(), List.of());
            for (ChargeItem chargeItem : chargeItems) {
                if (chargeItem == null || chargeItem.getId() == null) {
                    continue;
                }
                String finTypeCode = finTypeByChargeItemId.get(chargeItem.getId());
                if (!this.isDrugFinType(finTypeCode)) {
                    continue;
                }
                DailySettlementMedicationChargeLineDto line = this.buildLine(
                    payment,
                    chargeItem,
                    finTypeCode,
                    finTypeNameByCode.getOrDefault(finTypeCode, finTypeCode),
                    definitionById,
                    dispenseById,
                    refund);
                drugLines.add(line);
                if (Boolean.TRUE.equals(line.getUndispensed())) {
                    if (undispensed.size() < UNDISPENSED_ALERT_LIMIT) {
                        undispensed.add(line);
                    }
                }
                if (this.isDispenseRevenueEligible(line, dispenseWindowFrom, dispenseWindowTo)) {
                    BigDecimal amount = line.getAmount() == null ? BigDecimal.ZERO : line.getAmount();
                    totalDispenseRevenue = totalDispenseRevenue.add(amount);
                    BigDecimal existing = dispenseRevenueByFinType.getOrDefault(finTypeCode, BigDecimal.ZERO);
                    dispenseRevenueByFinType.put(finTypeCode, existing.add(amount));
                }
            }
        }
        response.setChargeDrugLines(drugLines);
        response.setMedicationRevenueSummary(
            this.buildSummary(totalDispenseRevenue, dispenseRevenueByFinType, finTypeNameByCode));
        DailySettlementMedicationUndispensedAlertDto alert = new DailySettlementMedicationUndispensedAlertDto();
        alert.setCount((int) drugLines.stream().filter(line -> Boolean.TRUE.equals(line.getUndispensed())).count());
        alert.setItems(undispensed);
        response.setUndispensedAlert(alert);
        return response;
    }

    private DailySettlementMedicationChargeLineDto buildLine(
        PaymentReconciliationQueryModel payment,
        ChargeItem chargeItem,
        String finTypeCode,
        String finTypeName,
        Map<Long, ChargeItemDefinition> definitionById,
        Map<Long, MedicationDispense> dispenseById,
        boolean refund) {
        DailySettlementMedicationChargeLineDto line = new DailySettlementMedicationChargeLineDto();
        line.setPaymentId(payment.getId());
        line.setPaymentNo(payment.getPaymentNo());
        line.setChargeItemId(chargeItem.getId());
        line.setFinTypeCode(finTypeCode);
        line.setFinTypeName(finTypeName);
        line.setQuantity(chargeItem.getQuantityValue());
        BigDecimal amount = chargeItem.getTotalPrice() == null ? BigDecimal.ZERO : chargeItem.getTotalPrice().abs();
        if (refund) {
            amount = amount.negate();
        }
        line.setAmount(amount);
        line.setBillDate(payment.getBillDate());
        ChargeItemDefinition definition = definitionById.get(chargeItem.getDefinitionId());
        if (definition != null && !StringUtils.isEmpty(definition.getChargeName())) {
            line.setItemName(definition.getChargeName().trim());
        } else {
            line.setItemName("—");
        }
        Long dispenseId = null;
        if (TableNameConstants.MED_MEDICATION_DISPENSE.equals(chargeItem.getDispenseTable())
            && chargeItem.getDispenseId() != null) {
            dispenseId = chargeItem.getDispenseId();
        }
        line.setDispenseId(dispenseId);
        MedicationDispense dispense = dispenseId == null ? null : dispenseById.get(dispenseId);
        if (dispense == null || dispense.getStatusEnum() == null) {
            line.setDispenseStatus(null);
            line.setDispenseStatusDisplay("未发药");
            line.setDispenseTime(null);
            line.setUndispensed(true);
            return line;
        }
        DispenseStatus status = dispense.getStatusEnum();
        line.setDispenseStatus(status.getCode());
        line.setDispenseStatusDisplay(status.getDisplay());
        line.setDispenseTime(dispense.getDispenseTime());
        line.setUndispensed(!DispenseStatus.COMPLETED.equals(status));
        return line;
    }

    private boolean isDispenseRevenueEligible(
        DailySettlementMedicationChargeLineDto line,
        LocalDateTime from,
        LocalDateTime to) {
        if (line == null || Boolean.TRUE.equals(line.getUndispensed())) {
            return false;
        }
        if (!DispenseStatus.COMPLETED.getCode().equals(line.getDispenseStatus())) {
            return false;
        }
        LocalDateTime dispenseTime = line.getDispenseTime();
        if (dispenseTime == null || from == null || to == null) {
            return false;
        }
        return !dispenseTime.isBefore(from) && !dispenseTime.isAfter(to);
    }

    private boolean isDrugFinType(String finTypeCode) {
        return finTypeCode != null && DRUG_FIN_TYPE_CODES.contains(finTypeCode);
    }

    private Map<Long, String> safeMapFinTypes(List<ChargeItem> chargeItems) {
        List<ChargeItem> priced = new ArrayList<>();
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem == null
                || chargeItem.getId() == null
                || chargeItem.getDefinitionId() == null
                || chargeItem.getUnitPrice() == null
                || chargeItem.getTotalPrice() == null) {
                continue;
            }
            priced.add(chargeItem);
        }
        if (priced.isEmpty()) {
            return Map.of();
        }
        try {
            return this.dailySettlementFinTypeAmountLoader.mapFinTypeCodeByChargeItemId(priced);
        } catch (RuntimeException ignored) {
            // 个别定价异常时逐条容错，避免整页药品 TAB 失败
            Map<Long, String> result = new LinkedHashMap<>();
            for (ChargeItem chargeItem : priced) {
                try {
                    Map<Long, String> one =
                        this.dailySettlementFinTypeAmountLoader.mapFinTypeCodeByChargeItemId(List.of(chargeItem));
                    result.putAll(one);
                } catch (RuntimeException ignoredOne) {
                    // skip
                }
            }
            return result;
        }
    }

    private List<ChargeItem> flattenChargeItems(Map<Long, List<ChargeItem>> chargeItemsByPaymentId) {
        List<ChargeItem> result = new ArrayList<>();
        if (chargeItemsByPaymentId == null) {
            return result;
        }
        for (List<ChargeItem> items : chargeItemsByPaymentId.values()) {
            if (items == null) {
                continue;
            }
            result.addAll(items);
        }
        return result;
    }

    private Map<Long, ChargeItemDefinition> loadDefinitions(List<ChargeItem> chargeItems) {
        Set<Long> definitionIds = new LinkedHashSet<>();
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem != null && chargeItem.getDefinitionId() != null) {
                definitionIds.add(chargeItem.getDefinitionId());
            }
        }
        if (definitionIds.isEmpty()) {
            return Map.of();
        }
        return this.chargeItemDefinitionQueryService.findAllDefinitionsByIdIn(definitionIds);
    }

    private Map<Long, MedicationDispense> loadDispenses(List<ChargeItem> chargeItems) {
        Set<Long> dispenseIds = new LinkedHashSet<>();
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem == null) {
                continue;
            }
            if (TableNameConstants.MED_MEDICATION_DISPENSE.equals(chargeItem.getDispenseTable())
                && chargeItem.getDispenseId() != null) {
                dispenseIds.add(chargeItem.getDispenseId());
            }
        }
        Map<Long, MedicationDispense> result = new LinkedHashMap<>();
        if (dispenseIds.isEmpty()) {
            return result;
        }
        List<MedicationDispense> rows = this.medicationDispenseQueryService.listMedicationDispensesByIds(dispenseIds);
        if (rows == null) {
            return result;
        }
        for (MedicationDispense row : rows) {
            if (row != null && row.getId() != null) {
                result.put(row.getId(), row);
            }
        }
        return result;
    }

    private Map<String, String> buildFinTypeNameMap(List<OptionDto> finTypeOptions) {
        Map<String, String> map = new LinkedHashMap<>();
        if (finTypeOptions == null) {
            return map;
        }
        for (OptionDto option : finTypeOptions) {
            if (option == null || StringUtils.isEmpty(option.getValue())) {
                continue;
            }
            map.put(option.getValue(), option.getLabel());
        }
        return map;
    }

    private DailySettlementMedicationRevenueSummaryDto buildSummary(
        BigDecimal total,
        Map<String, BigDecimal> byFinType,
        Map<String, String> finTypeNameByCode) {
        DailySettlementMedicationRevenueSummaryDto summary = new DailySettlementMedicationRevenueSummaryDto();
        summary.setTotalMedicationIncome(total);
        List<DailySettlementMedicationFinTypeAmountDto> rows = new ArrayList<>();
        for (String code : DRUG_FIN_TYPE_CODES) {
            BigDecimal amount = byFinType.get(code);
            if (amount == null) {
                continue;
            }
            DailySettlementMedicationFinTypeAmountDto row = new DailySettlementMedicationFinTypeAmountDto();
            row.setFinTypeCode(code);
            row.setFinTypeName(finTypeNameByCode.getOrDefault(code, code));
            row.setAmount(amount);
            rows.add(row);
        }
        summary.setFinTypeAmounts(rows);
        return summary;
    }

    private DailySettlementMedicationRevenueSummaryDto emptySummary() {
        DailySettlementMedicationRevenueSummaryDto summary = new DailySettlementMedicationRevenueSummaryDto();
        summary.setTotalMedicationIncome(BigDecimal.ZERO);
        summary.setFinTypeAmounts(List.of());
        return summary;
    }

    private DailySettlementMedicationUndispensedAlertDto emptyAlert() {
        DailySettlementMedicationUndispensedAlertDto alert = new DailySettlementMedicationUndispensedAlertDto();
        alert.setCount(0);
        alert.setItems(List.of());
        return alert;
    }
}
