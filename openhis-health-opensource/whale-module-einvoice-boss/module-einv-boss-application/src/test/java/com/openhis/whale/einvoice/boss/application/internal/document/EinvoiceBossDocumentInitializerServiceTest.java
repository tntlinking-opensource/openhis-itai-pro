/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.application.internal.document;

import com.openhis.whale.einvoice.boss.domain.issue.access.BossPayloadMetadataAccess;
import com.openhis.whale.einvoice.boss.gateway.config.EinvoiceBossGatewayProperties;
import com.openhis.whale.einvoice.boss.domain.issue.result.BossChargeLineSnapshot;
import com.openhis.whale.einvoice.boss.domain.issue.result.BossIssueSnapshot;
import com.openhis.whale.einvoice.boss.domain.enums.AmountStatus;
import com.openhis.whale.einvoice.boss.domain.issue.loader.EinvoiceIssueSnapshotContextLoader;
import com.openhis.whale.einvoice.boss.domain.contrast.repository.EinvChargeContrastRepository;
import com.openhis.whale.einvoice.boss.domain.document.entity.EinvBossDocument;
import com.openhis.whale.einvoice.boss.domain.document.entity.EinvBossLine;
import com.openhis.whale.einvoice.boss.domain.document.repository.EinvBossDocumentRepository;
import com.openhis.whale.einvoice.boss.domain.document.repository.EinvBossLineRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void initialize_usesUnitPriceAsStdAmount() {
        Long invoiceId = 100L;
        BossChargeLineSnapshot chargeLine = new BossChargeLineSnapshot();
        chargeLine.setChargeItemId(1L);
        chargeLine.setInternalCode("FIN_herbal");
        chargeLine.setAmount(new BigDecimal("262.50"));
        chargeLine.setQuantity(new BigDecimal("50"));
        chargeLine.setUnitPrice(new BigDecimal("5.25"));
        chargeLine.setUnit("g");
        chargeLine.setItemName("当归");

        BossIssueSnapshot snapshot = new BossIssueSnapshot();
        snapshot.setTenantId(1L);
        snapshot.setPaymentId(200L);
        snapshot.setBusNo("IV-1");
        snapshot.setTotalAmt(new BigDecimal("262.50"));
        snapshot.setPaymentAmt(new BigDecimal("262.50"));
        snapshot.setInvoiceTypeCode("OUTPATIENT");
        snapshot.setChargeLines(List.of(chargeLine));

        when(issueSnapshotContextLoader.loadForInvoice(invoiceId)).thenReturn(snapshot);
        when(documentRepository.findByInvoiceIdAndIsDeletedFalse(invoiceId)).thenReturn(Optional.empty());
        when(payloadMetadataAccess.resolveIssueKind(any())).thenReturn("AMB");
        when(payloadMetadataAccess.resolveBusType(any())).thenReturn("02");
        when(properties.getPlatform()).thenReturn(new EinvoiceBossGatewayProperties.Platform());
        when(documentRepository.saveAndFlush(any())).thenAnswer(inv -> {
            EinvBossDocument doc = inv.getArgument(0);
            doc.setId(50L);
            return doc;
        });
        when(lineRepository.findByDocumentIdAndIsDeletedFalseOrderByLineNoAsc(50L))
                .thenReturn(Collections.emptyList());

        initializer.initialize(invoiceId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<EinvBossLine>> linesCaptor = ArgumentCaptor.forClass(List.class);
        verify(lineRepository).saveAllAndFlush(linesCaptor.capture());
        EinvBossLine line = linesCaptor.getValue().get(0);
        assertEquals(new BigDecimal("5.25"), line.getStdAmount());
        assertEquals(new BigDecimal("262.50"), line.getAmount());
        assertEquals(new BigDecimal("50"), line.getQuantity());
        assertEquals("当归", line.getItemName());
    }

    @Test
    void initialize_fallsBackToAmountWithQtyOneWhenNoUnitPrice() {
        Long invoiceId = 101L;
        BossChargeLineSnapshot chargeLine = new BossChargeLineSnapshot();
        chargeLine.setChargeItemId(2L);
        chargeLine.setInternalCode("FIN_fee");
        chargeLine.setAmount(new BigDecimal("6.00"));
        chargeLine.setQuantity(new BigDecimal("2"));
        chargeLine.setUnit("次");

        BossIssueSnapshot snapshot = new BossIssueSnapshot();
        snapshot.setTenantId(1L);
        snapshot.setPaymentId(201L);
        snapshot.setBusNo("IV-2");
        snapshot.setTotalAmt(new BigDecimal("6.00"));
        snapshot.setPaymentAmt(new BigDecimal("6.00"));
        snapshot.setInvoiceTypeCode("OUTPATIENT");
        snapshot.setChargeLines(List.of(chargeLine));

        when(issueSnapshotContextLoader.loadForInvoice(invoiceId)).thenReturn(snapshot);
        when(documentRepository.findByInvoiceIdAndIsDeletedFalse(invoiceId)).thenReturn(Optional.empty());
        when(payloadMetadataAccess.resolveIssueKind(any())).thenReturn("AMB");
        when(payloadMetadataAccess.resolveBusType(any())).thenReturn("02");
        when(properties.getPlatform()).thenReturn(new EinvoiceBossGatewayProperties.Platform());
        when(documentRepository.saveAndFlush(any())).thenAnswer(inv -> {
            EinvBossDocument doc = inv.getArgument(0);
            doc.setId(51L);
            return doc;
        });
        when(lineRepository.findByDocumentIdAndIsDeletedFalseOrderByLineNoAsc(51L))
                .thenReturn(Collections.emptyList());

        initializer.initialize(invoiceId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<EinvBossLine>> linesCaptor = ArgumentCaptor.forClass(List.class);
        verify(lineRepository).saveAllAndFlush(linesCaptor.capture());
        EinvBossLine line = linesCaptor.getValue().get(0);
        assertEquals(new BigDecimal("6.00"), line.getStdAmount());
        assertEquals(new BigDecimal("6.00"), line.getAmount());
        assertEquals(BigDecimal.ONE, line.getQuantity());
    }
}
