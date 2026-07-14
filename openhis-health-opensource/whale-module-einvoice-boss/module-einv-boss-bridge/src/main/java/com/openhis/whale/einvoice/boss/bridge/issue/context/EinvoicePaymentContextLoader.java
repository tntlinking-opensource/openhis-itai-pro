/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.bridge.issue.context;

import com.openhis.health.domain.financial.paymentreconciliation.entity.PaymentReconciliation;
import com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.einvoice.boss.domain.share.EinvBossErrorCodes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 支付快照加载器：从 {@code fin_payment_reconciliation} 读取支付金额、收费项 ID 串等。
 */
@Component
@RequiredArgsConstructor
public class EinvoicePaymentContextLoader {

    private final PaymentReconciliationQueryService paymentQueryService;

    /**
     * 按主键加载支付记录。
     *
     * @param paymentId 支付主键
     * @return 支付实体
     */
    public PaymentReconciliation requireById(Long paymentId) {
        return paymentQueryService.findOptionalPaymentEntityById(paymentId)
                .orElseThrow(() -> new BusinessException(EinvBossErrorCodes.EINV_DOCUMENT_NOT_FOUND, "支付记录不存在"));
    }
}
