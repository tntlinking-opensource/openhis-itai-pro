/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.invoice.repository;

import com.zxplat.domain.administration.invoice.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

/**
 * 发票聚合根仓储。
 * <p>
 * 持久化发票实体，支持按主键、支付单及支付+状态组合查询。
 */
public interface InvoiceRepository extends JpaRepository<Invoice, Long>, JpaSpecificationExecutor<Invoice> {
    /** 按主键查询发票。 */
    Optional<Invoice> findById(Long id);

    /** 按支付主键查询第一条匹配的发票。 */
    Optional<Invoice> findFirstByPaymentId(Long paymentId);

    /** 按支付主键与发票状态枚举查询第一条匹配的发票。 */
    Optional<Invoice> findFirstByPaymentIdAndStatusEnum(Long paymentId, String statusEnum);
}
