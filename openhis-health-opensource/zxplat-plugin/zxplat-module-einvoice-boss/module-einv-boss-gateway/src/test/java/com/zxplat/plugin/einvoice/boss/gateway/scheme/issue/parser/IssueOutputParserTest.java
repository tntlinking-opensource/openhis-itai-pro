/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.einvoice.boss.gateway.scheme.issue.parser;

import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.plugin.einvoice.boss.domain.share.EinvBossErrorCodes;
import com.zxplat.plugin.einvoice.boss.domain.document.entity.EinvBossDocument;
import com.zxplat.plugin.einvoice.boss.domain.issue.result.BossInvoiceIssueResult;
import com.zxplat.plugin.einvoice.boss.domain.invoke.result.BossInvokeResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IssueOutputParserTest {

    private final IssueOutputParser parser = new IssueOutputParser();

    @Test
    void toIssueResult_mapsSuccessFields() {
        BossInvokeResult invokeResult = new BossInvokeResult();
        invokeResult.setResultCode("S0000");
        invokeResult.setBillNo("1001651756");
        invokeResult.setBillBatchCode("123");
        invokeResult.setRandom("053ff1");
        invokeResult.setBillQrCode("qr");
        invokeResult.setPictureUrl("http://pic");
        invokeResult.setPictureNetUrl("http://pic-net");
        LocalDateTime createTime = LocalDateTime.of(2026, 6, 29, 22, 47, 23, 940_000_000);
        invokeResult.setBillCreateTime(createTime);

        BossInvoiceIssueResult result = parser.toIssueResult(invokeResult, "IV-1", new BigDecimal("9.50"));

        assertEquals("IV-1", result.getBusNo());
        assertEquals(0, new BigDecimal("9.50").compareTo(result.getTotalAmt()));
        assertEquals("1001651756", result.getBillNo());
        assertEquals("123", result.getBatchCode());
        assertEquals("053ff1", result.getRandom());
        assertEquals("qr", result.getBillQrCode());
        assertEquals("http://pic", result.getPictureUrl());
        assertEquals("http://pic-net", result.getPictureNetUrl());
        assertEquals(createTime, result.getBillCreateTime());
    }

    @Test
    void toIssueResult_throwsOnBossReject() {
        BossInvokeResult invokeResult = new BossInvokeResult();
        invokeResult.setResultCode("E9999");
        invokeResult.setRawMessage("duplicate busNo");

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> parser.toIssueResult(invokeResult, "IV-1", BigDecimal.ONE));

        assertEquals(EinvBossErrorCodes.EINV_BOSS_REJECT, ex.getErrorCode());
    }

    @Test
    void fromDocument_and_applyToDocument_roundTrip() {
        EinvBossDocument document = new EinvBossDocument();
        document.setBusNo("IV-2");
        document.setBossBillNo("200001");
        document.setBossBatchCode("456");
        document.setRandom("abc");
        document.setBillQrCode("qr2");
        document.setPictureUrl("http://a");
        document.setPictureNetUrl("http://b");
        document.setTotalAmt(new BigDecimal("3.00"));

        BossInvoiceIssueResult fromDoc = parser.fromDocument(document);

        assertEquals("200001", fromDoc.getBillNo());
        assertEquals("IV-2", fromDoc.getBusNo());

        BossInvoiceIssueResult updated = new BossInvoiceIssueResult();
        updated.setBillNo("300001");
        updated.setBatchCode("789");
        updated.setRandom("xyz");
        updated.setBillQrCode("qr3");
        updated.setPictureUrl("http://c");
        updated.setPictureNetUrl("http://d");

        parser.applyToDocument(document, updated, "{\"k\":1}");

        assertEquals("300001", document.getBossBillNo());
        assertEquals("789", document.getBossBatchCode());
        assertEquals("xyz", document.getRandom());
        assertEquals("qr3", document.getBillQrCode());
        assertEquals("http://c", document.getPictureUrl());
        assertEquals("http://d", document.getPictureNetUrl());
        assertEquals("{\"k\":1}", document.getPayloadJson());
    }
}
