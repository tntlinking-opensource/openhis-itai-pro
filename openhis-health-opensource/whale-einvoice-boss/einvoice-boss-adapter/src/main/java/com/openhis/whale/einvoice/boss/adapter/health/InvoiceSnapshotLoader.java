package com.openhis.whale.einvoice.boss.adapter.health;

import com.openhis.health.domain.administration.invoice.entity.Invoice;
import com.openhis.health.domain.administration.invoice.service.InvoiceQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.einvoice.boss.contracts.constants.EinvBossErrorCodes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 读取 health 侧 adm_invoice 快照（boss 只读 bus_no 等字段）。
 */
@Component
@RequiredArgsConstructor
public class InvoiceSnapshotLoader {

    private final InvoiceQueryService invoiceQueryService;

    /**
     * 按主键加载票据快照。
     *
     * @param invoiceId 票据主键
     * @return 票据实体
     */
    public Invoice requireById(Long invoiceId) {
        return invoiceQueryService.findOptionalInvoiceEntityById(invoiceId)
                .orElseThrow(() -> new BusinessException(EinvBossErrorCodes.EINV_DOCUMENT_NOT_FOUND, "票据不存在"));
    }
}
