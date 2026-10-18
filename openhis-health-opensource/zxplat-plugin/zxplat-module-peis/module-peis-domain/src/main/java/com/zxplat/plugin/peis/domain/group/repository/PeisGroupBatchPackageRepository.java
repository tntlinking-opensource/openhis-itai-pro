/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.domain.group.repository;

import com.zxplat.plugin.peis.domain.group.entity.PeisGroupBatchPackage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PeisGroupBatchPackageRepository extends JpaRepository<PeisGroupBatchPackage, Long> {

    List<PeisGroupBatchPackage> findByBatchIdAndIsDeletedFalseOrderBySortOrderAscIdAsc(Long batchId);

    List<PeisGroupBatchPackage> findByBatchIdInAndIsDeletedFalseOrderBySortOrderAscIdAsc(List<Long> batchIds);
}
