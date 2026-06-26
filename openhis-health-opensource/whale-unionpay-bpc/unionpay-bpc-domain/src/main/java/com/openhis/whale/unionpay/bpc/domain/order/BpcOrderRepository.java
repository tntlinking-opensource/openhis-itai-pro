package com.openhis.whale.unionpay.bpc.domain.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BpcOrderRepository extends JpaRepository<BpcOrder, Long> {

    Optional<BpcOrder> findByTenantIdAndMerTradeNoAndIsDeletedFalse(Long tenantId, String merTradeNo);

    Optional<BpcOrder> findByTenantIdAndTradeNoAndIsDeletedFalse(Long tenantId, String tradeNo);

    List<BpcOrder> findByTenantIdAndPaymentIdAndIsDeletedFalseOrderByCreatedAtDesc(
            Long tenantId, Long paymentId);
}
