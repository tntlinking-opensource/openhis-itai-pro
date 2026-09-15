/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.peis.group.repository;

import com.openhis.health.domain.peis.group.entity.PeisGroupBatchPackage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PeisGroupBatchPackageRepository extends JpaRepository<PeisGroupBatchPackage, Long> {

    List<PeisGroupBatchPackage> findByBatchIdAndIsDeletedFalseOrderBySortOrderAscIdAsc(Long batchId);

    List<PeisGroupBatchPackage> findByBatchIdInAndIsDeletedFalseOrderBySortOrderAscIdAsc(List<Long> batchIds);
}
