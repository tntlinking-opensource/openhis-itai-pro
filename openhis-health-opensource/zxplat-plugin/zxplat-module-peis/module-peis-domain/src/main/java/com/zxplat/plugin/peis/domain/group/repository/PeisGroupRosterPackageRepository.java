/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.domain.group.repository;

import com.zxplat.plugin.peis.domain.group.entity.PeisGroupRosterPackage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PeisGroupRosterPackageRepository extends JpaRepository<PeisGroupRosterPackage, Long> {

    List<PeisGroupRosterPackage> findByRosterIdAndIsDeletedFalseOrderBySortOrderAscIdAsc(Long rosterId);

    List<PeisGroupRosterPackage> findByRosterIdInAndIsDeletedFalseOrderBySortOrderAscIdAsc(List<Long> rosterIds);
}
