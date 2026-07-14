/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.settlement.service;

import com.openhis.chs.domain.inpatient.entity.YbInpatientFeeUpload;
import com.openhis.chs.domain.outpatient.entity.YbClinicFeeUpload;
import com.openhis.chs.domain.settlement.SettlementInvariantException;
import com.openhis.chs.domain.settlement.YbFeeUploadStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChargeBatchLifecycleTest {

    private final ChargeBatchLifecycle lifecycle = new ChargeBatchLifecycle();

    @Test
    void settledClinicFeeCannotBeRevokedBy2205() {
        YbClinicFeeUpload row = new YbClinicFeeUpload();
        row.setUploadStatus(YbFeeUploadStatus.SETTLED);

        assertThrows(SettlementInvariantException.class, () -> lifecycle.markClinicFeeRevoked(row));
    }

    @Test
    void uploadedClinicFeeCanBeMarkedSettled() {
        YbClinicFeeUpload row = new YbClinicFeeUpload();
        row.setUploadStatus(YbFeeUploadStatus.UPLOADED);

        lifecycle.markClinicFeeSettled(row);
        assertEquals(YbFeeUploadStatus.SETTLED, row.getUploadStatus());
    }

    @Test
    void settleRevokedAllowsSettledRow() {
        YbInpatientFeeUpload row = new YbInpatientFeeUpload();
        row.setUploadStatus(YbFeeUploadStatus.SETTLED);

        lifecycle.markInpatientFeeSettleRevoked(row);
        assertEquals(YbFeeUploadStatus.REVOKED, row.getUploadStatus());
    }
}
