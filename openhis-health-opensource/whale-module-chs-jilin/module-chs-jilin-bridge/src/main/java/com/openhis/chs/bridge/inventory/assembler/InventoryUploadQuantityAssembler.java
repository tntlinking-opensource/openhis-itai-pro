/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.inventory.assembler;

import com.openhis.chs.domain.share.enums.TrdnFlag;
import static com.openhis.whale.core.application.util.StringUtils.isEmpty;
import com.openhis.chs.domain.share.constants.ChsInventoryConstants;
import com.openhis.health.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.openhis.health.domain.medication.medicationdispense.entity.MedicationDispense;
import com.openhis.health.domain.share.product.MedicationProductUnitConversionSource;
import com.openhis.health.domain.workflow.inventoryitem.support.MedicationProductQuantityToSplitUnitConverter;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 3505/3506 销售数量 {@code sel_retn_cnt} 换算静态辅助（无 IO、无 Spring 依赖）。
 *
 * <p>将发药业务数量按药品单位换算为医保平台要求的最小包装/拆零单位数量（见进销存规范 §4.5）。
 * 换算失败时回落原始业务数量。</p>
 */
public final class InventoryUploadQuantityAssembler {

    private InventoryUploadQuantityAssembler() {
    }

    public static BigDecimal resolveSelRetnCnt(
        MedicationDefinition medication,
        MedicationDispense dispense,
        String trdnFlag
    ) {
        BigDecimal businessQty = resolveBusinessQuantity(dispense);
        if (medication == null || dispense == null || businessQty == null) {
            return businessQty;
        }
        if (isEmpty(dispense.getUnitCode())) {
            return businessQty;
        }
        try {
            MedicationProductUnitConversionSource source = toConversionSource(medication);
            BigDecimal splitQty = MedicationProductQuantityToSplitUnitConverter.medicationQuantityToSplitUnit(
                source, dispense.getUnitCode(), businessQty);
            if (TrdnFlag.SPLIT.getCode().equals(trdnFlag)) {
                return splitQty;
            }
            if (medication.getPackageUnitRatio() != null
                && medication.getPackageUnitRatio().compareTo(BigDecimal.ZERO) > 0) {
                return splitQty.divide(medication.getPackageUnitRatio(), 4, RoundingMode.HALF_UP);
            }
            return businessQty;
        } catch (RuntimeException ex) {
            return businessQty;
        }
    }

    /**
     * 解析发药业务数量（优先发药数量，回落申请数量）。
     *
     * @param dispense 发药行
     * @return 业务数量绝对值
     */
    private static BigDecimal resolveBusinessQuantity(MedicationDispense dispense) {
        if (dispense.getDispenseQuantity() != null) {
            return dispense.getDispenseQuantity().abs();
        }
        if (dispense.getQuantity() != null) {
            return dispense.getQuantity().abs();
        }
        return null;
    }

    /**
     * 将药品定义转换为数量单位换算源。
     *
     * @param medication 药品定义
     * @return 单位换算源
     */
    private static MedicationProductUnitConversionSource toConversionSource(MedicationDefinition medication) {
        MedicationProductUnitConversionSource source = new MedicationProductUnitConversionSource();
        source.setSplitUnitCode(medication.getSplitUnitCode());
        source.setDoseUnitCode(medication.getDoseUnitCode());
        source.setDoseUnitRatio(medication.getDoseUnitRatio());
        source.setPurchaseUnitCode(medication.getPurchaseUnitCode());
        source.setPurchaseUnitRatio(medication.getPurchaseUnitRatio());
        source.setStockUnitCode(medication.getStockUnitCode());
        source.setStockUnitRatio(medication.getStockUnitRatio());
        source.setPackageUnitCode(medication.getPackageUnitCode());
        source.setPackageUnitRatio(medication.getPackageUnitRatio());
        return source;
    }
}
