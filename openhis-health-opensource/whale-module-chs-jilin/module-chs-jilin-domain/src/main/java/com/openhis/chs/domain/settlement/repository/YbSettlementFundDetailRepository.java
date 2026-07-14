/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.settlement.repository;

import com.openhis.chs.domain.settlement.entity.YbSettlementFundDetail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** {@link YbSettlementFundDetail} 仓储 */
public interface YbSettlementFundDetailRepository extends JpaRepository<YbSettlementFundDetail, Long> {

    /**
     * 按结算回执查询基金分项明细，按 id 升序。
     *
     * @param settlementRecordId 结算回执主键
     * @param tenantId           租户 ID
     */
    List<YbSettlementFundDetail> findAllBySettlementRecordIdAndTenantIdAndIsDeletedFalseOrderByIdAsc(
        Long settlementRecordId, Long tenantId);
}
