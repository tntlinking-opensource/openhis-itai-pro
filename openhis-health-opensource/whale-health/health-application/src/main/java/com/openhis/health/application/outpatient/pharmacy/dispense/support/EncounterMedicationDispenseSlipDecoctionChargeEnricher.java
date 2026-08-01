/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.pharmacy.dispense.support;

import com.openhis.health.application.outpatient.pharmacy.dispense.dto.EncounterMedicationDispenseSlipRowDto;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.share.constants.DoctorStationTcmConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.ChargeItemStatus;
import com.openhis.health.domain.share.utils.MonetaryAmountSupport;
import com.openhis.health.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.openhis.whale.core.application.util.StringUtils.trimToEmpty;
import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * 门诊发药 slip：按处方号回填同方代煎费（IF-OPP-03 展示；锚定代煎 companion ChargeItem）。
 */
@Component
@RequiredArgsConstructor
public class EncounterMedicationDispenseSlipDecoctionChargeEnricher {

    private final ActivityDefinitionQueryService activityDefinitionQueryService;
    private final ChargeItemQueryService chargeItemQueryService;

    /**
     * 为中药 slip 行写入同处方号代煎费金额/数量（方头未代煎或无费用时仅保留 {@code sufferingFlag}）。
     *
     * @param encounterId 就诊主键；空则跳过
     * @param rows        已装配 slip 行（就地回填）
     */
    public void enrichDecoctionChargeByPrescriptionNo(
        Long encounterId,
        List<EncounterMedicationDispenseSlipRowDto> rows
    ) {
        // Gate：无就诊或无行时不查费
        if (encounterId == null || rows == null || rows.isEmpty()) {
            return;
        }
        // Pre：仅中药且有处方号的方需要代煎费回填
        Set<String> herbalPrescriptionNos = collectHerbalPrescriptionNos(rows);
        if (herbalPrescriptionNos.isEmpty()) {
            return;
        }
        // Gate：本页无任何勾选代煎的行时跳过费用查询（仍可能仅有 sufferingFlag 展示需求时由方头字段承担）
        if (!hasSufferingHerbalRow(rows)) {
            return;
        }
        // Pre：解析租户「代煎中药」诊疗项目；未配置则静默跳过（与签发 Gate 分工）
        Long decoctionActivityId = resolveDecoctionActivityIdOrNull();
        if (decoctionActivityId == null) {
            return;
        }
        // Pre：按处方号汇总有效代煎收费明细
        Map<String, DecoctionChargeAggregate> amountByPrescriptionNo =
            loadDecoctionChargeByPrescriptionNo(encounterId, decoctionActivityId, herbalPrescriptionNos);
        if (amountByPrescriptionNo.isEmpty()) {
            return;
        }
        // Asm：同方各行写入一致的代煎费展示字段
        for (EncounterMedicationDispenseSlipRowDto row : rows) {
            if (row == null || !Boolean.TRUE.equals(row.getTcmFlag())) {
                continue;
            }
            String prescriptionNo = trimToNull(row.getPrescriptionNo());
            if (prescriptionNo == null) {
                continue;
            }
            DecoctionChargeAggregate aggregate = amountByPrescriptionNo.get(prescriptionNo);
            if (aggregate == null) {
                continue;
            }
            row.setDecoctionChargeAmount(aggregate.totalAmount());
            row.setDecoctionChargeQuantity(aggregate.quantity());
            row.setDecoctionChargeUnitCode(aggregate.unitCode());
            row.setDecoctionActivityName(DoctorStationTcmConstants.DECOCTION_ACTIVITY_NAME);
        }
    }

