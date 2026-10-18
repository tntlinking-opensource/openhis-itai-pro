/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.domain.group.repository;

import com.zxplat.plugin.peis.domain.group.entity.PeisGroupBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PeisGroupBatchRepository extends JpaRepository<PeisGroupBatch, Long> {

    List<PeisGroupBatch> findByCompanyIdAndIsDeletedFalse(Long companyId);

    List<PeisGroupBatch> findByCompanyIdInAndIsDeletedFalse(List<Long> companyIds);

    List<PeisGroupBatch> findByContractIdAndIsDeletedFalse(Long contractId);

    Optional<PeisGroupBatch> findByContractIdAndBatchNameAndIsDeletedFalse(Long contractId, String batchName);

    Optional<PeisGroupBatch> findByIdAndIsDeletedFalse(Long id);
}
