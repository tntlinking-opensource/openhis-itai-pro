/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.settlement.repository;

import com.openhis.chs.domain.settlement.entity.YbSettlementCorrection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** {@link YbSettlementCorrection} 仓储。 */
public interface YbSettlementCorrectionRepository extends JpaRepository<YbSettlementCorrection, Long> {

    List<YbSettlementCorrection> findAllBySettlementRecordIdAndTenantIdAndIsDeletedFalseOrderByCorrectedAtDesc(
        Long settlementRecordId, Long tenantId);
}
