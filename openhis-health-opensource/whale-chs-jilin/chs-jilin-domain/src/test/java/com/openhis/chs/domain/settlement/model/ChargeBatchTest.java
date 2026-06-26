/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.settlement.model;

import com.openhis.chs.domain.outpatient.entity.YbClinicPreSettle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChargeBatchTest {

    @Test
    void fromClinicPreSettle_usesChrgBchnoAsBatchKey() {
        YbClinicPreSettle pre = new YbClinicPreSettle();
        pre.setId(100L);
        pre.setChrgBchno("OP20260620001");
        pre.setMdtrtId("MD001");
        pre.setPaymentDraftId(200L);
        pre.setAccountId(300L);

        ChargeBatch batch = ChargeBatch.fromClinicPreSettle(pre);

        assertEquals("OP20260620001", batch.batchKey());
        assertEquals("MD001", batch.mdtrtId().value());
        assertEquals(100L, batch.preSettleId());
        assertEquals(200L, batch.paymentDraftId());
        assertEquals(300L, batch.accountId());
    }

    @Test
    void fromPreSettle_inpatientUsesIpPrefixBatchKey() {
        YbClinicPreSettle pre = new YbClinicPreSettle() {
            @Override
            public String resolveBatchKey() {
                return "IP888";
            }
        };
        pre.setId(888L);
        pre.setMdtrtId("MD888");

        ChargeBatch batch = ChargeBatch.fromPreSettle(pre, 900L, null);

        assertEquals("IP888", batch.batchKey());
        assertEquals(888L, batch.preSettleId());
        assertEquals(900L, batch.paymentDraftId());
    }

    @Test
    void fromClinicPreSettle_rejectsMissingBatchKey() {
        YbClinicPreSettle pre = new YbClinicPreSettle();
        pre.setMdtrtId("MD001");
        assertThrows(IllegalArgumentException.class, () -> ChargeBatch.fromClinicPreSettle(pre));
    }
}
