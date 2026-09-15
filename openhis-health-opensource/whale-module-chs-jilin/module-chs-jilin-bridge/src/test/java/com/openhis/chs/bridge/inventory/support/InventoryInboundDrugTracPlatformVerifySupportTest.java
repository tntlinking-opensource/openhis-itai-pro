/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.inventory.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.openhis.chs.domain.inventory.access.Inv35xxQueryInvoker;
import com.openhis.chs.domain.inventory.command.InvUploadLineCommand;
import com.openhis.chs.domain.inventory.result.PlatformInvQueryResult;
import com.openhis.chs.domain.share.constants.ChsInventoryConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** {@link InventoryInboundDrugTracPlatformVerifySupport} 入库 3512 核验单测。 */
@ExtendWith(MockitoExtension.class)
class InventoryInboundDrugTracPlatformVerifySupportTest {

    @Mock
    private Inv35xxQueryInvoker inv35xxQueryInvoker;

    @InjectMocks
    private InventoryInboundDrugTracPlatformVerifySupport support;

    @Test
    void skipVerifyForOutboundLine() {
        InvUploadLineCommand line = new InvUploadLineCommand();
        line.setInvChgType("107");
        line.setFixmedinsBchno("MDP1");
        line.setDrugTracCodgs(List.of("A"));
        assertThat(support.findIncompleteInboundTraceMessage("bus", line, null, null)).isNull();
    }

    @Test
    void failWhenPlatformMissesCodes() {
        InvUploadLineCommand line = new InvUploadLineCommand();
        line.setInvChgType("108");
        line.setFixmedinsBchno("CIR-C01");
        line.setDrugTracCodgs(List.of("CODE_OK", "CODE_MISS"));
        PlatformInvQueryResult result = new PlatformInvQueryResult();
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("drug_trac_codg", "CODE_OK");
        rows.add(row);
        result.setRows(rows);
        when(inv35xxQueryInvoker.query3512(any())).thenReturn(result);

        String msg = support.findIncompleteInboundTraceMessage("bus", line, 1L, "op");
        assertThat(msg).startsWith(ChsInventoryConstants.PLATFORM_MSG_INBOUND_TRACE_VERIFY_INCOMPLETE_PREFIX);
        assertThat(msg).contains("缺失=1");
        assertThat(msg).contains("CODE_MISS");
    }

    @Test
    void passWhenAllCodesPresent() {
        InvUploadLineCommand line = new InvUploadLineCommand();
        line.setInvChgType("106");
        line.setFixmedinsBchno("RET1");
        line.setDrugTracCodgs(List.of("A", "B"));
        PlatformInvQueryResult result = new PlatformInvQueryResult();
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> r1 = new LinkedHashMap<>();
        r1.put("drugTracCodg", "A");
        Map<String, Object> r2 = new LinkedHashMap<>();
        r2.put("drug_trac_codg", "B");
        rows.add(r1);
        rows.add(r2);
        result.setRows(rows);
        when(inv35xxQueryInvoker.query3512(any())).thenReturn(result);

        assertThat(support.findIncompleteInboundTraceMessage("bus", line, null, null)).isNull();
    }
}
