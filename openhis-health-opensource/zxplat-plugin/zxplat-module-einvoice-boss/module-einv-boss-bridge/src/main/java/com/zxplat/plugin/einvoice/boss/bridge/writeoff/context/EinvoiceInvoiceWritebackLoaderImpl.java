/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.einvoice.boss.bridge.writeoff.context;

import com.zxplat.domain.administration.invoice.command.CancelInvoiceCommand;
import com.zxplat.domain.administration.invoice.command.CreateDraftInvoiceCommand;
import com.zxplat.domain.administration.invoice.command.IssueElectronicInvoiceCommand;
import com.zxplat.domain.administration.invoice.entity.Invoice;
import com.zxplat.domain.administration.invoice.service.InvoiceDomainService;
import com.zxplat.domain.administration.invoice.service.InvoiceQueryService;
import com.zxplat.domain.share.enums.InvoiceStatus;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.plugin.einvoice.boss.domain.issue.result.BossInvoiceIssueResult;
import com.zxplat.plugin.einvoice.boss.domain.share.EinvBossErrorCodes;
import com.zxplat.plugin.einvoice.boss.domain.writeoff.loader.EinvoiceInvoiceWritebackLoader;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * {@link com.zxplat.plugin.einvoice.boss.domain.writeoff.loader.EinvoiceInvoiceWritebackLoader} 的 bridge 实现。
 *
 * <p>将博思票号写入 health {@code adm_invoice} 并触发 {@code InvoiceIssuedEvent}；
 * 无 billNo 时静默跳过，防止脏回写。</p>
 */
@Component
@Primary
@RequiredArgsConstructor
public class EinvoiceInvoiceWritebackLoaderImpl implements EinvoiceInvoiceWritebackLoader {

    private final InvoiceDomainService invoiceDomainService;
    private final InvoiceQueryService invoiceQueryService;

    /**
     * {@inheritDoc}
     */
    @Override
    public void issueElectronicInvoice(Long invoiceId, BossInvoiceIssueResult result, Long operatorId) {
        // Gate：无结果或无票号时不回写，避免脏状态
        if (invoiceId == null || result == null || result.getBillNo() == null) {
            return;
        }
        IssueElectronicInvoiceCommand command = new IssueElectronicInvoiceCommand();
        command.setInvoiceId(invoiceId);
        command.setBillNo(result.getBillNo());
        command.setBillBatchCode(result.getBatchCode());
        command.setBillCreateTime(result.getBillCreateTime());
        command.setOperatorId(operatorId);
        // Cmd：经 health 领域服务落库并发布 InvoiceIssuedEvent
        invoiceDomainService.issueElectronicInvoice(command);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeoffLocalInvoice(Long invoiceId, String reason, Long operatorId) {
        if (invoiceId == null) {
            return;
        }
        Invoice invoice = invoiceQueryService.findOptionalInvoiceEntityById(invoiceId).orElse(null);
        if (invoice == null) {
            return;
        }
        if (InvoiceStatus.CANCELLED.getCode().equals(invoice.getStatusEnum())) {
            return;
        }
        CancelInvoiceCommand command = new CancelInvoiceCommand();
        command.setInvoiceId(invoiceId);
        command.setReason(StringUtils.hasText(reason) ? reason.trim() : "手动冲红");
        command.setOperatorId(operatorId);
        invoiceDomainService.writeoffInvoiceAndCreateRedRecord(command);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Long createDraftInvoiceForReissue(Long writtenOffInvoiceId, Long operatorId) {
        Invoice source = invoiceQueryService.findOptionalInvoiceEntityById(writtenOffInvoiceId).orElse(null);
        if (source == null) {
            throw new BusinessException(EinvBossErrorCodes.EINV_DOCUMENT_NOT_FOUND, "原票据不存在，无法重开");
        }
        CreateDraftInvoiceCommand command = new CreateDraftInvoiceCommand();
        command.setPatientId(source.getPatientId());
        command.setPaymentId(source.getPaymentId());
        command.setTypeCode(source.getTypeCode());
        command.setAmount(source.getAmount());
        command.setOperatorId(operatorId);
        return invoiceDomainService.createDraftInvoiceAfterPayment(command);
    }
}
