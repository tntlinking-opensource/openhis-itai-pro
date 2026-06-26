/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.domain.financial.paymentreconciliation.repository;

import com.openhis.health.domain.financial.paymentreconciliation.entity.PaymentYbSettlement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/** 支付流水医保结算凭证仓储。 */
public interface PaymentYbSettlementRepository extends JpaRepository<PaymentYbSettlement, Long> {

    List<PaymentYbSettlement> findAllByReconciliationIdAndIsDeletedFalseOrderByCreatedAtAsc(Long reconciliationId);

    List<PaymentYbSettlement> findAllByReconciliationIdInAndIsDeletedFalseOrderByReconciliationIdAscCreatedAtAsc(
        Collection<Long> reconciliationIds);

    void deleteByReconciliationId(Long reconciliationId);
}
