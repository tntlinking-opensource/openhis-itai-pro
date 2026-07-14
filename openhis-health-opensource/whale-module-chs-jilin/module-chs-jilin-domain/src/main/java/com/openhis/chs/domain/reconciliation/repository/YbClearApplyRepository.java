/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.reconciliation.repository;

import com.openhis.chs.domain.reconciliation.entity.YbClearApply;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** {@code yb_clear_apply} JPA 仓储 */
public interface YbClearApplyRepository extends JpaRepository<YbClearApply, Long> {

    Optional<YbClearApply> findFirstByIdAndTenantIdAndIsDeletedFalse(Long id, Long tenantId);

    List<YbClearApply> findAllByContractBusNoAndTenantIdAndIsDeletedFalseOrderByCreatedAtDesc(
        String contractBusNo, Long tenantId);

    Optional<YbClearApply> findFirstByClrAppyEvtIdAndTenantIdAndIsDeletedFalse(
        String clrAppyEvtId, Long tenantId);
}
