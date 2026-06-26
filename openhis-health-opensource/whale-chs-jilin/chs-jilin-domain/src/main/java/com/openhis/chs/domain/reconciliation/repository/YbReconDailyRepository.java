/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.reconciliation.repository;

import com.openhis.chs.domain.reconciliation.entity.YbReconDaily;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface YbReconDailyRepository extends JpaRepository<YbReconDaily, Long> {

    List<YbReconDaily> findAllByContractBusNoAndTenantIdAndIsDeletedFalseOrderByCreatedAtDesc(
        String contractBusNo, Long tenantId);
}
