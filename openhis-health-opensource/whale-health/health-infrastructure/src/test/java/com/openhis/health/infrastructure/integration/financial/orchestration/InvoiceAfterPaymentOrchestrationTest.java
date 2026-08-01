/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.financial.orchestration;

import com.openhis.health.domain.administration.invoice.command.CreateDraftInvoiceCommand;
import com.openhis.health.domain.administration.invoice.service.InvoiceDomainService;
import com.openhis.health.infrastructure.integration.einvoice.orchestration.BossElectronicInvoiceAfterCommitOrchestration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceAfterPaymentOrchestrationTest {

    @Mock
    private InvoiceDomainService invoiceDomainService;

    @Mock
    private BossElectronicInvoiceAfterCommitOrchestration bossElectronicInvoiceAfterCommitOrchestration;

    @InjectMocks
    private InvoiceAfterPaymentOrchestration orchestration;

    @Test
    void requiresElectronicInvoice_positiveAmount_returnsTrue() {
        // 票面金额 > 0（含医保全报场景下收费项合计）应开票
        assertThat(InvoiceAfterPaymentOrchestration.requiresElectronicInvoice(new BigDecimal("0.01"))).isTrue();
    }

    @Test
    void requiresElectronicInvoice_zeroOrNull_returnsFalse() {
        // 真正零元收费才跳过；与患者现金 displayAmount 无关
        assertThat(InvoiceAfterPaymentOrchestration.requiresElectronicInvoice(BigDecimal.ZERO)).isFalse();
        assertThat(InvoiceAfterPaymentOrchestration.requiresElectronicInvoice(null)).isFalse();
    }

    @Test
    void createDraftAndScheduleIssueIfPayable_zeroAmount_skipsInvoice() {
        CreateDraftInvoiceCommand command = new CreateDraftInvoiceCommand();
        command.setAmount(BigDecimal.ZERO);

        assertThat(orchestration.createDraftAndScheduleIssueIfPayable(command)).isNull();

        verify(invoiceDomainService, never()).createDraftInvoiceAfterPayment(command);
        verify(bossElectronicInvoiceAfterCommitOrchestration, never()).afterDraftCreated(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void createDraftAndScheduleIssueIfPayable_positiveFaceAmount_createsDraftAndSchedules() {
        // 医保全报时现金可为 0，但票面（收费项合计）> 0 仍须建草稿并调度博思
        CreateDraftInvoiceCommand command = new CreateDraftInvoiceCommand();
        command.setAmount(new BigDecimal("12.50"));
        when(invoiceDomainService.createDraftInvoiceAfterPayment(command)).thenReturn(99L);

        assertThat(orchestration.createDraftAndScheduleIssueIfPayable(command)).isEqualTo(99L);

        verify(invoiceDomainService).createDraftInvoiceAfterPayment(command);
        verify(bossElectronicInvoiceAfterCommitOrchestration).afterDraftCreated(99L);
    }
}
