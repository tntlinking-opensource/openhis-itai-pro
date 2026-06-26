package com.openhis.whale.einvoice.boss.adapter.health;

import com.openhis.health.domain.financial.paymentreconciliation.entity.PaymentReconciliation;
import com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.einvoice.boss.contracts.constants.EinvBossErrorCodes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 读取 fin_payment_reconciliation 支付快照。
 */
@Component
@RequiredArgsConstructor
public class PaymentSnapshotLoader {

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
