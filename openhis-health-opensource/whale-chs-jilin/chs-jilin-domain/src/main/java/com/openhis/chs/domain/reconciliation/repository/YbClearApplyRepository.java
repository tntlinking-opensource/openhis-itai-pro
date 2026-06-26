/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.reconciliation.repository;

import com.openhis.chs.domain.reconciliation.entity.YbClearApply;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface YbClearApplyRepository extends JpaRepository<YbClearApply, Long> {

    Optional<YbClearApply> findFirstByIdAndTenantIdAndIsDeletedFalse(Long id, Long tenantId);

    List<YbClearApply> findAllByContractBusNoAndTenantIdAndIsDeletedFalseOrderByCreatedAtDesc(
        String contractBusNo, Long tenantId);

    Optional<YbClearApply> findFirstByClrAppyEvtIdAndTenantIdAndIsDeletedFalse(
        String clrAppyEvtId, Long tenantId);
}
