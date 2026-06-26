/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.settlement.repository;

import com.openhis.chs.domain.settlement.entity.YbSettlementFundDetail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** {@link YbSettlementFundDetail} 仓储。 */
public interface YbSettlementFundDetailRepository extends JpaRepository<YbSettlementFundDetail, Long> {

    List<YbSettlementFundDetail> findAllBySettlementRecordIdAndTenantIdAndIsDeletedFalseOrderByIdAsc(
        Long settlementRecordId, Long tenantId);
}
