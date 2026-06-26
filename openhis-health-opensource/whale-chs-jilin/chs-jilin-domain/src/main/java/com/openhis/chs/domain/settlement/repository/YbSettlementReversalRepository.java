/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.settlement.repository;

import com.openhis.chs.domain.settlement.entity.YbSettlementReversal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** {@link YbSettlementReversal} 仓储。 */
public interface YbSettlementReversalRepository extends JpaRepository<YbSettlementReversal, Long> {

    List<YbSettlementReversal> findAllBySettlementRecordIdAndTenantIdAndIsDeletedFalseOrderByReversedAtDesc(
        Long settlementRecordId, Long tenantId);
}
