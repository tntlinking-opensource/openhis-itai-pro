/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.reconciliation.support;

import com.openhis.chs.domain.reconciliation.result.ReconDetailRowResult;
import com.openhis.chs.domain.settlement.constants.ChsSettleEncounterClass;
import com.openhis.chs.domain.settlement.entity.YbSettlementRecord;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReconDetailRowSupportTest {

    @Test
    void fromSettlementRowsTagsOutpatientAndInpatient() {
        YbSettlementRecord outpatient = settlement("OP-1", "MD-1", ChsSettleEncounterClass.AMB);
        YbSettlementRecord inpatient = settlement("IP-1", "MD-2", ChsSettleEncounterClass.IMP);

        List<ReconDetailRowResult> rows = ReconDetailRowSupport.fromSettlementRows(List.of(outpatient, inpatient));

        assertEquals(2, rows.size());
        assertEquals("OP", rows.get(0).getSource());
        assertEquals("IP", rows.get(1).getSource());
        assertEquals("0", rows.get(0).getRefdSetlFlag());
        assertEquals("0", rows.get(1).getRefdSetlFlag());
    }

    @Test
    void fromSettlementRowsSkipsBlankIds() {
        YbSettlementRecord missingSetl = settlement("", "MD-1", ChsSettleEncounterClass.IMP);
        YbSettlementRecord missingMdtrt = settlement("IP-1", "", ChsSettleEncounterClass.IMP);

        List<ReconDetailRowResult> rows =
            ReconDetailRowSupport.fromSettlementRows(List.of(missingSetl, missingMdtrt));

        assertTrue(rows.isEmpty());
    }

    private static YbSettlementRecord settlement(String setlId, String mdtrtId, String encounterClass) {
        YbSettlementRecord row = new YbSettlementRecord();
        row.setSetlId(setlId);
        row.setMdtrtId(mdtrtId);
        row.setPsnNo("P1");
        row.setEncounterClass(encounterClass);
        row.setMedfeeSumamt(new BigDecimal("100.00"));
        row.setFundPaySumamt(new BigDecimal("80.00"));
        row.setAcctPay(BigDecimal.ZERO);
        row.setPsnCashPay(new BigDecimal("20.00"));
        return row;
    }
}
