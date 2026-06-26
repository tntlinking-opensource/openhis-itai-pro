package com.openhis.whale.einvoice.boss.adapter.writer;

import com.openhis.whale.einvoice.boss.adapter.health.InvoiceWritebackAdapter;
import com.openhis.whale.einvoice.boss.contracts.dto.BossInvoiceIssueResult;
import com.openhis.whale.einvoice.boss.contracts.writer.HealthInvoiceWritebackWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * {@link HealthInvoiceWritebackWriter} 的 adapter 实现。
 */
@Component
@Primary
@RequiredArgsConstructor
public class HealthInvoiceWritebackWriterAdapter implements HealthInvoiceWritebackWriter {

    private final InvoiceWritebackAdapter invoiceWritebackAdapter;

    @Override
    public void issueElectronicInvoice(Long invoiceId, BossInvoiceIssueResult result, Long operatorId) {
        invoiceWritebackAdapter.issueElectronicInvoice(invoiceId, result, operatorId);
    }
}
