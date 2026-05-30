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

package com.openhis.health.domain.financial.paymentreconciliation.repository;

import com.openhis.health.domain.financial.paymentreconciliation.entity.PaymentRecStatic;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 支付统计快照仓储。
 */
public interface PaymentRecStaticRepository extends JpaRepository<PaymentRecStatic, Long> {
    /**
     * 按条件查询。
     * @param reconciliationId 条件
     * @return 查询结果
     */
    List<PaymentRecStatic> findByReconciliationId(Long reconciliationId);

    /**
     * 按条件删除（受软删底座约束）。
     * @param reconciliationId 条件
     */
    void deleteByReconciliationId(Long reconciliationId);
}

