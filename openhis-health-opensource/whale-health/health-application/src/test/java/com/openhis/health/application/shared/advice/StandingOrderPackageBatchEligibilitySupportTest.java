/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.advice;

import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
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
}
