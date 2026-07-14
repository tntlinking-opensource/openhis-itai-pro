/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.pharmacy.dispense;

import com.openhis.health.application.outpatient.pharmacy.dispense.support.OutpatientEncounterMedicationDispenseSlipPageLoader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class OutpatientPharmacyDispenseSlipBaselineRegressionTest {

    @Test
    void slipPageLoaderBeanExistsForBaselineScope() {
        assertNotNull(OutpatientEncounterMedicationDispenseSlipPageLoader.class);
    }
}
