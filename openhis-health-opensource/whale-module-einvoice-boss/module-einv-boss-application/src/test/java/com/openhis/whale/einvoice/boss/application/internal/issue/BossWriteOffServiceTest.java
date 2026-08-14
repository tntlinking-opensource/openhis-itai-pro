/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.application.internal.issue;

import com.openhis.whale.einvoice.boss.domain.writeoff.access.BossWriteOffOutboundAccess;
import com.openhis.whale.einvoice.boss.domain.document.entity.EinvBossDocument;
import com.openhis.whale.einvoice.boss.domain.document.repository.EinvBossDocumentRepository;
import com.openhis.whale.einvoice.boss.domain.issue.result.BossInvoiceIssueResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BossWriteOffServiceTest {

    @Mock
    private BossWriteOffOutboundAccess writeOffOutboundAccess;
    @Mock
    private com.openhis.whale.einvoice.boss.domain.issue.loader.EinvoiceIssueSnapshotContextLoader issueSnapshotContextLoader;
    @Mock
    private EinvBossDocumentRepository documentRepository;

    @InjectMocks
    private BossWriteOffService writeOffService;

    @Test
    void writeOff_idempotentWhenAlreadySuccess() {
        Long invoiceId = 10L;
        EinvBossDocument document = new EinvBossDocument();
        document.setId(1L);
        document.setBossBillNo("100001");
        document.setWriteOffStatus("SUCCESS");
        document.setWriteOffBillNo("200001");
        document.setBusNo("IV-1");
        document.setTotalAmt(new BigDecimal("5.00"));

        when(documentRepository.findAllByInvoiceIdAndIsDeletedFalseOrderByIdAsc(invoiceId))
                .thenReturn(List.of(document));

        BossInvoiceIssueResult result = writeOffService.writeOff(invoiceId, "??");

        assertEquals("200001", result.getBillNo());
        verify(writeOffOutboundAccess, never()).invokeWriteOff(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }
}
