/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.advice;

import com.zxplat.domain.share.product.dto.ProductDefinitionDisplayDto;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link StandingOrderPackageBatchEligibilitySupport} 资格判定单测。
 */
class StandingOrderPackageBatchEligibilitySupportTest {

    @Test
    void eligible_longTerm_oral_packageDistinct() {
        ProductDefinitionDisplayDto product = new ProductDefinitionDisplayDto();
        product.setPackageUnitCode("box");
        product.setSplitUnitCode("tablet");
        product.setInjectFlag(Boolean.FALSE);
        assertTrue(StandingOrderPackageBatchEligibilitySupport.isEligibleWithoutSplittableSwitch(
            "LONG_TERM",
            "1",
            Map.of("1", "口服"),
            product));
    }

    @Test
    void eligible_oralExtendedRelease() {
        ProductDefinitionDisplayDto product = new ProductDefinitionDisplayDto();
        product.setPackageUnitCode("box");
        product.setSplitUnitCode("tablet");
        assertTrue(StandingOrderPackageBatchEligibilitySupport.isEligibleWithoutSplittableSwitch(
            "LONG_TERM",
            "2",
            Map.of("2", "口服缓释"),
            product));
    }

    @Test
    void notEligible_samePackageAndSplitUnit() {
        ProductDefinitionDisplayDto product = new ProductDefinitionDisplayDto();
        product.setPackageUnitCode("tablet");
        product.setSplitUnitCode("tablet");
        assertFalse(StandingOrderPackageBatchEligibilitySupport.isEligibleWithoutSplittableSwitch(
            "LONG_TERM",
            "1",
            Map.of("1", "口服"),
            product));
    }

    @Test
    void notEligible_injectionMethod() {
        ProductDefinitionDisplayDto product = new ProductDefinitionDisplayDto();
        product.setPackageUnitCode("box");
        product.setSplitUnitCode("tablet");
        assertFalse(StandingOrderPackageBatchEligibilitySupport.isEligibleWithoutSplittableSwitch(
            "LONG_TERM",
            "9",
            Map.of("9", "静脉注射"),
            product));
    }

    @Test
    void notEligible_infusionCatalog_evenIfOralMethod() {
        ProductDefinitionDisplayDto product = new ProductDefinitionDisplayDto();
        product.setPackageUnitCode("box");
        product.setSplitUnitCode("bottle");
        product.setInjectFlag(Boolean.TRUE);
        assertTrue(StandingOrderPackageBatchEligibilitySupport.isInfusionCatalog(product));
        assertFalse(StandingOrderPackageBatchEligibilitySupport.isEligibleWithoutSplittableSwitch(
            "LONG_TERM",
            "1",
            Map.of("1", "口服"),
            product));
    }

    @Test
    void minSplitReuse_skipInfusionUnlessSubcutaneous() {
        ProductDefinitionDisplayDto infusion = new ProductDefinitionDisplayDto();
        infusion.setInjectFlag(Boolean.TRUE);
        Map<String, String> labels = Map.of(
            "401", "皮下注射",
            "9", "静脉注射",
            "1", "口服");
        assertTrue(StandingOrderPackageBatchEligibilitySupport.shouldSkipMinSplitReuseForOpenDiscardInfusion(
            infusion, "9", labels));
        assertFalse(StandingOrderPackageBatchEligibilitySupport.shouldSkipMinSplitReuseForOpenDiscardInfusion(
            infusion, "401", labels));
        ProductDefinitionDisplayDto oral = new ProductDefinitionDisplayDto();
        oral.setInjectFlag(Boolean.FALSE);
        assertFalse(StandingOrderPackageBatchEligibilitySupport.shouldSkipMinSplitReuseForOpenDiscardInfusion(
            oral, "1", labels));
    }
}
