/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.inventoryitem.support;

import com.openhis.health.domain.share.product.MedicationProductUnitConversionSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 发药扣库单位换算：开方单位一律 × 五类拆零倍数；盒与追溯码同码时不得用贴码倍数 1。
 */
class MedicationProductQuantityToSplitUnitConverterTest {

    /**
     * MD00000856 同类：盒=30 片，追溯码单位也是盒但倍数填 1（一盒一码）。10 盒须落到 300 片。
     */
    @Test
    void boxDispenseUsesPackageRatioWhenTraceUnitIsAlsoBoxWithRatioOne() {
        MedicationProductUnitConversionSource product = new MedicationProductUnitConversionSource();
        product.setSplitUnitCode("TAB");
        product.setPurchaseUnitCode("BOX");
        product.setPurchaseUnitRatio(new BigDecimal("30"));
        product.setStockUnitCode("BOX");
        product.setStockUnitRatio(new BigDecimal("30"));
        product.setPackageUnitCode("BOX");
        product.setPackageUnitRatio(new BigDecimal("30"));
        product.setTraceUnitCode("BOX");
        product.setTraceUnitRatio(BigDecimal.ONE);

        BigDecimal splitQty = MedicationProductQuantityToSplitUnitConverter.medicationQuantityToSplitUnit(
            product, "BOX", new BigDecimal("10"));

        assertEquals(0, new BigDecimal("300").compareTo(splitQty));
    }

    /**
     * 开方单位已是拆零：倍数 1，10 片仍落到 10 片账面（仍走转换，不是跳过）。
     */
    @Test
    void splitUnitStillConvertsWithRatioOne() {
        MedicationProductUnitConversionSource product = new MedicationProductUnitConversionSource();
        product.setSplitUnitCode("TAB");
        product.setPackageUnitCode("BOX");
        product.setPackageUnitRatio(new BigDecimal("30"));

        BigDecimal splitQty = MedicationProductQuantityToSplitUnitConverter.medicationQuantityToSplitUnit(
            product, "TAB", new BigDecimal("10"));

        assertEquals(0, new BigDecimal("10").compareTo(splitQty));
    }

    /**
     * 贴码单位游离于五类时，仍按追溯码倍数换算。
     */
    @Test
    void freeTraceUnitStillConvertsByTraceRatio() {
        MedicationProductUnitConversionSource product = new MedicationProductUnitConversionSource();
        product.setSplitUnitCode("TAB");
        product.setPackageUnitCode("BOX");
        product.setPackageUnitRatio(new BigDecimal("30"));
        product.setTraceUnitCode("MIDPACK");
        product.setTraceUnitRatio(new BigDecimal("10"));

        BigDecimal splitQty = MedicationProductQuantityToSplitUnitConverter.medicationQuantityToSplitUnit(
            product, "MIDPACK", new BigDecimal("2"));

        assertEquals(0, new BigDecimal("20").compareTo(splitQty));
    }
}
