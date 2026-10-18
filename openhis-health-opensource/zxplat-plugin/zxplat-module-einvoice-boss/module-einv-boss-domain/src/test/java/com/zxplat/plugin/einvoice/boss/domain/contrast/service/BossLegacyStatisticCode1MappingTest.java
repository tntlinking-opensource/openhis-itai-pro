/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.einvoice.boss.domain.contrast.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BossLegacyStatisticCode1MappingTest {

    @Test
    void ambulatory_mapsTreatmentFeeToYlmz04() {
        assertEquals("YLMZ04", BossLegacyStatisticCode1Mapping.toOutboundChargeCode("AMB", "YLZY05"));
    }

    @Test
    void inpatient_keepsStatisticCode() {
        assertEquals("YLZY05", BossLegacyStatisticCode1Mapping.toOutboundChargeCode("IMP", "YLZY05"));
    }

    @Test
    void registration_keepsYlmz11() {
        assertEquals("YLMZ11", BossLegacyStatisticCode1Mapping.toOutboundChargeCode("REG", "YLMZ11"));
    }

    @Test
    void needsCode1_onlyAmbAndReg() {
        assertTrue(BossLegacyStatisticCode1Mapping.needsCode1("AMB"));
        assertTrue(BossLegacyStatisticCode1Mapping.needsCode1("REG"));
        assertFalse(BossLegacyStatisticCode1Mapping.needsCode1("IMP"));
        assertFalse(BossLegacyStatisticCode1Mapping.needsCode1("COM"));
    }
}
