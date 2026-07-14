/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.reconciliation.repository;

import com.openhis.chs.domain.reconciliation.entity.YbReconDaily;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** {@code yb_recon_daily} JPA 仓储 */
public interface YbReconDailyRepository extends JpaRepository<YbReconDaily, Long> {

    List<YbReconDaily> findAllByContractBusNoAndTenantIdAndIsDeletedFalseOrderByCreatedAtDesc(
        String contractBusNo, Long tenantId);
}
