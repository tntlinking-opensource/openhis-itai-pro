/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.peis.group.repository;

import com.openhis.health.domain.peis.group.entity.PeisGroupContractPackage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PeisGroupContractPackageRepository extends JpaRepository<PeisGroupContractPackage, Long> {

    List<PeisGroupContractPackage> findByContractIdAndIsDeletedFalseOrderBySortOrderAscIdAsc(
        Long contractId);

    List<PeisGroupContractPackage> findByContractIdInAndIsDeletedFalseOrderBySortOrderAscIdAsc(
        List<Long> contractIds);
}
