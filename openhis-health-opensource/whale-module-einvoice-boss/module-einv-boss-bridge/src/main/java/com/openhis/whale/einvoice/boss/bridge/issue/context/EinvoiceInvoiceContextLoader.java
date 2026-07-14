/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.bridge.issue.context;

import com.openhis.health.domain.administration.invoice.entity.Invoice;
import com.openhis.health.domain.administration.invoice.service.InvoiceQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.einvoice.boss.domain.share.EinvBossErrorCodes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 票据主数据加载器：从 health {@code adm_invoice} 读取开票上下文。
 *
 * <p>供 {@link EinvoiceIssueSnapshotContextLoaderImpl} 聚合；缺失时抛
 * {@link com.openhis.whale.einvoice.boss.domain.share.EinvBossErrorCodes#EINV_DOCUMENT_NOT_FOUND}。</p>
 */
@Component
@RequiredArgsConstructor
public class EinvoiceInvoiceContextLoader {

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
