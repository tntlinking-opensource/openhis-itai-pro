/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.settlement.support;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class SettlementRetailUploadDiffSupportTest {

    @Test
    void of_retailLessThanUpload_negativeDiff() {
        SettlementRetailUploadDiffSupport.RetailUploadDiffAmounts out =
            SettlementRetailUploadDiffSupport.of(
                new BigDecimal("27.54"), new BigDecimal("27.60"));

        assertThat(out.retailMedfeeSumamt()).isEqualByComparingTo("27.54");
        assertThat(out.uploadMedfeeSumamt()).isEqualByComparingTo("27.60");
        assertThat(out.retailUploadDiffAmt()).isEqualByComparingTo("-0.06");
    }

    @Test
    void of_nullInputs_treatedAsZero() {
        SettlementRetailUploadDiffSupport.RetailUploadDiffAmounts out =
            SettlementRetailUploadDiffSupport.of(null, null);

        assertThat(out.retailMedfeeSumamt()).isEqualByComparingTo("0.00");
        assertThat(out.uploadMedfeeSumamt()).isEqualByComparingTo("0.00");
        assertThat(out.retailUploadDiffAmt()).isEqualByComparingTo("0.00");
    }
}
