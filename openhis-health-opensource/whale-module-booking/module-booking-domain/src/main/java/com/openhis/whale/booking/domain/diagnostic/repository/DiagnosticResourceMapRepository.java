/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.domain.diagnostic.repository;

import com.openhis.whale.booking.domain.diagnostic.entity.DiagnosticResourceMap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DiagnosticResourceMapRepository extends JpaRepository<DiagnosticResourceMap, Long>, JpaSpecificationExecutor<DiagnosticResourceMap> {
}
