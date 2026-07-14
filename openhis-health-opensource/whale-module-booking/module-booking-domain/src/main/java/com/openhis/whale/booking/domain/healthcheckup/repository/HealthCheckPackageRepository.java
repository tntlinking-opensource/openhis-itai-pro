/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.domain.healthcheckup.repository;

import com.openhis.whale.booking.domain.healthcheckup.entity.HealthCheckPackage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface HealthCheckPackageRepository extends JpaRepository<HealthCheckPackage, Long>, JpaSpecificationExecutor<HealthCheckPackage> {

    Optional<HealthCheckPackage> findByOrganizationIdAndPackageCodeAndIsDeletedFalse(Long organizationId, String packageCode);
}
