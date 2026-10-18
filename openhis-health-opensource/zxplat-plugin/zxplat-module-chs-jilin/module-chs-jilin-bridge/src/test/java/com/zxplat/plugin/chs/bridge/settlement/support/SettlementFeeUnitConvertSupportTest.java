/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.settlement.support;

import com.zxplat.plugin.chs.domain.catalog.support.NationalCatalogPackHint;
import com.zxplat.domain.medication.medicationdefinition.entity.MedicationDefinition;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class SettlementFeeUnitConvertSupportTest {

    @Test
    void catalogHint_convertsBoxToTabletUsingMinPacCnt() {
        MedicationDefinition med = hospitalMed("1", "2", new BigDecimal("30"));
        NationalCatalogPackHint hint = NationalCatalogPackHint.of(
            "XC08CAZ067A001010702697", "7", "盒", "片", "片");

        SettlementFeeUnitConvertSupport.FeeUploadQtyPrice out =
            SettlementFeeUnitConvertSupport.toSplitUnit(
                med, "2", new BigDecimal("2"), new BigDecimal("13.77"), hint);

        // 2 盒 × 7 片 → 14；单价 = 27.54 / 14 = 1.967143（6 位），总额仍 27.54
        assertThat(out.cnt()).isEqualByComparingTo("14");
        assertThat(out.pric()).isEqualByComparingTo("1.967143");
        assertThat(out.detItemFeeSumamt()).isEqualByComparingTo("27.54");
        assertThat(out.unitCode()).isEqualTo("1");
    }

    @Test
    void catalogHint_pricesByPack_skipsHospitalSplit() {
        MedicationDefinition med = hospitalMed("1", "2", new BigDecimal("30"));
        NationalCatalogPackHint hint = NationalCatalogPackHint.of(
            "CODE", "30", "盒", "片", "盒");

        SettlementFeeUnitConvertSupport.FeeUploadQtyPrice out =
            SettlementFeeUnitConvertSupport.toSplitUnit(
                med, "2", new BigDecimal("2"), new BigDecimal("13.77"), hint);

        assertThat(out.cnt()).isEqualByComparingTo("2");
        assertThat(out.pric()).isEqualByComparingTo("13.77");
        assertThat(out.detItemFeeSumamt()).isEqualByComparingTo("27.54");
        assertThat(out.unitCode()).isEqualTo("2");
    }

    @Test
    void catalogHint_alreadyPricingUnit_noConvert() {
        MedicationDefinition med = hospitalMed("1", "2", new BigDecimal("30"));
        NationalCatalogPackHint hint = NationalCatalogPackHint.of(
            "CODE", "30", "盒", "片", "片");

        SettlementFeeUnitConvertSupport.FeeUploadQtyPrice out =
            SettlementFeeUnitConvertSupport.toSplitUnit(
                med, "1", new BigDecimal("14"), new BigDecimal("0.46"), hint);

        assertThat(out.cnt()).isEqualByComparingTo("14");
        assertThat(out.pric()).isEqualByComparingTo("0.46");
        assertThat(out.detItemFeeSumamt()).isEqualByComparingTo("6.44");
    }

    @Test
    void missingCatalog_fallsBackToHospitalRatio() {
        MedicationDefinition med = hospitalMed("1", "2", new BigDecimal("30"));

        SettlementFeeUnitConvertSupport.FeeUploadQtyPrice out =
            SettlementFeeUnitConvertSupport.toSplitUnit(
                med, "2", new BigDecimal("2"), new BigDecimal("13.77"), null);

        // 2 盒 × 30 → 60；单价 = 27.54 / 60 = 0.459000，总额仍 27.54（不再因两位价变 27.60）
        assertThat(out.cnt()).isEqualByComparingTo("60");
        assertThat(out.pric()).isEqualByComparingTo("0.459");
        assertThat(out.detItemFeeSumamt()).isEqualByComparingTo("27.54");
        assertThat(out.unitCode()).isEqualTo("1");
    }

    @Test
    void guideExample_sixDecimalPricKeepsRetailTotal() {
        // 文档示例：1 盒 10.33 / 7 片 → pric 1.475714，det 仍 10.33
        MedicationDefinition med = hospitalMed("1", "2", new BigDecimal("7"));
        NationalCatalogPackHint hint = NationalCatalogPackHint.of(
            "GUIDE", "7", "盒", "片", "片");

        SettlementFeeUnitConvertSupport.FeeUploadQtyPrice out =
            SettlementFeeUnitConvertSupport.toSplitUnit(
                med, "2", BigDecimal.ONE, new BigDecimal("10.33"), hint);

        assertThat(out.cnt()).isEqualByComparingTo("7");
        assertThat(out.pric()).isEqualByComparingTo("1.475714");
        assertThat(out.detItemFeeSumamt()).isEqualByComparingTo("10.33");
    }

    private static MedicationDefinition hospitalMed(
        String splitUnit, String packageUnit, BigDecimal packageRatio) {
        MedicationDefinition med = new MedicationDefinition();
        med.setSplitUnitCode(splitUnit);
        med.setPackageUnitCode(packageUnit);
        med.setPackageUnitRatio(packageRatio);
        return med;
    }
}

