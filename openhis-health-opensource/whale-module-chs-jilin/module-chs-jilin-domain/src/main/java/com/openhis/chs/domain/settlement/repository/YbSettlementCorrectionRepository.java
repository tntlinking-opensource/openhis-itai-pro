/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.settlement.repository;

import com.openhis.chs.domain.settlement.entity.YbSettlementCorrection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** {@link YbSettlementCorrection} 仓储 */
public interface YbSettlementCorrectionRepository extends JpaRepository<YbSettlementCorrection, Long> {

    /**
     * 按结算回执查询冲正记录，按冲正时间倒序。
     *
     * @param settlementRecordId 结算回执主键
     * @param tenantId           租户 ID
     */
    List<YbSettlementCorrection> findAllBySettlementRecordIdAndTenantIdAndIsDeletedFalseOrderByCorrectedAtDesc(
        Long settlementRecordId, Long tenantId);
}
