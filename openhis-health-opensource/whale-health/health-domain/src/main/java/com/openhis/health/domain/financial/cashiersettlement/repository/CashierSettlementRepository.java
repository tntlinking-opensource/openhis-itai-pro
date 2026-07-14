/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
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
