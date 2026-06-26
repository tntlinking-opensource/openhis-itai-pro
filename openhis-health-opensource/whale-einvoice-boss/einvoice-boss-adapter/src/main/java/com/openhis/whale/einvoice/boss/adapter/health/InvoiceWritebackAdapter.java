package com.openhis.whale.einvoice.boss.adapter.health;

import com.openhis.health.domain.administration.invoice.command.IssueElectronicInvoiceCommand;
import com.openhis.health.domain.administration.invoice.service.InvoiceDomainService;
import com.openhis.whale.einvoice.boss.contracts.dto.BossInvoiceIssueResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 博思开具成功后回写 health 票据（bill_no、issued_at、status_enum）。
 */
@Component
@RequiredArgsConstructor
public class InvoiceWritebackAdapter {

    private final InvoiceDomainService invoiceDomainService;

    /**
     * 将博思开具结果回写 adm_invoice。
     *
     * @param invoiceId 票据主键
     * @param result 博思开具结果
     * @param operatorId 操作人（可空）
     */
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
}
