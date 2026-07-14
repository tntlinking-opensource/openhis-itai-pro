/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.unionpay.bpc.domain.order.repository;

import com.openhis.whale.unionpay.bpc.domain.order.entity.BpcOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface BpcOrderRepository extends JpaRepository<BpcOrder, Long>, JpaSpecificationExecutor<BpcOrder> {

    Optional<BpcOrder> findByTenantIdAndMerTradeNoAndIsDeletedFalse(Long tenantId, String merTradeNo);

    Optional<BpcOrder> findByTenantIdAndTradeNoAndIsDeletedFalse(Long tenantId, String tradeNo);

    List<BpcOrder> findByTenantIdAndPaymentIdAndIsDeletedFalseOrderByCreatedAtDesc(
            Long tenantId, Long paymentId);
}
