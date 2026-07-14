/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.settlement.repository;

import com.openhis.chs.domain.settlement.entity.YbSettlementReversal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** {@link YbSettlementReversal} 仓储 */
public interface YbSettlementReversalRepository extends JpaRepository<YbSettlementReversal, Long> {

    /**
     * 按结算回执查询撤销凭证，按撤销时间倒序。
     *
     * @param settlementRecordId 结算回执主键
     * @param tenantId           租户 ID
     */
    List<YbSettlementReversal> findAllBySettlementRecordIdAndTenantIdAndIsDeletedFalseOrderByReversedAtDesc(
        Long settlementRecordId, Long tenantId);
}
