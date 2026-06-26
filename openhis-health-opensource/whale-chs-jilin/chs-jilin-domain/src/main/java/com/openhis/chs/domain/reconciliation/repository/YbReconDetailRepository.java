/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.reconciliation.repository;

import com.openhis.chs.domain.reconciliation.entity.YbReconDetail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface YbReconDetailRepository extends JpaRepository<YbReconDetail, Long> {

    List<YbReconDetail> findAllByContractBusNoAndTenantIdAndIsDeletedFalseOrderByCreatedAtDesc(
        String contractBusNo, Long tenantId);
}
