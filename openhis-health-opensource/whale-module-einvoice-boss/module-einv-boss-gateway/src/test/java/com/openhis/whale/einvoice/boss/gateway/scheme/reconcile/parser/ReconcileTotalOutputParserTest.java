/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.gateway.scheme.reconcile.parser;

import com.alibaba.fastjson2.JSONObject;
import com.openhis.whale.einvoice.boss.domain.invoke.result.BossInvokeResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReconcileTotalOutputParserTest {

    private final ReconcileTotalOutputParser parser = new ReconcileTotalOutputParser();

    @Test
    void parse_readsBossTotalFields() {
        BossInvokeResult invokeResult = new BossInvokeResult();
        invokeResult.setResultCode("S0000");
        JSONObject business = new JSONObject();
        business.put("allPageTotalCount", 12);
        business.put("allPageTotalAmt", new BigDecimal("99.50"));
        business.put("count", 1);
        invokeResult.setBusinessJson(business);

        var stats = parser.parse(invokeResult);

        assertTrue(stats.isSuccess());
        assertEquals(12L, stats.getTotalCount());
        assertEquals(0, new BigDecimal("99.50").compareTo(stats.getTotalAmount()));
        assertEquals(1L, stats.getPageCount());
    }
}
