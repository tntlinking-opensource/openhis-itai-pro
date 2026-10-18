/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.unionpay.bpc.domain.order.repository;

import com.zxplat.plugin.unionpay.bpc.domain.order.entity.BpcOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface BpcOrderRepository extends JpaRepository<BpcOrder, Long>, JpaSpecificationExecutor<BpcOrder> {

    Optional<BpcOrder> findByTenantIdAndMerTradeNoAndIsDeletedFalse(Long tenantId, String merTradeNo);

    Optional<BpcOrder> findByTenantIdAndTradeNoAndIsDeletedFalse(Long tenantId, String tradeNo);

    List<BpcOrder> findByTenantIdAndPaymentIdAndIsDeletedFalseOrderByCreatedAtDesc(
            Long tenantId, Long paymentId);

    /** 无租户上下文时按支付单查（草稿作废事件收口 PENDING）。 */
    List<BpcOrder> findByPaymentIdAndIsDeletedFalseOrderByCreatedAtDesc(Long paymentId);
}
