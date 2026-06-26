/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.insurance.inventory.support;

import com.openhis.chs.domain.inventory.ChsInventoryConstants;
import com.openhis.health.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.openhis.health.domain.medication.medicationdispense.entity.MedicationDispense;
import com.openhis.health.domain.share.product.MedicationProductUnitConversionSource;
import com.openhis.health.domain.workflow.inventoryitem.support.MedicationProductQuantityToSplitUnitConverter;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** 3505/3506 {@code sel_retn_cnt}：换算为最小包装/拆零口径（§4.5）。 */
public final class InventoryUploadQuantitySupport {

    private InventoryUploadQuantitySupport() {
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
        if (StringUtils.isBlank(dispense.getUnitCode())) {
            return businessQty;
        }
        try {
            MedicationProductUnitConversionSource source = toConversionSource(medication);
            BigDecimal splitQty = MedicationProductQuantityToSplitUnitConverter.medicationQuantityToSplitUnit(
                source, dispense.getUnitCode(), businessQty);
            if (ChsInventoryConstants.TRDN_FLAG_SPLIT.equals(trdnFlag)) {
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

    private static BigDecimal resolveBusinessQuantity(MedicationDispense dispense) {
        if (dispense.getDispenseQuantity() != null) {
            return dispense.getDispenseQuantity().abs();
        }
        if (dispense.getQuantity() != null) {
            return dispense.getQuantity().abs();
        }
        return null;
    }

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
