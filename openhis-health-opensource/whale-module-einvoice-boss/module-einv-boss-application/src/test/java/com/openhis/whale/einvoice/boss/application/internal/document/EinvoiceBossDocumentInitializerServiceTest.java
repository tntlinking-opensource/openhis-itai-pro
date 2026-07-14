/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.application.internal.document;

import com.openhis.whale.einvoice.boss.domain.issue.access.BossPayloadMetadataAccess;
import com.openhis.whale.einvoice.boss.gateway.config.EinvoiceBossGatewayProperties;
import com.openhis.whale.einvoice.boss.domain.issue.result.BossIssueSnapshot;
import com.openhis.whale.einvoice.boss.domain.enums.AmountStatus;
import com.openhis.whale.einvoice.boss.domain.issue.loader.EinvoiceIssueSnapshotContextLoader;
import com.openhis.whale.einvoice.boss.domain.contrast.repository.EinvChargeContrastRepository;
import com.openhis.whale.einvoice.boss.domain.document.entity.EinvBossDocument;
import com.openhis.whale.einvoice.boss.domain.document.repository.EinvBossDocumentRepository;
import com.openhis.whale.einvoice.boss.domain.document.repository.EinvBossLineRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EinvoiceBossDocumentInitializerServiceTest {

    @Mock
    private EinvoiceIssueSnapshotContextLoader issueSnapshotContextLoader;
    @Mock
    private EinvBossDocumentRepository documentRepository;
    @Mock
    private EinvBossLineRepository lineRepository;
    @Mock
    private EinvChargeContrastRepository contrastRepository;
    @Mock
    private BossPayloadMetadataAccess payloadMetadataAccess;
    @Mock
    private EinvoiceBossGatewayProperties properties;

    @InjectMocks
    private EinvoiceBossDocumentInitializerService initializer;

    @Test
    void refreshForIssue_skipsLineRebuildWhenAdjusted() {
        Long invoiceId = 100L;
        BossIssueSnapshot snapshot = new BossIssueSnapshot();
        snapshot.setTenantId(1L);
        snapshot.setPaymentId(200L);
        snapshot.setBusNo("IV-1");
        snapshot.setTotalAmt(new BigDecimal("10.00"));
        snapshot.setPaymentAmt(new BigDecimal("10.00"));
        snapshot.setInvoiceTypeCode("REG");

        EinvBossDocument existing = new EinvBossDocument();
        existing.setId(50L);
        existing.setAmountStatus(AmountStatus.ADJUSTED.getCode());
        existing.setTotalAmt(new BigDecimal("9.50"));

        when(issueSnapshotContextLoader.loadForInvoice(invoiceId)).thenReturn(snapshot);
        when(documentRepository.findByInvoiceIdAndIsDeletedFalse(invoiceId)).thenReturn(Optional.of(existing));
        when(payloadMetadataAccess.resolveIssueKind(any())).thenReturn("REG");
        when(payloadMetadataAccess.resolveBusType(any())).thenReturn("06");
        when(properties.getPlatform()).thenReturn(new EinvoiceBossGatewayProperties.Platform());
        when(documentRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        initializer.refreshForIssue(invoiceId);

        verify(lineRepository, never()).saveAll(anyList());
        verify(lineRepository, never()).saveAllAndFlush(anyList());
    }
}
