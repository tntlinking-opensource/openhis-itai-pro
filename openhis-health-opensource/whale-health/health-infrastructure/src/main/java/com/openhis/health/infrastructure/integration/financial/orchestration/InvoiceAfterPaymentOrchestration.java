/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.financial.orchestration;

import com.openhis.health.domain.administration.invoice.command.CreateDraftInvoiceCommand;
import com.openhis.health.domain.administration.invoice.service.InvoiceDomainService;
import com.openhis.health.infrastructure.integration.einvoice.orchestration.BossElectronicInvoiceAfterCommitOrchestration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 支付后票据编排：票面金额（收费项合计）&gt; 0 时创建 DRAFT 并调度博思开具。
 *
 * <p>医保基金/个账分担后患者现金为 0 仍须开票；仅真正零元收费（票面合计 ≤ 0）跳过开票（BR-INV-008）。</p>
 */
@Component
@RequiredArgsConstructor
public class InvoiceAfterPaymentOrchestration {

    private final InvoiceDomainService invoiceDomainService;
    private final BossElectronicInvoiceAfterCommitOrchestration bossElectronicInvoiceAfterCommitOrchestration;

    /**
     * 按票面金额决定是否开票（非患者现金 {@code displayAmount}）。
     *
     * @param command 草稿命令，{@code amount} 须为收费项合计票面金额（结算刻度）
     * @return 票据主键；票面零元或命令无效时 {@code null}
     */
    public Long createDraftAndScheduleIssueIfPayable(CreateDraftInvoiceCommand command) {
        // Gate：票面金额 ≤ 0（真正零元收费）不开票；医保全报现金为 0 但票面 > 0 仍开票
        if (command == null || !requiresElectronicInvoice(command.getAmount())) {
            return null;
        }
        // Cmd：创建 adm_invoice 草稿
        Long invoiceId = invoiceDomainService.createDraftInvoiceAfterPayment(command);
        // Asm：事务提交后调度博思开具
        bossElectronicInvoiceAfterCommitOrchestration.afterDraftCreated(invoiceId);
        return invoiceId;
    }

    /**
     * 是否应开具电子票据：票面金额须严格大于 0。
     *
     * <p>入参为收费项合计（含医保已付部分），不是个人现金应付 {@code displayAmount}。</p>
     *
     * @param invoiceFaceAmount 票面金额（收费项合计，结算刻度）
     * @return {@code true} 表示应创建草稿并调度博思
     */
    public static boolean requiresElectronicInvoice(BigDecimal invoiceFaceAmount) {
        return invoiceFaceAmount != null && invoiceFaceAmount.compareTo(BigDecimal.ZERO) > 0;
    }
}
