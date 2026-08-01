/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.settlement.repository;

import com.openhis.chs.domain.settlement.entity.YbSettlementReversal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
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

    /**
     * 查询指定撤销时间窗内的未删除撤销凭证（左闭右开，供对账汇总/明细）。
     *
     * @param tenantId     租户 ID
     * @param beginAt      起始时间（含）
     * @param endExclusive 结束时间（不含）
     */
    List<YbSettlementReversal> findAllByTenantIdAndIsDeletedFalseAndReversedAtGreaterThanEqualAndReversedAtLessThan(
        Long tenantId,
        LocalDateTime beginAt,
        LocalDateTime endExclusive);
}
