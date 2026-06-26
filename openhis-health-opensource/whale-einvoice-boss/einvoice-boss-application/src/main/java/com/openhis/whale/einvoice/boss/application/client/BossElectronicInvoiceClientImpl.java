package com.openhis.whale.einvoice.boss.application.client;

import com.openhis.whale.einvoice.boss.application.issue.BossRecoverAppService;
import com.openhis.whale.einvoice.boss.application.issue.BossWriteOffAppService;
import com.openhis.whale.einvoice.boss.application.issue.EinvoiceBossIssueAppService;
import com.openhis.whale.einvoice.boss.contracts.client.BossElectronicInvoiceClient;
import com.openhis.whale.einvoice.boss.contracts.dto.BossInvoiceIssueResult;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * {@link BossElectronicInvoiceClient} 的 application 层实现。
 */
@Component
@Primary
@RequiredArgsConstructor
public class BossElectronicInvoiceClientImpl implements BossElectronicInvoiceClient {

    private final EinvoiceBossIssueAppService issueAppService;
    private final BossRecoverAppService recoverService;
    private final BossWriteOffAppService writeOffService;

    @Override
    public void scheduleIssueAsync(Long invoiceId) {
        issueAppService.scheduleIssueAsync(invoiceId);
    }

    @Override
    public BossInvoiceIssueResult issueByDocument(Long documentId) {
        BossInvoiceIssueResult result = new BossInvoiceIssueResult();
        result.setBusNo(String.valueOf(documentId));
        return result;
    }

    @Override
    public void scheduleWriteOffAsync(Long invoiceId, String reason) {
        writeOffService.writeOff(invoiceId, reason);
    }

    @Override
    public BossInvoiceIssueResult writeOff(Long invoiceId, String reason) {
        return writeOffService.writeOff(invoiceId, reason);
    }

    @Override
    public Optional<BossInvoiceIssueResult> recoverByBusNo(String busNo) {
        return recoverService.recoverByBusNo(busNo, null);
    }
}
