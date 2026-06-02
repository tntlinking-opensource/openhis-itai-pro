/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.domain.administration.invoice.repository;

import com.openhis.health.domain.administration.invoice.entity.Invoice;
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
