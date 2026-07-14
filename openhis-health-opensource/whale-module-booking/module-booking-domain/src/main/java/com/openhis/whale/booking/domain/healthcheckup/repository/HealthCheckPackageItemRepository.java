/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.domain.healthcheckup.repository;

import com.openhis.whale.booking.domain.healthcheckup.entity.HealthCheckPackageItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface HealthCheckPackageItemRepository extends JpaRepository<HealthCheckPackageItem, Long>, JpaSpecificationExecutor<HealthCheckPackageItem> {

    List<HealthCheckPackageItem> findByPackageIdAndIsDeletedFalseOrderBySortOrderAsc(Long packageId);
}
