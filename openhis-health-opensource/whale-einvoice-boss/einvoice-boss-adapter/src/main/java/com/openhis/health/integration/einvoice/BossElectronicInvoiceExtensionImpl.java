/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.einvoice;

import com.openhis.whale.einvoice.boss.contracts.client.BossElectronicInvoiceClient;
import com.openhis.whale.einvoice.boss.domain.document.entity.EinvBossDocument;
import com.openhis.whale.einvoice.boss.domain.document.repository.EinvBossDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** {@link BossElectronicInvoiceExtension} 实现（委托 {@link BossElectronicInvoiceClient}）。 */
@Slf4j
@Component
@Primary
@RequiredArgsConstructor
@ConditionalOnBean(BossElectronicInvoiceClient.class)
@ConditionalOnProperty(name = "openhis.modules.einvoice-boss.enabled", havingValue = "true")
public class BossElectronicInvoiceExtensionImpl implements BossElectronicInvoiceExtension {

    private final BossElectronicInvoiceClient bossElectronicInvoiceClient;
    private final EinvBossDocumentRepository documentRepository;

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    @Async
    public void scheduleIssueAfterDraft(Long invoiceId) {
        if (invoiceId == null) {
            return;
        }
        log.debug("BossElectronicInvoiceExtension schedule issue, invoiceId={}", invoiceId);
        bossElectronicInvoiceClient.scheduleIssueAsync(invoiceId);
    }

    @Override
    @Async
    public void scheduleWriteOffAfterCancel(Long invoiceId, String reason) {
        if (invoiceId == null) {
            return;
        }
        EinvBossDocument document = documentRepository.findByInvoiceIdAndIsDeletedFalse(invoiceId).orElse(null);
        if (document == null || !StringUtils.hasText(document.getBossBillNo())) {
            log.debug("BossElectronicInvoiceExtension skip writeOff, invoiceId={}", invoiceId);
            return;
        }
        String writeOffReason = StringUtils.hasText(reason) ? reason : "退费冲红";
        log.info("BossElectronicInvoiceExtension schedule writeOff, invoiceId={}", invoiceId);
        bossElectronicInvoiceClient.scheduleWriteOffAsync(invoiceId, writeOffReason);
    }
}
