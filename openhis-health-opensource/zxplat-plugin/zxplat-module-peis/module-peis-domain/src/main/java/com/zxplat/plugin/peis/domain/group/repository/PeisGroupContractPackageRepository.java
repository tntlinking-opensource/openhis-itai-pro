/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.domain.group.repository;

import com.zxplat.plugin.peis.domain.group.entity.PeisGroupContractPackage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PeisGroupContractPackageRepository extends JpaRepository<PeisGroupContractPackage, Long> {

    List<PeisGroupContractPackage> findByContractIdAndIsDeletedFalseOrderBySortOrderAscIdAsc(
        Long contractId);

    List<PeisGroupContractPackage> findByContractIdInAndIsDeletedFalseOrderBySortOrderAscIdAsc(
        List<Long> contractIds);
}
