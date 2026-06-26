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

package com.openhis.health.domain.financial.cashiersettlement.repository;

import com.openhis.health.domain.financial.cashiersettlement.entity.CashierSettlement;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 收费员交账头仓储。
 */
public interface CashierSettlementRepository extends JpaRepository<CashierSettlement, Long> {

    /**
     * 判断租户下交账单号是否已存在。
     *
     * @param busNo    交账单号
     * @param tenantId 租户标识
     * @return 是否存在
     */
    boolean existsByBusNoAndTenantId(String busNo, Long tenantId);
}
