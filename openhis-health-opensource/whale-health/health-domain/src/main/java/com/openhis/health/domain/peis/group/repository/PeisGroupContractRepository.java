/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.peis.group.repository;

import com.openhis.health.domain.peis.group.entity.PeisGroupContract;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PeisGroupContractRepository extends JpaRepository<PeisGroupContract, Long> {

    List<PeisGroupContract> findByCompanyIdAndIsDeletedFalse(Long companyId);

    List<PeisGroupContract> findByCompanyIdInAndIsDeletedFalse(List<Long> companyIds);

    boolean existsByCompanyIdAndIsDeletedFalse(Long companyId);
}
