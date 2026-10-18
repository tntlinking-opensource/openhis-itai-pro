/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.reconciliation.support;

import com.zxplat.plugin.chs.domain.reconciliation.result.ReconDetailRowResult;
import com.zxplat.plugin.chs.domain.settlement.YbSettlementVoucherStatus;
import com.zxplat.plugin.chs.domain.settlement.constants.ChsSettleEncounterClass;
import com.zxplat.plugin.chs.domain.settlement.entity.YbSettlementRecord;
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

    /**
     * 已撤销回执不得打成正向（refd_setl_flag=0），避免平台「退费状态不一致」。
     */
    @Test
    void fromSettlementRowsSkipsRevoked() {
        YbSettlementRecord confirmed = settlement("OP-1", "MD-1", ChsSettleEncounterClass.AMB);
        YbSettlementRecord revoked = settlement("OP-2", "MD-2", ChsSettleEncounterClass.AMB);
        revoked.setStatus(YbSettlementVoucherStatus.REVOKED);

        List<ReconDetailRowResult> rows =
            ReconDetailRowSupport.fromSettlementRows(List.of(confirmed, revoked));

        assertEquals(1, rows.size());
        assertEquals("OP-1", rows.get(0).getSetlId());
        assertEquals("0", rows.get(0).getRefdSetlFlag());
    }

    private static YbSettlementRecord settlement(String setlId, String mdtrtId, String encounterClass) {
        YbSettlementRecord row = new YbSettlementRecord();
        row.setSetlId(setlId);
        row.setMdtrtId(mdtrtId);
        row.setPsnNo("P1");
        row.setEncounterClass(encounterClass);
        row.setStatus(YbSettlementVoucherStatus.CONFIRMED);
        row.setMedfeeSumamt(new BigDecimal("100.00"));
        row.setFundPaySumamt(new BigDecimal("80.00"));
        row.setAcctPay(BigDecimal.ZERO);
        row.setPsnCashPay(new BigDecimal("20.00"));
        return row;
    }
}