    /**
     * 本页是否存在勾选代煎的中药行（无则无需查代煎收费）。
     *
     * @param rows slip 行
     * @return {@code true} 存在代煎方
     */
    private static boolean hasSufferingHerbalRow(List<EncounterMedicationDispenseSlipRowDto> rows) {
        for (EncounterMedicationDispenseSlipRowDto row : rows) {
            if (row != null && Boolean.TRUE.equals(row.getTcmFlag()) && Boolean.TRUE.equals(row.getSufferingFlag())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 收集本页中药饮片且带处方号的处方号集合。
     *
     * @param rows slip 行
     * @return 处方号集合；无则空
     */
    private static Set<String> collectHerbalPrescriptionNos(List<EncounterMedicationDispenseSlipRowDto> rows) {
        Set<String> prescriptionNos = new HashSet<>();
        for (EncounterMedicationDispenseSlipRowDto row : rows) {
            if (row == null || !Boolean.TRUE.equals(row.getTcmFlag())) {
                continue;
            }
            String prescriptionNo = trimToNull(row.getPrescriptionNo());
            if (prescriptionNo != null) {
                prescriptionNos.add(prescriptionNo);
            }
        }
        return prescriptionNos;
    }

    /**
     * 解析代煎诊疗项目主键；未配置或名称不唯一时返回 {@code null}（发药读侧不阻断）。
     *
     * @return 代煎 activity 主键；未配置时 null
     */
    private Long resolveDecoctionActivityIdOrNull() {
        try {
            return activityDefinitionQueryService.requireIdByExactName(
                DoctorStationTcmConstants.DECOCTION_ACTIVITY_NAME);
        } catch (RuntimeException ex) {
            // 租户未维护代煎项目时发药列表仍可展示药材行
            return null;
        }
    }

    /**
     * 按就诊 + 代煎 activity 产品锚点加载收费明细，筛出有效代煎费用并按方汇总。
     *
     * @param encounterId            就诊主键
     * @param decoctionActivityId    代煎 activity 主键
     * @param herbalPrescriptionNos  本页中药处方号
     * @return 处方号 → 代煎费汇总
     */
    private Map<String, DecoctionChargeAggregate> loadDecoctionChargeByPrescriptionNo(
        Long encounterId,
        Long decoctionActivityId,
        Set<String> herbalPrescriptionNos
    ) {
        // Pre：只拉代煎产品费用行，禁止 listChargeItemsByEncounterId 全就诊扫表
        List<ChargeItem> chargeItems = chargeItemQueryService.listChargeItemsByEncounterAndProduct(
            encounterId, TableNameConstants.WKF_ACTIVITY_DEFINITION, decoctionActivityId);
        Map<String, DecoctionChargeAggregate> result = new HashMap<>();
        for (ChargeItem chargeItem : chargeItems) {
            if (!isEffectiveDecoctionChargeItem(chargeItem, decoctionActivityId)) {
                continue;
            }
            String prescriptionNo = trimToNull(chargeItem.getPrescriptionNo());
            if (prescriptionNo == null || !herbalPrescriptionNos.contains(prescriptionNo)) {
                continue;
            }
            BigDecimal lineAmount = chargeItem.getTotalPrice();
            if (lineAmount == null) {
                lineAmount = BigDecimal.ZERO;
            }
            DecoctionChargeAggregate current = result.get(prescriptionNo);
            if (current == null) {
                result.put(
                    prescriptionNo,
                    new DecoctionChargeAggregate(
                        MonetaryAmountSupport.scaleDisplay(lineAmount),
                        chargeItem.getQuantityValue(),
                        trimToNull(chargeItem.getQuantityUnit())));
                continue;
            }
            BigDecimal mergedAmount = MonetaryAmountSupport.scaleDisplay(
                current.totalAmount().add(lineAmount));
            BigDecimal mergedQuantity = current.quantity();
            if (mergedQuantity == null) {
                mergedQuantity = chargeItem.getQuantityValue();
            } else if (chargeItem.getQuantityValue() != null) {
                mergedQuantity = mergedQuantity.add(chargeItem.getQuantityValue());
            }
            String unitCode = current.unitCode();
            if (unitCode == null) {
                unitCode = trimToNull(chargeItem.getQuantityUnit());
            }
            result.put(prescriptionNo, new DecoctionChargeAggregate(mergedAmount, mergedQuantity, unitCode));
        }
        return result;
    }

    /**
     * 判定是否为可展示的代煎收费明细（未删、未退完、产品锚定代煎 activity）。
     *
     * @param chargeItem          收费明细
     * @param decoctionActivityId 代煎 activity 主键
     * @return {@code true} 纳入汇总
     */
    private static boolean isEffectiveDecoctionChargeItem(ChargeItem chargeItem, Long decoctionActivityId) {
        if (chargeItem == null || Boolean.TRUE.equals(chargeItem.getIsDeleted())) {
            return false;
        }
        if (!TableNameConstants.WKF_ACTIVITY_DEFINITION.equals(trimToEmpty(chargeItem.getProductTable()))) {
            return false;
        }
        if (!decoctionActivityId.equals(chargeItem.getProductId())) {
            return false;
        }
        String status = trimToEmpty(chargeItem.getStatusEnum());
        // 已退费不再展示；草稿/待收/待结/已结/退款中均回显金额
        if (ChargeItemStatus.REFUNDED.getCode().equals(status)) {
            return false;
        }
        if (ChargeItemStatus.DRAFT.getCode().equals(status)
            || ChargeItemStatus.PLANNED.getCode().equals(status)
            || ChargeItemStatus.PENDING_SETTLEMENT.getCode().equals(status)
            || ChargeItemStatus.SETTLED.getCode().equals(status)
            || ChargeItemStatus.REFUNDING.getCode().equals(status)) {
            return true;
        }
        return false;
    }

    /**
     * 同方代煎费汇总（金额 + 数量/单位）。
     *
     * @param totalAmount 金额合计
     * @param quantity    数量（付数）
     * @param unitCode    数量单位码
     */
    private record DecoctionChargeAggregate(BigDecimal totalAmount, BigDecimal quantity, String unitCode) {
    }
}
